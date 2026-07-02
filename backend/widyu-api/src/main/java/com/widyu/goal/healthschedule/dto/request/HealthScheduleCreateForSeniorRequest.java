package com.widyu.goal.healthschedule.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record HealthScheduleCreateForSeniorRequest(
        @NotNull(message = "시니어 회원 ID는 필수입니다.")
        Long memberId,

        @NotBlank(message = "일정 이름은 필수입니다.")
        String scheduleName,

        String placeAddress,
        Double latitude,
        Double longitude,

        @NotNull(message = "일정 날짜는 필수입니다.")
        LocalDateTime scheduledAt
) {
}
