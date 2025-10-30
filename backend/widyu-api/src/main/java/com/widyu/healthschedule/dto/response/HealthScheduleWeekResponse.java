package com.widyu.healthschedule.dto.response;

import com.widyu.healthschedule.HealthSchedule;
import java.time.LocalDateTime;

public record HealthScheduleWeekResponse(
        LocalDateTime datetime,
        Long healthScheduleId,
        Position position
) {
    public static HealthScheduleWeekResponse from(HealthSchedule healthSchedule) {
        return new HealthScheduleWeekResponse(
                healthSchedule.getScheduledAt(),
                healthSchedule.getId(),
                Position.of(healthSchedule.getLatitude(), healthSchedule.getLongitude())
        );
    }
}
