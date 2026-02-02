package com.widyu.heart.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;

public record HeartRateMeasurement(
        @NotNull(message = "심박수는 필수입니다.")
        @Positive(message = "심박수는 양수여야 합니다.")
        Integer heartRate,

        @NotNull(message = "측정 시각은 필수입니다.")
        LocalDateTime measuredAt
) {
}
