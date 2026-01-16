package com.widyu.ai.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.widyu.ai.dto.response.HeartRateAnomalyResponse;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class HeartRateAnomalyService {

    private final RestTemplate aiRestTemplate;

    @Value("${ai.server.url}")
    private String aiServerUrl;

    public HeartRateAnomalyService(@Qualifier("aiRestTemplate") RestTemplate aiRestTemplate) {
        this.aiRestTemplate = aiRestTemplate;
    }

    /**
     * 심박수 이상치 판별
     *
     * @param heartRates 심박수 데이터 (15개)
     * @return 판별 결과
     */
    public HeartRateAnomalyResponse detectAnomaly(List<Integer> heartRates) {
        if (heartRates.size() != 15) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "심박수 데이터는 정확히 15개여야 합니다.");
        }

        String url = aiServerUrl + "/check";

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            String jsonInput = objectMapper.writeValueAsString(heartRates);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("json_input", jsonInput);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);

            log.info("AI 서버 호출: url={}, heartRates={}", url, heartRates);

            String result = aiRestTemplate.postForObject(url, request, String.class);

            log.info("AI 서버 응답: result={}", result);

            return HeartRateAnomalyResponse.fromJsonResponse(result);

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
}
