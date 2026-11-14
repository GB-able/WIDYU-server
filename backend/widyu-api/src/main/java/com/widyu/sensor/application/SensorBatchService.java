package com.widyu.sensor.application;

import static com.widyu.sensor.validator.SensorBatchValidator.BATCH_ID_PATTERN;
import static com.widyu.sensor.validator.SensorBatchValidator.SESSION_ID_PATTERN;
import static com.widyu.sensor.validator.SensorBatchValidator.elapsedRange;
import static com.widyu.sensor.validator.SensorBatchValidator.parseElapsedNs;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.infrastructure.s3.S3Service;
import com.widyu.global.properties.SensorProperties;
import com.widyu.decision.application.FallAssessmentService;
import com.widyu.heart.application.HeartRateBatchService;
import com.widyu.member.Member;
import com.widyu.member.repository.MemberRepository;
import com.widyu.run.CollectionRun;
import com.widyu.run.application.CollectionRunService;
import com.widyu.sensor.SensorBatch;
import com.widyu.sensor.SensorBatchRejection;
import com.widyu.sensor.SensorIngestChannel;
import com.widyu.sensor.dto.request.HeartRateBatchRequest;
import com.widyu.sensor.dto.request.SensorBatchRequest;
import com.widyu.sensor.dto.response.SensorBatchResult;
import com.widyu.sensor.dto.response.SensorBatchResultResponse;
import com.widyu.sensor.repository.SensorBatchRepository;
import com.widyu.sensor.validator.SensorBatchValidator;
import com.widyu.sensor.validator.SensorBatchValidator.ElapsedRange;
import com.widyu.sensor.validator.SensorBatchValidator.TimeRange;
import com.widyu.sensor.validator.SensorBatchValidator.ValidatedBatch;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Validator;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * v2 IMU 배치 1건을 S3 객체 하나와 인덱스 행 하나로 저장한다(LLD-0041 5.1).
 * S3에는 <b>받은 원문 바이트를 그대로</b> 올린다. 재직렬화·정렬·압축을 하지 않으므로
 * 꺼냈을 때 앱이 보낸 본문과 바이트 단위로 같다(지시서 B2, 완료기준 C9-1).
 * 멱등 키는 앱이 붙인 불변 {@code batch_id}다.
 *
 * <p>S3 호출을 트랜잭션 밖에 두기 위해 {@code @Transactional}을 선언하지 않는다.
 * 원시 센서값은 어떤 로그 레벨에도 남기지 않는다(정책 1.6.7).
 */
@Slf4j
@Service
public class SensorBatchService {

    static final String CONTENT_TYPE = "application/json";
    static final String QUALITY_STATUS_OK = "OK";
    private static final String STREAM_WATCH = "imu_watch";
    private static final String STREAM_PHONE = "imu_phone";
    static final String STREAM_HR = "hr";
    private static final String STREAM_IMU_DROPPED = "imu_dropped";
    /** 거절 기록에 남길 stream 이름. 목록 밖 값도 남겨 옛 앱이 보낸 이름을 찾는다(LLD-0128 4.3). */
    private static final Pattern STREAM_NAME_PATTERN = Pattern.compile("^[a-z0-9_]{1,20}$");
    /** 수신 시도 카운터(LLD-0128 3.2). 태그 키는 channel·stream·result로 고정한다. */
    public static final String RECEIVED_METRIC = "sensor.batch.received";
    public static final String UNKNOWN_STREAM = "unknown";
    private static final String RESULT_REJECTED = "REJECTED";
    private static final String RESULT_FAILED = "FAILED";

    private final SensorBatchRepository sensorBatchRepository;
    private final ClockMappingService clockMappingService;
    private final FallAssessmentService fallAssessmentService;
    private final ActivityStateService activityStateService;
    private final CollectionRunService collectionRunService;
    private final MemberRepository memberRepository;
    private final S3Service s3Service;
    private final SensorBatchValidator batchValidator;
    private final HeartRateBatchIngest heartRateBatchIngest;
    private final SensorProperties sensorProperties;
    private final ObjectMapper payloadMapper;
    private final SensorDroppedRangeService sensorDroppedRangeService;
    private final SensorBatchRejectionRecorder rejectionRecorder;
    private final MeterRegistry meterRegistry;

