package com.widyu.heart.dto.response;

import com.widyu.heart.HeartRateStatus;
import java.time.LocalDateTime;

public record HeartGraphCurrentResponse(
        Integer heartRate,
        LocalDateTime measuredAt,
        Integer maxHeartRate,
        Integer minHeartRate,
        HeartRateStatus status
) {
    public static HeartGraphCurrentResponse of(
            Integer heartRate, LocalDateTime measuredAt,
            Integer maxHeartRate, Integer minHeartRate, HeartRateStatus status) {
        return new HeartGraphCurrentResponse(heartRate, measuredAt, maxHeartRate, minHeartRate, status);
    }

    public static HeartGraphCurrentResponse unknown(Integer maxHeartRate, Integer minHeartRate) {
        return new HeartGraphCurrentResponse(null, null, maxHeartRate, minHeartRate, HeartRateStatus.UNKNOWN);
    }
}
