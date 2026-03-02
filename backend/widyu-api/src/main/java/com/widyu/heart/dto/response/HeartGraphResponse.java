package com.widyu.heart.dto.response;

import java.util.List;

public record HeartGraphResponse(
        HeartGraphCurrentResponse current,
        HeartRateEventResponse firstEmergency, // 최초 조회 시에만 포함 (갱신 시 null)
        List<HeartRateEventResponse> events
) {
    public static HeartGraphResponse forInitial(
            HeartGraphCurrentResponse current,
            HeartRateEventResponse firstEmergency,
            List<HeartRateEventResponse> events) {
        return new HeartGraphResponse(current, firstEmergency, events);
    }

    public static HeartGraphResponse forRefresh(
            HeartGraphCurrentResponse current,
            List<HeartRateEventResponse> events) {
        return new HeartGraphResponse(current, null, events);
    }
}
