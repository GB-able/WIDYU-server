package com.widyu.location.realtime.dto;

import java.time.LocalDateTime;

public record LocationPoint(
        Double latitude,
        Double longitude,
        LocalDateTime timestamp
) {
    public static LocationPoint of(Double latitude, Double longitude) {
        return new LocationPoint(latitude, longitude, LocalDateTime.now());
    }
}
