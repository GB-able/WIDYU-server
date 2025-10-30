package com.widyu.healthschedule.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record HealthScheduleCreateRequest(
        @NotBlank(message = "일정 이름은 필수입니다.")
        String scheduleName,

        String placeAddress,
        String latitude,
        String longitude,

        @NotNull(message = "일정 날짜는 필수입니다.")
        LocalDateTime scheduledAt
) {
}