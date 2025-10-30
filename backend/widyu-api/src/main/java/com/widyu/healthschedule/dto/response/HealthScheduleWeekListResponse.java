package com.widyu.healthschedule.dto.response;

import com.widyu.healthschedule.HealthSchedule;
import java.util.List;

public record HealthScheduleWeekListResponse(
        List<HealthScheduleWeekResponse> schedules
) {
    public static HealthScheduleWeekListResponse from(List<HealthSchedule> healthSchedules) {
        List<HealthScheduleWeekResponse> schedules = healthSchedules.stream()
                .map(HealthScheduleWeekResponse::from)
                .toList();

        return new HealthScheduleWeekListResponse(schedules);
    }
}