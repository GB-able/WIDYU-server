package com.widyu.goal.healthschedule.dto.response;

import com.widyu.healthschedule.HealthSchedule;
import com.widyu.healthschedule.ProgressStatus;
import java.time.LocalDateTime;

public record HealthScheduleDetailResponse(
        Long healthScheduleId,
        String scheduleName,
        LocalDateTime scheduledAt,
        String placeAddress,
        Double latitude,
        Double longitude,
        ProgressStatus progressStatus
) {
    public static HealthScheduleDetailResponse from(HealthSchedule healthSchedule) {
        return new HealthScheduleDetailResponse(
                healthSchedule.getId(),
                healthSchedule.getScheduleName(),
                healthSchedule.getScheduledAt(),
                healthSchedule.getPlaceAddress(),
                healthSchedule.getLatitude(),
                healthSchedule.getLongitude(),
                healthSchedule.getProgressStatus()
        );
    }
}