    public SensorBatchService(
            SensorBatchRepository sensorBatchRepository,
            ClockMappingService clockMappingService,
            HeartRateBatchService heartRateBatchService,
            FallAssessmentService fallAssessmentService,
            ActivityStateService activityStateService,
            CollectionRunService collectionRunService,
            MemberRepository memberRepository,
            S3Service s3Service,
            Validator validator,
            SensorProperties sensorProperties,
            ObjectMapper objectMapper,
            SensorDroppedRangeService sensorDroppedRangeService,
            SensorBatchRejectionRecorder rejectionRecorder,
            MeterRegistry meterRegistry
    ) {
        this.sensorBatchRepository = sensorBatchRepository;
        this.clockMappingService = clockMappingService;
        this.fallAssessmentService = fallAssessmentService;
        this.activityStateService = activityStateService;
        this.collectionRunService = collectionRunService;
        this.memberRepository = memberRepository;
        this.s3Service = s3Service;
        this.batchValidator = new SensorBatchValidator(validator);
        this.heartRateBatchIngest = new HeartRateBatchIngest(sensorBatchRepository, clockMappingService,
                heartRateBatchService, collectionRunService, s3Service, batchValidator);
        this.sensorProperties = sensorProperties;
        this.sensorDroppedRangeService = sensorDroppedRangeService;
        this.rejectionRecorder = rejectionRecorder;
        this.meterRegistry = meterRegistry;
        // 나노초 필드는 64비트 정밀도를 지키려고 JSON 문자열로 받는다. Jackson 기본값은 number를
        // String 필드에 조용히 넣어주므로, 문자열 대상 강제 변환을 꺼서 number면 파싱이 실패하게 한다(검사기 A3).
        this.payloadMapper = objectMapper.copy()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.payloadMapper.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
    }

    public SensorBatchResultResponse ingest(Long memberId, byte[] payload, SensorIngestChannel channel) {
        return ingest(memberId, payload, System.currentTimeMillis(), channel);
    }

