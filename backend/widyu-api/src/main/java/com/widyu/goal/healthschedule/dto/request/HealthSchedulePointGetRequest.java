package com.widyu.goal.healthschedule.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record HealthSchedulePointGetRequest(
        Long healthScheduleId
) {
}