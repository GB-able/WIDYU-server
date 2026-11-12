package com.widyu.device.application;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.widyu.device.DeviceEvent;
import com.widyu.device.dto.request.DeviceEventRequest;
import com.widyu.device.dto.response.DeviceEventResponse;
import com.widyu.device.dto.response.DeviceEventResult;
import com.widyu.device.repository.DeviceEventRepository;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.member.repository.MemberRepository;
import com.widyu.run.CollectionRun;
import com.widyu.run.application.CollectionRunService;
import jakarta.validation.Validator;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 기기 상태 바뀜 묶음을 항목마다 {@code device_event} 행 하나로 저장한다(LLD-0129 5절).
 * 한 항목의 형식 오류는 그 항목만 REJECTED로 돌려주고 나머지는 계속 저장한다.
 *
 * <p>INSERT만 트랜잭션에 넣으려고 {@code @Transactional}을 두지 않는다. 저장은 리포지토리 자체 트랜잭션에서 돈다
 * (DeviceHeartbeatService와 같다). 원문·{@code attributes} 값은 어떤 로그 레벨에도 남기지 않는다.
 */
@Slf4j
@Service
public class DeviceEventService {

    static final int MAX_EVENTS = 500;
    static final int MAX_EVENT_BYTES = 4096;

    private final DeviceEventRepository deviceEventRepository;
    private final CollectionRunService collectionRunService;
    private final MemberRepository memberRepository;
    private final Validator validator;
    private final ObjectMapper payloadMapper;

    public DeviceEventService(
            DeviceEventRepository deviceEventRepository,
            CollectionRunService collectionRunService,
            MemberRepository memberRepository,
            Validator validator,
            ObjectMapper objectMapper
    ) {
        this.deviceEventRepository = deviceEventRepository;
        this.collectionRunService = collectionRunService;
        this.memberRepository = memberRepository;
        this.validator = validator;
        // 모르는 필드는 거절하지 않는다. 원문이 그대로 남아 손실이 없다(ADR-0030 v2 결정 3).
        this.payloadMapper = objectMapper.copy()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        // 타입이 틀린 값(kind: 123, occurred_at_ms: "1") 을 바꿔 읽지 않고 그 항목만 거절한다(LLD-0129 3절).
        this.payloadMapper.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        this.payloadMapper.coercionConfigFor(LogicalType.Integer)
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
    }

    public List<DeviceEventResponse> ingest(Long memberId, byte[] payload) {
        long serverReceivedAtMs = System.currentTimeMillis();

        if (!memberRepository.existsById(memberId)) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }

        List<byte[]> items = split(payload);
        List<DeviceEventResponse> responses = new ArrayList<>(items.size());
        Set<String> deviceIds = new TreeSet<>();
        for (byte[] item : items) {
            responses.add(ingestOne(memberId, item, serverReceivedAtMs, deviceIds));
        }

