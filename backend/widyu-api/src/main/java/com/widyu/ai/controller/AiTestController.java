package com.widyu.ai.controller;

import com.widyu.ai.application.HeartRateAnomalyService;
import com.widyu.ai.controller.docs.AiTestDocs;
import com.widyu.ai.dto.request.HeartRateAnomalyRequest;
import com.widyu.ai.dto.response.HeartRateAnomalyResponse;
import com.widyu.global.response.ApiResponseTemplate;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ai/test")
public class AiTestController implements AiTestDocs {

    private final HeartRateAnomalyService heartRateAnomalyService;

    @Override
    @PostMapping("/heart-rate-anomaly")
    public ApiResponseTemplate<HeartRateAnomalyResponse> detectHeartRateAnomaly(
            @Valid @RequestBody HeartRateAnomalyRequest request
    ) {
        HeartRateAnomalyResponse response = heartRateAnomalyService.detectAnomaly(request.heartRates());
        return ApiResponseTemplate.ok()
                .code("AI_TEST_2001")
                .message("심박수 이상치 판별 완료")
                .body(response);
    }
}
