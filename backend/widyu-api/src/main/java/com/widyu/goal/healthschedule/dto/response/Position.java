package com.widyu.goal.healthschedule.dto.response;

public record Position(
        Double lat,
        Double lon
) {
    public static Position of(Double latitude, Double longitude) {
        return new Position(latitude, longitude);
    }
}
