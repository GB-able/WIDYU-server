package com.widyu.heart.dto.response;

import com.widyu.heart.HeartRateEvent;
import java.time.LocalDateTime;

public record HeartRateEventResponse(
        Integer heartRate,
        LocalDateTime measuredAt
) {
    public static HeartRateEventResponse from(HeartRateEvent event) {
        return new HeartRateEventResponse(event.getHeartRate(), event.getMeasuredAt());
    }
}
