package com.widyu.sensor.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.sensor.SensorDroppedRange;
import com.widyu.sensor.dto.request.SensorDroppedRangeRequest;
import com.widyu.sensor.dto.response.SensorBatchResult;
import com.widyu.sensor.dto.response.SensorBatchResultResponse;
import com.widyu.sensor.repository.SensorDroppedRangeRepository;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 워치 IMU 큐가 넘쳐 버린 구간({@code stream: "imu_dropped"})을 {@code sensor_dropped_range} 한 행으로 저장한다(LLD-0124).
 * 원문 바이트를 그대로 {@code payload}에 남긴다. 멱등 키는 {@code (member_id, session_id, from_seq)}다.
 *
 * <p>센서 배치처럼 {@code @Transactional}을 두지 않고 저장은 리포지토리의 자체 트랜잭션에서 돈다.
 * 이 기록에는 {@code batch_id}가 없어 결과 응답의 {@code batchId}·{@code seq}는 null이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensorDroppedRangeService {

    private final SensorDroppedRangeRepository sensorDroppedRangeRepository;
    private final Validator validator;
    private final ObjectMapper objectMapper;

    public SensorBatchResultResponse record(Long memberId, byte[] payload, JsonNode root, long serverReceivedAtMs) {
        SensorDroppedRangeRequest request = parse(root);
        validate(request);

        if (sensorDroppedRangeRepository.existsByMemberIdAndSessionIdAndFromSeq(
                memberId, request.sessionId(), request.fromSeq())) {
            return logged(memberId, request, SensorBatchResult.DUPLICATE);
        }

        try {
            sensorDroppedRangeRepository.save(SensorDroppedRange.builder()
                    .memberId(memberId)
                    .sessionId(request.sessionId())
                    .fromSeq(request.fromSeq())
                    .toSeq(request.toSeq())
                    .reason(request.reason())
                    .tsMs(request.tsMs())
                    .serverReceivedAtMs(serverReceivedAtMs)
                    .persistedAtMs(System.currentTimeMillis())
                    .payload(new String(payload, StandardCharsets.UTF_8))
                    .payloadSha256(sha256(payload))
                    .build());
        } catch (DataIntegrityViolationException e) {
            // UK 경합이면 같은 구간 행이 이미 있다. 행이 확인될 때만 중복으로 본다(DeviceHeartbeatService와 같은 규칙).
            if (!sensorDroppedRangeRepository.existsByMemberIdAndSessionIdAndFromSeq(
                    memberId, request.sessionId(), request.fromSeq())) {
                throw e;
            }
            return logged(memberId, request, SensorBatchResult.DUPLICATE);
        }
        return logged(memberId, request, SensorBatchResult.STORED);
    }

    private SensorDroppedRangeRequest parse(JsonNode root) {
        try {
            return objectMapper.treeToValue(root, SensorDroppedRangeRequest.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.SENSOR_BATCH_INVALID);
        }
    }

    private void validate(SensorDroppedRangeRequest request) {
        if (!validator.validate(request).isEmpty()) {
            throw new BusinessException(ErrorCode.SENSOR_BATCH_INVALID);
        }
        if (request.toSeq() < request.fromSeq()) {
            throw new BusinessException(ErrorCode.SENSOR_BATCH_INVALID);
        }
    }

    private SensorBatchResultResponse logged(Long memberId, SensorDroppedRangeRequest request, SensorBatchResult result) {
        log.info("IMU 버린 구간 수신: memberId={}, sessionId={}, fromSeq={}, toSeq={}, result={}",
                memberId, request.sessionId(), request.fromSeq(), request.toSeq(), result);
        return SensorBatchResultResponse.of(null, null, result);
    }

    private String sha256(byte[] payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }
}
