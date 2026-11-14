package com.widyu.location.realtime.dto;

import com.widyu.location.stay.SeniorStayState;
import java.time.LocalDateTime;

public record StayInfo(
    Double latitude,
    Double longitude,
    LocalDateTime startTime,
    String locationType,
    String locationName
) {
    public static StayInfo from(SeniorStayState state) {
        return new StayInfo(state.getLatitude(), state.getLongitude(), state.getStayStartedAt(),
                state.getLocationType(), state.getLocationName());
    }
}
