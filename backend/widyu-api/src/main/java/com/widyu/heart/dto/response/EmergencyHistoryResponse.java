package com.widyu.heart.dto.response;

import java.util.List;

public record EmergencyHistoryResponse(
        long emergencyCount,
        int totalDuration,
        List<EmergencyEventResponse> events
) {
    public static EmergencyHistoryResponse of(long emergencyCount, int totalDuration, List<EmergencyEventResponse> events) {
        return new EmergencyHistoryResponse(emergencyCount, totalDuration, events);
    }
}
