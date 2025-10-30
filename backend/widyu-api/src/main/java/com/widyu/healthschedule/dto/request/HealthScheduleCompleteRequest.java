package com.widyu.healthschedule.dto.request;

import jakarta.validation.constraints.NotNull;

public record HealthScheduleCompleteRequest(
        @NotNull(message = "일정 ID는 필수입니다.")
        Long healthScheduleId
) {
}