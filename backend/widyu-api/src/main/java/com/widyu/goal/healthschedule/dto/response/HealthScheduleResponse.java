package com.widyu.goal.healthschedule.dto.response;

import com.widyu.healthschedule.HealthSchedule;
import java.time.LocalDateTime;

public record HealthScheduleResponse(
        String scheduleName,
        LocalDateTime scheduledAt,
        String placeAddress,
        Double latitude,
        Double longitude
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