    /**
     * WebSocket 경로는 수신 스레드에서 잡은 수신 시각을 넘긴다. 실행기 대기 시간이 server_received에
     * 섞이면 수신 시각이 실제보다 늦어진다(정책 1.1.5, LLD-0091).
     *
     * <p>수신 시도마다 경로·자료 종류·결과로 한 번 센다. 서비스 안 거절은 거절 기록을 남기고, S3·DB 일시 실패는
     * 앱이 다시 보내야 하므로 거절로 바꾸지 않는다(LLD-0128 5.1).
     */
    public SensorBatchResultResponse ingest(
            Long memberId, byte[] payload, long serverReceivedAtMs, SensorIngestChannel channel) {
        JsonNode root = null;
        try {
            Member member = memberRepository.findById(memberId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

            if (payload.length > sensorProperties.maxPayloadBytes()) {
                throw new BusinessException(ErrorCode.SENSOR_BATCH_TOO_LARGE);
            }

            // 심박과 IMU가 같은 endpoint로 온다(ADR-0031 결정 1). stream만 먼저 읽어 DTO를 고른다.
            root = readTree(payload);
            SensorBatchResultResponse response =
                    route(member, memberId, payload, root, serverReceivedAtMs, channel);
            countReceived(meterRegistry, channel, streamTag(root), response.result().name());
            return response;
        } catch (BusinessException e) {
            if (isTransient(e)) {
                countReceived(meterRegistry, channel, streamTag(root), RESULT_FAILED);
                throw e;
            }
            countReceived(meterRegistry, channel, streamTag(root), RESULT_REJECTED);
            recordRejection(memberId, root, e, channel, serverReceivedAtMs);
            throw e;
        } catch (RuntimeException e) {
            countReceived(meterRegistry, channel, streamTag(root), RESULT_FAILED);
            throw e;
        }
    }

    /**
     * 재전송으로 복구되는 일시 실패. WebSocket은 이 경우 {@code REJECTED} ACK 대신 오류 큐로 보내 앱이 다시 보내게 한다.
     * DB 일시 실패는 {@code BusinessException}이 아니라 여기 오지 않는다.
     */
    public static boolean isTransient(BusinessException e) {
        return ErrorCode.FILE_UPLOAD_FAILED.equals(e.getErrorCode());
    }

    /** 태그 키를 한곳에서 정한다. 같은 이름에 다른 키 집합을 등록하면 Prometheus 등록기가 한쪽을 버린다. */
    public static void countReceived(
            MeterRegistry meterRegistry, SensorIngestChannel channel, String stream, String result) {
        meterRegistry.counter(RECEIVED_METRIC, "channel", channel.name(), "stream", stream, "result", result)
                .increment();
    }

    private SensorBatchResultResponse route(Member member, Long memberId, byte[] payload, JsonNode root,
                                            long serverReceivedAtMs, SensorIngestChannel channel) {
        String stream = streamOf(root);
        if (STREAM_HR.equals(stream)) {
            return heartRateBatchIngest.ingest(member, memberId, payload,
                    treeToValue(root, HeartRateBatchRequest.class), serverReceivedAtMs, channel);
        }
        // 워치 IMU 큐가 넘쳐 버린 구간 기록. 배치가 아니라 결측 근거라 따로 저장한다(LLD-0124).
        if (STREAM_IMU_DROPPED.equals(stream)) {
            return sensorDroppedRangeService.record(memberId, payload, root, serverReceivedAtMs);
        }
        return ingestImu(member, memberId, payload, root, serverReceivedAtMs, channel);
    }

    /** 카운터 태그는 값 종류가 고정돼야 하므로 허용 stream만 그대로 쓰고 나머지는 unknown이다. */
    private String streamTag(JsonNode root) {
        if (root == null) {
            return UNKNOWN_STREAM;
        }
        JsonNode stream = root.get("stream");
        if (stream == null || !stream.isTextual()) {
            return UNKNOWN_STREAM;
        }
        String value = stream.asText();
        if (STREAM_HR.equals(value) || STREAM_WATCH.equals(value) || STREAM_PHONE.equals(value)
                || STREAM_IMU_DROPPED.equals(value)) {
            return value;
        }
        return UNKNOWN_STREAM;
    }

    /**
     * 거절 기록은 별도 빈의 자기 트랜잭션이다. 기록 실패는 그 호출 바깥인 여기서 잡아 원래 거절 오류를 지킨다
     * (LLD-0128 5.2). 원문과 센서 값은 남기지 않는다.
     */
    private void recordRejection(Long memberId, JsonNode root, BusinessException cause,
                                 SensorIngestChannel channel, long serverReceivedAtMs) {
        String errorCode = cause.getErrorCode().getCode();
        try {
            rejectionRecorder.record(SensorBatchRejection.builder()
                    .memberId(memberId)
                    .deviceId(textMatching(root, "device_id", SESSION_ID_PATTERN))
                    .stream(textMatching(root, "stream", STREAM_NAME_PATTERN))
                    .batchId(textMatching(root, "batch_id", BATCH_ID_PATTERN))
                    .sessionId(textMatching(root, "session_id", SESSION_ID_PATTERN))
                    .seq(seqOf(root))
                    .errorCode(errorCode)
                    .ingestChannel(channel)
                    .receivedAtMs(serverReceivedAtMs)
                    .build());
        } catch (RuntimeException e) {
            log.warn("센서 배치 거절 기록 실패: memberId={}, code={}, errorType={}",
                    memberId, errorCode, e.getClass().getSimpleName());
        }
    }

    /** 수신 검증과 같은 형식일 때만 값을 돌려준다. 거절된 본문이라 형식을 믿을 수 없다. */
    private static String textMatching(JsonNode root, String field, Pattern pattern) {
        if (root == null) {
            return null;
        }
        JsonNode node = root.get(field);
        if (node == null || !node.isTextual() || !pattern.matcher(node.asText()).matches()) {
            return null;
        }
        return node.asText();
    }

    private static Long seqOf(JsonNode root) {
        if (root == null) {
            return null;
        }
        JsonNode seq = root.get("seq");
        if (seq == null || !seq.isIntegralNumber() || !seq.canConvertToLong()) {
            return null;
        }
        return seq.asLong();
    }

    private SensorBatchResultResponse ingestImu(Member member, Long memberId, byte[] payload, JsonNode root,
                                                long serverReceivedAtMs, SensorIngestChannel channel) {
        SensorBatchRequest request = treeToValue(root, SensorBatchRequest.class);
        ValidatedBatch validatedBatch = batchValidator.validate(request, root);
        long acceptedAtMs = System.currentTimeMillis();
        String payloadSha256 = sha256(payload);

        // 축 시각 범위는 매핑의 관측 범위와 배치의 환산 시각 양쪽에 쓰므로 한 번만 구한다.
        ElapsedRange elapsed = elapsedRange(request);
        // 매핑 대조는 S3 PUT 앞이다. 충돌이면 S3에도 배치 행에도 아무것도 남지 않는다(LLD-0044 5.2).
        clockMappingService.register(
                request.clock(), request.deviceId(), elapsed.minNs(), elapsed.maxNs());

        RunAttribution attribution = resolveAttribution(
                request, memberId, validatedBatch.measuredTimeRange().startMs());
        Optional<SensorBatch> existingBatch = sensorBatchRepository.findByBatchId(request.batchId());
        if (existingBatch.isPresent()) {
            return duplicateOrReject(memberId, request, payloadSha256, existingBatch.get());
        }

        boolean configMismatch = configMismatch(request, memberId, attribution);

        String objectKey = "sensor/%d/%s/%s/%s-%s.json".formatted(
                memberId, request.deviceId(), request.stream(), request.batchId(), payloadSha256);
        s3Service.uploadBytes(objectKey, payload, CONTENT_TYPE);

        long persistedAtMs = System.currentTimeMillis();
        try {
            SensorBatch savedBatch = toEntity(member, request, payload, payloadSha256, objectKey,
                    attribution, configMismatch, serverReceivedAtMs, acceptedAtMs, persistedAtMs, validatedBatch,
                    channel);
            sensorBatchRepository.save(savedBatch);
            assessImpact(savedBatch);
            // 실패해도 예외를 던지지 않는다. 묶음 저장과 응답은 그대로다(LLD-0138 6절).
            activityStateService.record(memberId, request.batchId(), request.stream(), request.clock(), request.acc(),
                    request.resend());
        } catch (DataIntegrityViolationException e) {
            // UK 경합이면 같은 batch_id 행이 이미 있다. FK 오류나 스키마 불일치까지 DUPLICATE로
            // 응답하면 클라이언트가 재전송을 멈춰 그 배치가 유실되므로, 행이 확인될 때만 중복으로 본다.
            Optional<SensorBatch> concurrentlyStored = sensorBatchRepository.findByBatchId(request.batchId());
            if (concurrentlyStored.isEmpty()) {
                throw e;
            }
            return duplicateOrReject(memberId, request, payloadSha256, concurrentlyStored.get());
        }
        return logged(memberId, request, SensorBatchResult.STORED);
    }

    private void assessImpact(SensorBatch savedBatch) {
        if (savedBatch.getTriggerKind() == null) {
            return;
        }
        try {
            fallAssessmentService.assessAfterImpact(savedBatch);
        } catch (Exception e) {
            log.warn("낙상 판정 훅 실패: memberId={}, batchId={}, errorType={}",
                    savedBatch.getMember().getId(), savedBatch.getBatchId(), e.getClass().getSimpleName());
        }
    }

    private JsonNode readTree(byte[] payload) {
        try {
            JsonNode root = payloadMapper.readTree(payload);
            // 비어 있거나 공백뿐인 본문은 예외 없이 빈 값으로 읽힌다. 형식 오류로 거절해 거절 기록에 남긴다.
            if (root == null || root.isMissingNode()) {
                throw new BusinessException(ErrorCode.SENSOR_PAYLOAD_INVALID);
            }
            return root;
        } catch (IOException e) {
            // 예외 메시지에 샘플 값이 섞일 수 있어 어떤 레벨에도 남기지 않는다(정책 1.6.7).
            throw new BusinessException(ErrorCode.SENSOR_PAYLOAD_INVALID);
        }
    }

    private String streamOf(JsonNode root) {
        JsonNode stream = root.get("stream");
        if (stream == null || !stream.isTextual()) {
            throw new BusinessException(ErrorCode.SENSOR_BATCH_INVALID);
        }
        String value = stream.asText();
        if (!STREAM_HR.equals(value) && !STREAM_WATCH.equals(value) && !STREAM_PHONE.equals(value)
                && !STREAM_IMU_DROPPED.equals(value)) {
            throw new BusinessException(ErrorCode.SENSOR_BATCH_INVALID);
        }
        return value;
    }

    private <T> T treeToValue(JsonNode root, Class<T> type) {
        try {
            return payloadMapper.treeToValue(root, type);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.SENSOR_PAYLOAD_INVALID);
        }
    }

