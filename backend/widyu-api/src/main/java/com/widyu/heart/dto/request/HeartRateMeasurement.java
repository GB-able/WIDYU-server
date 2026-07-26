package com.widyu.heart.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record HeartRateMeasurement(
        @NotNull(message = "심박수는 필수입니다.")
        @Min(value = 0, message = "심박수는 0 이상이어야 합니다.")
        @Max(value = 300, message = "심박수는 300 이하여야 합니다.")
        Integer heartRate,

        @NotNull(message = "측정 시각은 필수입니다.")
        LocalDateTime measuredAt
) {
}
