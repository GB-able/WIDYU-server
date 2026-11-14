package com.widyu.sensor.application;

import static com.widyu.sensor.application.SensorBatchService.CONTENT_TYPE;
import static com.widyu.sensor.application.SensorBatchService.QUALITY_STATUS_OK;
import static com.widyu.sensor.application.SensorBatchService.STREAM_HR;
import static com.widyu.sensor.application.SensorBatchService.applyResend;
import static com.widyu.sensor.application.SensorBatchService.attributionOf;
import static com.widyu.sensor.application.SensorBatchService.originalRunId;
import static com.widyu.sensor.application.SensorBatchService.sha256;
import static com.widyu.sensor.validator.SensorBatchValidator.parseElapsedNs;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.infrastructure.s3.S3Service;
import com.widyu.heart.application.HeartRateBatchService;
import com.widyu.member.Member;
import com.widyu.run.application.CollectionRunService;
import com.widyu.sensor.SensorBatch;
import com.widyu.sensor.SensorIngestChannel;
import com.widyu.sensor.application.SensorBatchService.RunAttribution;
import com.widyu.sensor.dto.request.HeartRateBatchRequest;
import com.widyu.sensor.dto.request.SensorBatchRequest;
import com.widyu.sensor.dto.response.SensorBatchResult;
import com.widyu.sensor.dto.response.SensorBatchResultResponse;
import com.widyu.sensor.repository.SensorBatchRepository;
import com.widyu.sensor.validator.SensorBatchValidator;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 심박 배치 수집 경로(LLD-0047). {@link SensorBatchService}가 {@code stream: "hr"} 본문을 넘긴다.
 * IMU와 같이 받은 원문 바이트를 그대로 S3에 올리고, 샘플 행을 인덱스 행보다 먼저 저장한다(ADR-0031 결정 4).
 * 심박 수치·판정 상태·AI 응답은 어떤 로그 레벨에도 남기지 않는다(#639).
 */
@Slf4j
final class HeartRateBatchIngest {

    private static final String SOURCE_WATCH = "watch";

    private final SensorBatchRepository sensorBatchRepository;
    private final ClockMappingService clockMappingService;
    private final HeartRateBatchService heartRateBatchService;
    private final CollectionRunService collectionRunService;
    private final S3Service s3Service;
    private final SensorBatchValidator batchValidator;

    HeartRateBatchIngest(
            SensorBatchRepository sensorBatchRepository,
            ClockMappingService clockMappingService,
            HeartRateBatchService heartRateBatchService,
            CollectionRunService collectionRunService,
            S3Service s3Service,
            SensorBatchValidator batchValidator
    ) {
        this.sensorBatchRepository = sensorBatchRepository;
        this.clockMappingService = clockMappingService;
        this.heartRateBatchService = heartRateBatchService;
        this.collectionRunService = collectionRunService;
        this.s3Service = s3Service;
        this.batchValidator = batchValidator;
    }

    SensorBatchResultResponse ingest(Member member, Long memberId, byte[] payload, HeartRateBatchRequest request,
                                     long serverReceivedAtMs, SensorIngestChannel channel) {
        batchValidator.validateHeartRate(request);
        long acceptedAtMs = System.currentTimeMillis();

        // 심박은 경과 나노초 축이 없어 관측 범위를 앵커 한 점으로 준다(LLD-0047 5절 3단계).
        long anchorElapsedNs = parseElapsedNs(request.clock().anchorElapsedNs());
        clockMappingService.register(
                request.clock(), request.deviceId(), anchorElapsedNs, anchorElapsedNs);

        // 검증이 ts_ms 엄격 증가를 보장하므로 첫·마지막 샘플이 곧 측정 범위다.
        List<HeartRateBatchRequest.Sample> samples = request.samples();
        long measuredAtStartMs = samples.get(0).tsMs();
        long measuredAtEndMs = samples.get(samples.size() - 1).tsMs();

        RunAttribution attribution = resolveAttribution(memberId, request.deviceId(),
                request.runId(), request.studyId(), request.participationId(),
                request.resend(), measuredAtStartMs);

        String payloadSha256 = sha256(payload);
        Optional<SensorBatch> existingBatch = sensorBatchRepository.findByBatchId(request.batchId());
        if (existingBatch.isPresent()) {
            return duplicateOrRejectHeartRate(memberId, request, payloadSha256, existingBatch.get());
        }

        String objectKey = "sensor/%d/%s/%s/%s-%s.json".formatted(
                memberId, request.deviceId(), STREAM_HR, request.batchId(), payloadSha256);
        s3Service.uploadBytes(objectKey, payload, CONTENT_TYPE);

        // 샘플 행이 인덱스 행보다 먼저다. 인덱스가 먼저 생기면 그 뒤 샘플 저장이 실패했을 때
        // 재전송이 DUPLICATE로 막혀 샘플이 영영 비는 구멍이 생긴다(ADR-0031 결정 4).
        HeartRateBatchService.BatchOutcome outcome = heartRateBatchService.storeAndAssess(
                member, request.batchId(), attribution.runId(), request.onBody(), request.wearState(),
                samples, serverReceivedAtMs);

        long persistedAtMs = System.currentTimeMillis();
        SensorBatch batch = heartRateEntity(member, request, payload, payloadSha256, objectKey, attribution,
                measuredAtStartMs, measuredAtEndMs, serverReceivedAtMs, acceptedAtMs, persistedAtMs, channel);
        try {
            sensorBatchRepository.save(batch);
        } catch (DataIntegrityViolationException e) {
            Optional<SensorBatch> concurrentlyStored = sensorBatchRepository.findByBatchId(request.batchId());
            if (concurrentlyStored.isEmpty()) {
                throw e;
            }
            return duplicateOrRejectHeartRate(memberId, request, payloadSha256, concurrentlyStored.get());
        }
        return loggedHeartRate(memberId, request, outcome.aiSkipped(), SensorBatchResult.STORED);
    }

    private RunAttribution resolveAttribution(
            Long memberId, String deviceId, String runId, String studyId, String participationId,
            SensorBatchRequest.Resend resend, long measuredAtStartMs) {
        String candidateRunId = originalRunId(resend);
        if (runId != null) {
            candidateRunId = runId;
        }
        if (candidateRunId != null) {
            return attributionOf(collectionRunService.requireAttributableRun(
                    candidateRunId, memberId, deviceId, measuredAtStartMs));
        }
        return collectionRunService.resolveRun(memberId, deviceId, measuredAtStartMs)
                .map(SensorBatchService::attributionOf)
                .orElseGet(() -> new RunAttribution(null, studyId, participationId, null));
    }

    /** 심박 인덱스 행. 축·충격·설정 대조는 IMU만의 개념이라 비운다(LLD-0047 4절). */
    private SensorBatch heartRateEntity(
            Member member,
            HeartRateBatchRequest request,
            byte[] payload,
            String payloadSha256,
            String objectKey,
            RunAttribution attribution,
            long measuredAtStartMs,
            long measuredAtEndMs,
            long serverReceivedAtMs,
            long acceptedAtMs,
            long persistedAtMs,
            SensorIngestChannel channel
    ) {
        SensorBatchRequest.Clock clock = request.clock();
        SensorBatch.SensorBatchBuilder builder = SensorBatch.builder()
                .batchId(request.batchId())
                .member(member)
                .stream(STREAM_HR)
                .source(SOURCE_WATCH)
                .deviceId(request.deviceId())
                .sessionId(request.sessionId())
                .seq(request.seq())
                .studyId(attribution.studyId())
                .participationId(attribution.participationId())
                .runId(attribution.runId())
                .bootId(clock.bootId())
                .clockMappingId(clock.clockMappingId())
                .anchorElapsedNs(parseElapsedNs(clock.anchorElapsedNs()))
                .anchorEpochMs(clock.anchorEpochMs())
                .uncertaintyMs(clock.uncertaintyMs())
                .sampleCount(request.samples().size())
                // 심박은 샘플마다 벽시계 시각이 실려 와 시계 환산을 하지 않는다.
                .measuredAtStartMs(measuredAtStartMs)
                .measuredAtEndMs(measuredAtEndMs)
                .phoneReceivedAtMs(request.phoneReceivedAtMs())
                .serverReceivedAtMs(serverReceivedAtMs)
                .acceptedAtMs(acceptedAtMs)
                .persistedAtMs(persistedAtMs)
                .modelAvailableAtServerMs(persistedAtMs)
                .onBody(request.onBody())
                .wearState(request.wearState())
                .watchBatteryPct(request.watchBatteryPct())
                .qualityStatus(QUALITY_STATUS_OK)
                .gyroBackfill(false)
                .isResend(false)
                .configMismatch(false)
                .s3Key(objectKey)
                .byteSize(payload.length)
                .payloadSha256(payloadSha256)
                .ingestChannel(channel);
        applyResend(builder, request.resend());
        return builder.build();
    }

    private SensorBatchResultResponse loggedHeartRate(
            Long memberId, HeartRateBatchRequest request, int aiSkipped, SensorBatchResult result) {
        // 심박 수치·판정 상태·AI 응답은 어떤 레벨에도 남기지 않는다(#639).
        log.info("심박 배치 수신: memberId={}, stream={}, seq={}, sampleCount={}, aiSkipped={}, result={}",
                memberId, STREAM_HR, request.seq(), request.samples().size(), aiSkipped, result);
        return SensorBatchResultResponse.of(request.batchId(), request.seq(), result);
    }

    private SensorBatchResultResponse duplicateOrRejectHeartRate(
            Long memberId,
            HeartRateBatchRequest request,
            String payloadSha256,
            SensorBatch existingBatch
    ) {
        if (!payloadSha256.equals(existingBatch.getPayloadSha256())) {
            throw new BusinessException(ErrorCode.SENSOR_BATCH_INVALID);
        }
        return loggedHeartRate(memberId, request, 0, SensorBatchResult.DUPLICATE);
    }
}