    /**
     * 이 배치가 붙을 회차와 연구 식별자. 셋 다 null이면 운영 외 자료다.
     * {@code collectionMode}는 회차를 실제로 조회했을 때만 채워진다. 설정 대조의 기준값이며,
     * 앱이 실어 보낸 {@code run_id}를 그대로 쓴 경우에는 추가 조회를 하지 않아 null이다.
     */
    record RunAttribution(
            String runId, String studyId, String participationId, String collectionMode) {}

    /**
     * 회차 귀속(LLD-0045 5절). 앱이 명시한 회차도 회원·기기·측정 시각의 실제 배정과 맞아야 한다.
     * 재전송은 원 회차를 쓴다. 둘 다 없으면 그 시점에 이 기기를 쓰던 열린 회차를 서버가 찾는다.
     */
    private RunAttribution resolveAttribution(
            SensorBatchRequest request, Long memberId, long measuredAtStartMs) {
        if (request.runId() != null) {
            return attributionOf(collectionRunService.requireAttributableRun(
                    request.runId(), memberId, request.deviceId(), measuredAtStartMs));
        }
        String originalRunId = originalRunId(request.resend());
        if (originalRunId != null) {
            return attributionOf(collectionRunService.requireAttributableRun(
                    originalRunId, memberId, request.deviceId(), measuredAtStartMs));
        }
        Optional<CollectionRun> run =
                collectionRunService.resolveRun(memberId, request.deviceId(), measuredAtStartMs);
        if (run.isEmpty()) {
            return new RunAttribution(null, request.studyId(), request.participationId(), null);
        }
        return attributionOf(run.get());
    }

