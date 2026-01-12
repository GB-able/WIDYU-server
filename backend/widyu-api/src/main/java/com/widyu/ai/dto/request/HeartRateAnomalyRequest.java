package com.widyu.ai.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "심박수 이상치 판별 요청")
public record HeartRateAnomalyRequest(
        @Schema(description = "심박수 데이터 (15개)", example = "[82,85,83,90,120,100,84,88,92,95,100,85,80,77,125]")
        @NotNull(message = "심박수 데이터는 필수입니다.")
        @Size(min = 15, max = 15, message = "심박수 데이터는 정확히 15개여야 합니다.")
        List<Integer> heartRates
) {
}
