package com.widyu.heart.dto.response;

import java.util.List;

public record EmergencyHistoryResponse(
        long emergencyCount,
        int totalDuration,
        List<EmergencyEventResponse> events
) {
}
