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
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
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
    private static final String NORMAL_REASON = "normal";

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

        long startedAt = System.nanoTime();
        List<AiHeartRateResponse> responses = new ArrayList<>();
        HeartRateStatus status = HeartRateStatus.NORMAL;
        boolean emergency = false;
        for (HeartRateMeasurement measurement : sortedMeasurements) {
            AiHeartRateResponse response = requestAnalysis(memberId, measurement, context);
            responses.add(response);
            HeartRateStatus nextStatus = parseStatus(response);
            status = higherStatus(status, nextStatus);
            if (Boolean.TRUE.equals(response.alert()) && nextStatus == HeartRateStatus.EMERGENCY) {
                emergency = true;
            }
        }

        logBatchSummary(memberId, context, status, responses, startedAt);

        return new DetectionResult(status, emergency);
    }

    /**
     * 배치 단위로 AI 판정 근거를 남긴다. 개인화(layer=L1, baselineSource=PERSONAL)가 실제로 적용되는지와
     * AI 순차 호출이 조회 지연에 얼마나 기여하는지를 운영에서 확인하기 위한 로그다.
     */
    private void logBatchSummary(
            Long memberId,
            String context,
            HeartRateStatus status,
            List<AiHeartRateResponse> responses,
            long startedAt
    ) {
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        log.info(
                "심박 배치 AI 판정: memberId={}, context={}, status={}, 소요={}ms, layer={}, baselineSource={}, sampleCount={}, 이상사유={}",
                memberId,
                context,
                status,
                elapsedMillis,
                distinctValues(responses, AiHeartRateResponse::layer),
                distinctValues(responses, AiHeartRateResponse::baselineSource),
                lastSampleCount(responses),
                abnormalReasons(responses)
        );
    }

    private Set<String> distinctValues(
            List<AiHeartRateResponse> responses,
            Function<AiHeartRateResponse, String> extractor
    ) {
        return responses.stream()
                .map(extractor)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> abnormalReasons(List<AiHeartRateResponse> responses) {
        return responses.stream()
                .map(AiHeartRateResponse::reason)
                .filter(Objects::nonNull)
                .filter(reason -> !NORMAL_REASON.equals(reason))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Integer lastSampleCount(List<AiHeartRateResponse> responses) {
        if (responses.isEmpty()) {
            return null;
        }
        return responses.getLast().sampleCount();
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

    /**
     * 판정에 사용하는 필드는 {@code alert}, {@code level} 뿐이고 나머지는 로그 관측용이다.
     * 영속화 범위는 LLD-0019를 따른다.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AiHeartRateResponse(
            Boolean alert,
            String level,
            String layer,
            String reason,
            @JsonProperty("baseline_source") String baselineSource,
            @JsonProperty("sample_count") Integer sampleCount
    ) {
    }
}
