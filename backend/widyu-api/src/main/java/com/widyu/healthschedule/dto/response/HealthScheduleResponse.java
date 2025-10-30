package com.widyu.healthschedule.dto.response;

import com.widyu.healthschedule.HealthSchedule;
import com.widyu.healthschedule.ProgressStatus;
import java.time.LocalDateTime;

public record HealthScheduleResponse(
        Long id,
        String scheduleName,
        String placeAddress,
        String latitude,
        String longitude,
        LocalDateTime scheduledAt,
        ProgressStatus progressStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static HealthScheduleResponse from(HealthSchedule healthSchedule) {
        return new HealthScheduleResponse(
                healthSchedule.getId(),
                healthSchedule.getScheduleName(),
                healthSchedule.getPlaceAddress(),
                healthSchedule.getLatitude(),
                healthSchedule.getLongitude(),
                healthSchedule.getScheduledAt(),
                healthSchedule.getProgressStatus(),
                healthSchedule.getCreatedAt(),
                healthSchedule.getUpdatedAt()
        );
    }
}
