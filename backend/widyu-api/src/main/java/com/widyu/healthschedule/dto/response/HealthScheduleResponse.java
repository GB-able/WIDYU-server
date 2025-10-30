package com.widyu.healthschedule.dto.response;

import com.widyu.healthschedule.HealthSchedule;
import com.widyu.healthschedule.ProgressStatus;
import java.time.LocalDateTime;

public record HealthScheduleResponse(
        String scheduleName,
        LocalDateTime scheduledAt,
        String placeAddress,
        String latitude,
        String longitude
) {

    public static HealthScheduleResponse from(HealthSchedule healthSchedule) {
        return new HealthScheduleResponse(
                healthSchedule.getScheduleName(),
                healthSchedule.getScheduledAt(),
                healthSchedule.getPlaceAddress(),
                healthSchedule.getLatitude(),
                healthSchedule.getLongitude()
        );
    }
}
