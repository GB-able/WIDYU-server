package com.widyu.heart.dto.response;

import com.widyu.heart.HeartRateEmergency;

public record RecentEmergencyResponse(
        boolean detected,
        EmergencyEventResponse emergency // detected=false 이면 null
) {
    public static RecentEmergencyResponse from(HeartRateEmergency emergency) {
        return new RecentEmergencyResponse(true, EmergencyEventResponse.from(emergency));
    }

    public static RecentEmergencyResponse notDetected() {
        return new RecentEmergencyResponse(false, null);
    }
}
