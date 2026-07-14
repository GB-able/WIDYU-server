package com.widyu.heart.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.properties.AiProperties;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayName("HeartRateAnomalyDetector 예외 처리 단위 테스트")
class HeartRateAnomalyDetectorTest {

    @Mock private RestTemplate aiRestTemplate;

    @Test
    @DisplayName("심박수 데이터가 15개가 아니면 BAD_REQUEST 예외를 던진다")
    void 심박수_데이터가_15개가_아니면_예외가_발생한다() {
        // given
        HeartRateAnomalyDetector detector = detector();

        // when & then
        assertThatThrownBy(() -> detector.detectAnomaly(List.of(70, 71, 72)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BAD_REQUEST)
                .hasMessageContaining("심박수 데이터는 정확히 15개여야 합니다.");
    }

    @Test
    @DisplayName("AI 서버 통신 실패 시 INTERNAL_SERVER_ERROR 예외를 던진다")
    void AI_서버_통신_실패_시_예외가_발생한다() {
        // given
        HeartRateAnomalyDetector detector = detector();
        given(aiRestTemplate.postForObject(eq("http://ai-server/check"), any(), eq(String.class)))
                .willThrow(new RestClientException("connection refused"));

        // when & then
        assertThatThrownBy(() -> detector.detectAnomaly(fifteenHeartRates()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INTERNAL_SERVER_ERROR)
                .hasMessageContaining("AI 서버와의 통신에 실패했습니다.");
    }

    @Test
    @DisplayName("AI 서버가 Abnormal을 반환하면 이상 상태로 판단한다")
    void AI_서버가_Abnormal을_반환하면_이상_상태로_판단한다() {
        // given
        HeartRateAnomalyDetector detector = detector();
        given(aiRestTemplate.postForObject(eq("http://ai-server/check"), any(), eq(String.class)))
                .willReturn("{\"result\":\"Abnormal\"}");

        // when
        boolean result = detector.detectAnomaly(fifteenHeartRates());

        // then
        assertThat(result).isTrue();
    }

    private HeartRateAnomalyDetector detector() {
        AiProperties properties = new AiProperties(new AiProperties.Server("http://ai-server"));
        return new HeartRateAnomalyDetector(aiRestTemplate, properties, new ObjectMapper());
    }

    private List<Integer> fifteenHeartRates() {
        return List.of(70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84);
    }
}
