package com.widyu.goal.healthschedule.dto.response;

public record Position(
        String lat,
        String lon
) {
    public static Position of(String latitude, String longitude) {
        return new Position(latitude, longitude);
    }
}