        Map<DeviceEventResult, Integer> counts = new EnumMap<>(DeviceEventResult.class);
        responses.forEach(response -> counts.merge(response.result(), 1, Integer::sum));
        log.info("기기 이벤트 수신: memberId={}, deviceIds={}, events={}, results={}",
                memberId, deviceIds, responses.size(), counts);
        return responses;
    }

    /**
     * 배열 원소마다 원문 바이트 구간을 잘라 낸다. 객체가 아닌 원소는 빈 배열로 남겨 그 항목만 거절되게 한다.
     * 최상위가 배열이 아니거나 문법이 깨졌거나 개수가 범위를 벗어나면 요청 전체를 거절한다.
     */
    private List<byte[]> split(byte[] payload) {
        List<byte[]> items = new ArrayList<>();
        try (JsonParser parser = payloadMapper.getFactory().createParser(payload)) {
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new BusinessException(ErrorCode.DEVICE_EVENT_BATCH_INVALID);
            }
            JsonToken token;
            while ((token = parser.nextToken()) != JsonToken.END_ARRAY) {
                if (token == null || items.size() == MAX_EVENTS) {
                    throw new BusinessException(ErrorCode.DEVICE_EVENT_BATCH_INVALID);
                }
                if (token != JsonToken.START_OBJECT) {
                    parser.skipChildren();
                    items.add(new byte[0]);
                    continue;
                }
                int start = (int) parser.currentTokenLocation().getByteOffset();
                parser.skipChildren();
                int end = (int) parser.currentLocation().getByteOffset();
                items.add(Arrays.copyOfRange(payload, start, end));
            }
            if (parser.nextToken() != null) {
                throw new BusinessException(ErrorCode.DEVICE_EVENT_BATCH_INVALID);
            }
        } catch (IOException e) {
            // 예외 메시지에 원문 조각이 섞일 수 있어 남기지 않는다.
            throw new BusinessException(ErrorCode.DEVICE_EVENT_BATCH_INVALID);
        }
        if (items.isEmpty()) {
            throw new BusinessException(ErrorCode.DEVICE_EVENT_BATCH_INVALID);
        }
        return items;
    }

    private DeviceEventResponse ingestOne(Long memberId, byte[] item, long serverReceivedAtMs, Set<String> deviceIds) {
        if (item.length == 0 || item.length > MAX_EVENT_BYTES) {
            return DeviceEventResponse.of(null, DeviceEventResult.REJECTED);
        }
        DeviceEventRequest request;
        try {
            request = payloadMapper.readValue(item, DeviceEventRequest.class);
        } catch (IOException e) {
            return DeviceEventResponse.of(null, DeviceEventResult.REJECTED);
        }
        if (!validator.validate(request).isEmpty()) {
            return DeviceEventResponse.of(request.eventId(), DeviceEventResult.REJECTED);
        }
        deviceIds.add(request.deviceId());

        if (deviceEventRepository.existsByDeviceIdAndEventId(request.deviceId(), request.eventId())) {
            return DeviceEventResponse.of(request.eventId(), DeviceEventResult.DUPLICATE);
        }
        try {
            deviceEventRepository.save(toEntity(memberId, request, item, serverReceivedAtMs));
        } catch (DataIntegrityViolationException e) {
            // UK 경합이면 같은 (기기, 이벤트) 행이 이미 있다. 다른 무결성 오류까지 DUPLICATE로 응답하면
            // 앱이 그 기록을 지워 유실되므로, 행이 확인될 때만 중복으로 본다.
            if (!deviceEventRepository.existsByDeviceIdAndEventId(request.deviceId(), request.eventId())) {
                throw e;
            }
            return DeviceEventResponse.of(request.eventId(), DeviceEventResult.DUPLICATE);
        }
        return DeviceEventResponse.of(request.eventId(), DeviceEventResult.STORED);
    }

    private DeviceEvent toEntity(Long memberId, DeviceEventRequest request, byte[] item, long serverReceivedAtMs) {
        String attributes = null;
        if (request.attributes() != null) {
            attributes = request.attributes().toString();
        }
        return DeviceEvent.builder()
                .memberId(memberId)
                .eventId(request.eventId())
                .deviceId(request.deviceId())
                .source(request.source())
                .kind(request.kind())
                .occurredAtMs(request.occurredAtMs())
                .sessionId(request.sessionId())
                .attributes(attributes)
                .runId(runId(memberId, request))
                .serverReceivedAtMs(serverReceivedAtMs)
                .payload(new String(item, UTF_8))
                .payloadSha256(sha256(item))
                .build();
    }

    /** 회차 귀속(LLD-0045 5절). 상태가 바뀐 기기와 시각으로 열린 회차를 찾고, 없으면 운영 외 자료다. */
    private String runId(Long memberId, DeviceEventRequest request) {
        Optional<CollectionRun> run =
                collectionRunService.resolveRun(memberId, request.deviceId(), request.occurredAtMs());
        if (run.isEmpty()) {
            return null;
        }
        return run.get().getRunId();
    }

    private String sha256(byte[] payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }
}
