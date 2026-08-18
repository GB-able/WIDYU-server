package com.widyu.heart.dto.response;

import com.widyu.heart.HeartRateEmergency;
import java.time.LocalDateTime;

public record RecentEmergencyResponse(
        boolean detected,
        EmergencyEventResponse emergency, // detected=false 이면 null
        LocalDateTime cycleExpiresAt // 위험 상태가 유지되는 시각. 그 전에 다시 감지되면 연장된다
) {
    public static RecentEmergencyResponse from(HeartRateEmergency emergency, LocalDateTime cycleExpiresAt) {
        return new RecentEmergencyResponse(true, EmergencyEventResponse.from(emergency), cycleExpiresAt);
    }

    public static RecentEmergencyResponse notDetected() {
        return new RecentEmergencyResponse(false, null, null);
    }
}
