package com.widyu.heart.application;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.properties.AiProperties;
import com.widyu.heart.HeartRateStatus;
import com.widyu.heart.dto.request.HeartRateMeasurement;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class HeartRateAnomalyDetector {

    private static final ZoneId HEART_RATE_ZONE = ZoneId.of("Asia/Seoul");

    private final RestTemplate aiRestTemplate;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    public DetectionResult detect(Long memberId, List<HeartRateMeasurement> measurements, String context) {
        if (measurements.size() != 15) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "심박수 데이터는 정확히 15개여야 합니다.");
        }

        List<HeartRateMeasurement> sortedMeasurements = measurements.stream()
                .sorted(Comparator.comparing(HeartRateMeasurement::measuredAt))
                .toList();

        HeartRateStatus status = HeartRateStatus.NORMAL;
        boolean emergency = false;
        for (HeartRateMeasurement measurement : sortedMeasurements) {
            AiHeartRateResponse response = requestAnalysis(memberId, measurement, context);
            HeartRateStatus nextStatus = parseStatus(response);
            status = higherStatus(status, nextStatus);
            if (Boolean.TRUE.equals(response.alert()) && nextStatus == HeartRateStatus.EMERGENCY) {
                emergency = true;
            }
        }
        return new DetectionResult(status, emergency);
    }

    private AiHeartRateResponse requestAnalysis(
            Long memberId,
            HeartRateMeasurement measurement,
            String context
    ) {
        String url = aiProperties.server().url() + "/api/hr";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            AiHeartRateRequest body = AiHeartRateRequest.of(memberId, measurement, context);
            HttpEntity<AiHeartRateRequest> request = new HttpEntity<>(body, headers);
            String result = aiRestTemplate.postForObject(url, request, String.class);
            return parseResponse(result);
        } catch (RestClientException e) {
            log.error("AI 서버 호출 실패: url={}, error={}", url, e.getMessage(), e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    "AI 서버와의 통신에 실패했습니다. 잠시 후 다시 시도해주세요."
            );
        }
    }

    private AiHeartRateResponse parseResponse(String jsonResponse) {
        try {
            AiHeartRateResponse response = objectMapper.readValue(jsonResponse, AiHeartRateResponse.class);
            if (response.alert() == null || response.level() == null) {
                throw invalidResponse();
            }
            return response;
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.error("AI 서버 응답 처리 실패: response={}, error={}", jsonResponse, e.getMessage());
            throw invalidResponse();
        }
    }

    private HeartRateStatus parseStatus(AiHeartRateResponse response) {
        return switch (response.level()) {
            case "NORMAL" -> HeartRateStatus.NORMAL;
            case "CAUTION" -> HeartRateStatus.CAUTION;
            case "EMERGENCY" -> HeartRateStatus.EMERGENCY;
            default -> throw invalidResponse();
        };
    }

    private HeartRateStatus higherStatus(HeartRateStatus current, HeartRateStatus next) {
        if (current == HeartRateStatus.EMERGENCY || next == HeartRateStatus.EMERGENCY) {
            return HeartRateStatus.EMERGENCY;
        }
        if (current == HeartRateStatus.CAUTION || next == HeartRateStatus.CAUTION) {
            return HeartRateStatus.CAUTION;
        }
        return HeartRateStatus.NORMAL;
    }

    private BusinessException invalidResponse() {
        return new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "AI 서버가 올바르지 않은 응답을 반환했습니다.");
    }

    public record DetectionResult(HeartRateStatus status, boolean emergency) {
    }

    private record AiHeartRateRequest(
            @JsonProperty("user_id") String userId,
            Integer bpm,
            String context,
            Double timestamp
    ) {
        private static AiHeartRateRequest of(
                Long memberId,
                HeartRateMeasurement measurement,
                String context
        ) {
            double timestamp = measurement.measuredAt()
                    .atZone(HEART_RATE_ZONE)
                    .toInstant()
                    .toEpochMilli() / 1000.0;
            return new AiHeartRateRequest(
                    memberId.toString(),
                    measurement.heartRate(),
                    context,
                    timestamp
            );
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AiHeartRateResponse(
            Boolean alert,
            String level
    ) {
    }
}
