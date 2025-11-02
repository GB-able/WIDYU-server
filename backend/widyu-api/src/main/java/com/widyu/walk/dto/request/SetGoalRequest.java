package com.widyu.walk.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SetGoalRequest(
        @NotNull(message = "목표 걸음 수는 필수입니다.")
        @Min(value = 1, message = "목표 걸음 수는 최소 1보 이상이어야 합니다.")
        @Max(value = 10000, message = "목표 걸음 수는 최대 10000보까지 설정 가능합니다.")
        Integer steps
) {
}
