package com.widyu.location.realtime.dto;

import java.time.LocalDateTime;

public record StayInfo(
    Double latitude,
    Double longitude,
    LocalDateTime startTime
) {
    public static StayInfo of(Double latitude, Double longitude) {
        return new StayInfo(latitude, longitude, LocalDateTime.now());
    }
}
