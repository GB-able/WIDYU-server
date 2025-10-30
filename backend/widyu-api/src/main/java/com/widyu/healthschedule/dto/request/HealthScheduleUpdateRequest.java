package com.widyu.healthschedule.dto.request;

import com.widyu.healthschedule.ProgressStatus;
import java.time.LocalDateTime;

public record HealthScheduleUpdateRequest(
        String scheduleName,
        String placeAddress,
        String latitude,
        String longitude,
        LocalDateTime scheduledAt,
        ProgressStatus progressStatus
) {
}
