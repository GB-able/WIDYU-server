package com.widyu.ai.application;

import com.widyu.ai.dto.response.HeartRateAnomalyResponse;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
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

        String url = aiServerUrl + "/predict";
        Map<String, Object> requestBody = Map.of("json_input", heartRates);

        try {
            log.info("AI 서버 호출: url={}, heartRates={}", url, heartRates);

            Integer result = aiRestTemplate.postForObject(url, requestBody, Integer.class);

            log.info("AI 서버 응답: result={}", result);

            return HeartRateAnomalyResponse.of(result);

        } catch (RestClientException e) {
            log.error("AI 서버 호출 실패: url={}, error={}", url, e.getMessage(), e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    "AI 서버와의 통신에 실패했습니다. 잠시 후 다시 시도해주세요."
            );
        }
    }
}
