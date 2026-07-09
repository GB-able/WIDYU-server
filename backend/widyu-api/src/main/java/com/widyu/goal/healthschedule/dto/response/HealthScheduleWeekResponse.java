package com.widyu.goal.healthschedule.dto.response;

import com.widyu.healthschedule.HealthSchedule;
import java.time.LocalDateTime;

public record HealthScheduleWeekResponse(
        LocalDateTime datetime,
        Long healthScheduleId,
        Double latitude,
        Double longitude
) {
    public static HealthScheduleWeekResponse from(HealthSchedule healthSchedule) {
        return new HealthScheduleWeekResponse(
                healthSchedule.getScheduledAt(),
                healthSchedule.getId(),
                healthSchedule.getLatitude(),
                healthSchedule.getLongitude()
        );
    }
}