    static RunAttribution attributionOf(CollectionRun run) {
        return new RunAttribution(
                run.getRunId(), run.getStudyId(), run.getParticipationId(), run.getCollectionMode());
    }

    /**
     * 앱이 적용한 설정을 서버 지시값과 대조한다(지시서 B12 「확인」). 다르면 표시만 하고 거부하지 않는다.
     * 설정이 적용되지 않은 채 도는 상황을 조용히 넘기지 않는 것이 목적이다.
     * 로그에는 다른 필드 <b>이름만</b> 남긴다(정책 1.6.7).
     */
    private boolean configMismatch(
            SensorBatchRequest request, Long memberId, RunAttribution attribution) {
        SensorProperties.Config config = sensorProperties.config();
        List<String> mismatchedFields = new ArrayList<>();

        Optional<String> baselineMode = baselineCollectionMode(attribution);
        // 모르는 회차면 기준이 없다. 다른 필드는 그대로 대조하고 수집 모드만 건너뛴다.
        if (baselineMode.isPresent() && !baselineMode.get().equals(request.collectionMode())) {
            mismatchedFields.add("collection_mode");
        }
        if (!config.gyroMode().equals(request.gyroMode())) {
            mismatchedFields.add("gyro_mode");
        }
        if (request.acc() != null && request.acc().fsHzRequested() != null
                && Double.compare(request.acc().fsHzRequested(), config.accFsHz()) != 0) {
            mismatchedFields.add("acc.fs_hz_requested");
        }

        if (mismatchedFields.isEmpty()) {
            return false;
        }
        log.warn("센서 설정 불일치: memberId={}, batchId={}, fields={}",
                memberId, request.batchId(), mismatchedFields);
        return true;
    }

    /**
     * 기준 수집 모드. 회차가 없으면 서버가 내려 준 기본 모드다(운영 product, 개발 서버 research, #989).
     * 회차는 정해졌는데 그 값을 모르면
     * (앱이 {@code run_id}를 실어 보냈거나 재전송이라 서버가 회차를 조회하지 않은 경우)
     * 배치당 한 번 조회한다. 모르는 회차면 기준이 없어 비워 둔다.
     */
    private Optional<String> baselineCollectionMode(RunAttribution attribution) {
        if (attribution.runId() == null) {
            return Optional.of(sensorProperties.config().defaultCollectionMode());
        }
        if (attribution.collectionMode() != null) {
            return Optional.of(attribution.collectionMode());
        }
        return collectionRunService.findCollectionMode(attribution.runId());
    }

    static String originalRunId(SensorBatchRequest.Resend resend) {
        if (resend == null || !Boolean.TRUE.equals(resend.isResend())) {
            return null;
        }
        return resend.originalRunId();
    }

