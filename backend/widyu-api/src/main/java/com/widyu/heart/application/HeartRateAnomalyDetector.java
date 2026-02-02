package com.widyu.heart.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.properties.AiProperties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class HeartRateAnomalyDetector {

    private final RestTemplate aiRestTemplate;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    /**
     * 심박수 이상치 판별
     *
     * @param heartRates 심박수 데이터 (15개)
     * @return 이상 여부 (true: 비정상, false: 정상)
     */
    public boolean detectAnomaly(List<Integer> heartRates) {
        if (heartRates.size() != 15) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "심박수 데이터는 정확히 15개여야 합니다.");
        }

        String url = aiProperties.server().url() + "/check";

        try {
            String jsonInput = objectMapper.writeValueAsString(heartRates);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("json_input", jsonInput);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);

            log.info("AI 서버 호출: url={}, heartRates={}", url, heartRates);

            String result = aiRestTemplate.postForObject(url, request, String.class);

            log.info("AI 서버 응답: result={}", result);

            return parseAnomalyResult(result);

        } catch (JsonProcessingException e) {
            log.error("JSON 변환 실패: error={}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "데이터 처리 중 오류가 발생했습니다.");
        } catch (RestClientException e) {
            log.error("AI 서버 호출 실패: url={}, error={}", url, e.getMessage(), e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    "AI 서버와의 통신에 실패했습니다. 잠시 후 다시 시도해주세요."
            );
        }
    }

    private boolean parseAnomalyResult(String jsonResponse) throws JsonProcessingException {
        JsonNode node = objectMapper.readTree(jsonResponse);
        String resultValue = node.get("result").asText();
        return "Abnormal".equalsIgnoreCase(resultValue);
    }
}
