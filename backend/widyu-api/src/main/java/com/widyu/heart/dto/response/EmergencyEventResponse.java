package com.widyu.heart.dto.response;

import com.widyu.heart.HeartRateEmergency;
import java.time.LocalDateTime;

public record EmergencyEventResponse(
        Integer heartRate,
        LocalDateTime measuredAt,
        String location
) {
    public static EmergencyEventResponse from(HeartRateEmergency emergency) {
        return new EmergencyEventResponse(
                emergency.getHeartRate(),
                emergency.getMeasuredAt(),
                emergency.getLocation()
        );
    }
}