    private SensorBatch toEntity(
            Member member,
            SensorBatchRequest request,
            byte[] payload,
            String payloadSha256,
            String objectKey,
            RunAttribution attribution,
            boolean configMismatch,
            long serverReceivedAtMs,
            long acceptedAtMs,
            long persistedAtMs,
            ValidatedBatch validatedBatch,
            SensorIngestChannel channel
    ) {
        SensorBatchRequest.Clock clock = request.clock();
        long anchorElapsedNs = parseElapsedNs(clock.anchorElapsedNs());
        TimeRange measuredTimeRange = validatedBatch.measuredTimeRange();

        SensorBatch.SensorBatchBuilder builder = SensorBatch.builder()
                .batchId(request.batchId())
                .member(member)
                .stream(request.stream())
                .source(request.source())
                .deviceId(request.deviceId())
                .sessionId(request.sessionId())
                .seq(request.seq())
                .studyId(attribution.studyId())
                .participationId(attribution.participationId())
                .runId(attribution.runId())
                .bootId(clock.bootId())
                .clockMappingId(clock.clockMappingId())
                .anchorElapsedNs(anchorElapsedNs)
                .anchorEpochMs(clock.anchorEpochMs())
                .uncertaintyMs(clock.uncertaintyMs())
                .measuredAtStartMs(measuredTimeRange.startMs())
                .measuredAtEndMs(measuredTimeRange.endMs())
                .phoneReceivedAtMs(request.phoneReceivedAtMs())
                .serverReceivedAtMs(serverReceivedAtMs)
                .acceptedAtMs(acceptedAtMs)
                .persistedAtMs(persistedAtMs)
                // 가용 시각은 저장 시각과 같고 소급하지 않는다(정책 1.1.5).
                .modelAvailableAtServerMs(persistedAtMs)
                .collectionMode(request.collectionMode())
                .gyroMode(request.gyroMode())
                .onBody(request.onBody())
                .wearState(request.wearState())
                .missingReason(request.missingReason())
                .watchBatteryPct(request.watchBatteryPct())
                .qualityStatus(QUALITY_STATUS_OK)
                .gyroBackfill(Boolean.TRUE.equals(request.gyroBackfill()))
                .backfillFor(validatedBatch.backfillFor())
                .isResend(false)
                .s3Key(objectKey)
                .byteSize(payload.length)
                .payloadSha256(payloadSha256)
                .configMismatch(configMismatch)
                .ingestChannel(channel);

        // gyro가 null이면 축 컬럼도 null로 둔다. 0으로 채우면 정지 상태로 읽힌다(검사기 D1).
        if (request.acc() != null) {
            builder.accN(request.acc().n())
                    .accT0ElapsedNs(parseElapsedNs(request.acc().t0ElapsedNs()))
                    .accFsHzRequested(request.acc().fsHzRequested());
        }
        if (request.gyro() != null) {
            builder.gyroN(request.gyro().n())
                    .gyroT0ElapsedNs(parseElapsedNs(request.gyro().t0ElapsedNs()))
                    .gyroFsHzRequested(request.gyro().fsHzRequested());
        }
        if (request.trigger() != null) {
            builder.triggerKind(request.trigger().kind())
                    .triggerSmvG(request.trigger().smvG())
                    .triggerTsMs(request.trigger().tsMs());
            if (request.trigger().eventElapsedNs() != null) {
                builder.triggerEventElapsedNs(parseElapsedNs(request.trigger().eventElapsedNs()));
            }
        }
        applyResend(builder, request.resend());
        return builder.build();
    }

    /** 재전송 계보 6필드(지시서 B4). 두 스트림이 같은 규칙을 쓴다. */
    static void applyResend(SensorBatch.SensorBatchBuilder builder, SensorBatchRequest.Resend resend) {
        if (resend == null) {
            return;
        }
        builder.isResend(Boolean.TRUE.equals(resend.isResend()))
                .originalBatchId(resend.originalBatchId())
                .originalSeq(resend.originalSeq())
                .originalRunId(resend.originalRunId())
                .originalSessionId(resend.originalSessionId())
                .resentAtMs(resend.resentAtMs());
    }

    private SensorBatchResultResponse duplicateOrReject(
            Long memberId,
            SensorBatchRequest request,
            String payloadSha256,
            SensorBatch existingBatch
    ) {
        if (!payloadSha256.equals(existingBatch.getPayloadSha256())) {
            throw new BusinessException(ErrorCode.SENSOR_BATCH_INVALID);
        }
        return logged(memberId, request, SensorBatchResult.DUPLICATE);
    }

    private SensorBatchResultResponse logged(
            Long memberId, SensorBatchRequest request, SensorBatchResult result) {
        log.info("센서 배치 수신: memberId={}, stream={}, seq={}, accN={}, gyroN={}, result={}",
                memberId, request.stream(), request.seq(),
                sampleCount(request.acc()), sampleCount(request.gyro()), result);
        return SensorBatchResultResponse.of(request.batchId(), request.seq(), result);
    }

    private Integer sampleCount(SensorBatchRequest.Axis axis) {
        if (axis == null) {
            return null;
        }
        return axis.n();
    }

    static String sha256(byte[] payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }

}
