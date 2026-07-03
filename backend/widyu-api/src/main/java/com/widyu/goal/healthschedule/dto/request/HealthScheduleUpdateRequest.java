package com.widyu.goal.healthschedule.dto.request;

import com.widyu.healthschedule.ProgressStatus;
import java.time.LocalDateTime;

public record HealthScheduleUpdateRequest(
        String scheduleName,
        String placeAddress,
        Double latitude,
        Double longitude,
        LocalDateTime scheduledAt,
        ProgressStatus progressStatus
) {
}
