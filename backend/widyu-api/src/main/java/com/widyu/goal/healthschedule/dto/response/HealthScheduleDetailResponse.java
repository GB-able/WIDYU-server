package com.widyu.goal.healthschedule.dto.response;

import com.widyu.healthschedule.HealthSchedule;
import com.widyu.healthschedule.ProgressStatus;
import java.time.LocalDateTime;

public record HealthScheduleDetailResponse(
        Long healthScheduleId,
        LocalDateTime scheduledAt,
        String locationName,
        ProgressStatus progressStatus
) {
    public static HealthScheduleDetailResponse from(HealthSchedule healthSchedule) {
        return new HealthScheduleDetailResponse(
                healthSchedule.getId(),
                healthSchedule.getScheduledAt(),
                healthSchedule.getPlaceAddress(),
                healthSchedule.getProgressStatus()
        );
    }
}