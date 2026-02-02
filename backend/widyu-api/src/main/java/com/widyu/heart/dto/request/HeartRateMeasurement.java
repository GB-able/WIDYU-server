package com.widyu.heart.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;

@Schema(description = "심박수 측정 데이터")
public record HeartRateMeasurement(
        @Schema(description = "심박수 (BPM)", example = "82")
        @NotNull(message = "심박수는 필수입니다.")
        @Positive(message = "심박수는 양수여야 합니다.")
        Integer heartRate,

        @Schema(description = "측정 시각", example = "2026-02-01T15:48:00")
        @NotNull(message = "측정 시각은 필수입니다.")
        LocalDateTime measuredAt
) {
}
