package com.widyu.ai.dto.response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "심박수 이상치 판별 결과")
public record HeartRateAnomalyResponse(
        @Schema(description = "AI 서버 판별 결과 (0: 정상, 1: 비정상)", example = "1")
        Integer result,

        @Schema(description = "판별 결과 설명", example = "비정상")
        String description,

        @Schema(description = "이상 여부", example = "true")
        Boolean isAbnormal
) {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static HeartRateAnomalyResponse of(Integer aiResult) {
        boolean isAbnormal = aiResult != null && aiResult == 1;
        String description = isAbnormal ? "비정상" : "정상";

        return new HeartRateAnomalyResponse(aiResult, description, isAbnormal);
    }

    public static HeartRateAnomalyResponse fromJsonResponse(String jsonResponse) throws JsonProcessingException {
        JsonNode node = objectMapper.readTree(jsonResponse);
        String resultValue = node.get("result").asText();

        boolean isAbnormal = "Abnormal".equalsIgnoreCase(resultValue);
        Integer result = isAbnormal ? 1 : 0;
        String description = isAbnormal ? "비정상" : "정상";

        return new HeartRateAnomalyResponse(result, description, isAbnormal);
    }
}
