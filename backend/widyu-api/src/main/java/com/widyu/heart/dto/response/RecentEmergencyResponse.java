package com.widyu.heart.dto.response;

public record RecentEmergencyResponse(
        boolean detected
) {
    public static RecentEmergencyResponse of(boolean detected) {
        return new RecentEmergencyResponse(detected);
    }
}
