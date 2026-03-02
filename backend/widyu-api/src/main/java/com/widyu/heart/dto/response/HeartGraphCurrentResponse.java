package com.widyu.heart.dto.response;

import com.widyu.heart.HeartRateStatus;
import java.time.LocalDateTime;

public record HeartGraphCurrentResponse(
        Integer heartRate,
        LocalDateTime measuredAt, // 최초 조회 시에만 포함 (갱신 시 null)
        Integer maxHeartRate,
        Integer minHeartRate,
        HeartRateStatus status
) {
    public static HeartGraphCurrentResponse forInitial(
            Integer heartRate, LocalDateTime measuredAt,
            Integer maxHeartRate, Integer minHeartRate, HeartRateStatus status) {
        return new HeartGraphCurrentResponse(heartRate, measuredAt, maxHeartRate, minHeartRate, status);
    }

    public static HeartGraphCurrentResponse forRefresh(
            Integer heartRate, Integer maxHeartRate, Integer minHeartRate, HeartRateStatus status) {
        return new HeartGraphCurrentResponse(heartRate, null, maxHeartRate, minHeartRate, status);
    }
}
