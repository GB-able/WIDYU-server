package com.widyu.goal.healthschedule.dto.response;

import com.widyu.healthschedule.HealthSchedule;
import com.widyu.healthschedule.ProgressStatus;
import java.time.LocalDate;

public record HealthScheduleDayResponse(
        LocalDate day,
        ProgressStatus progressStatus
) {
    public static HealthScheduleDayResponse from(HealthSchedule healthSchedule) {
        return new HealthScheduleDayResponse(
                healthSchedule.getScheduledAt().toLocalDate(),
                healthSchedule.getProgressStatus()
        );
    }
}