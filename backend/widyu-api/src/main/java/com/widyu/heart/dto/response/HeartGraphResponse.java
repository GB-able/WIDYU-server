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

    /** 진행 중인 위급 사이클이 없을 때의 응답. 그래프에 그릴 구간이 없으므로 events는 비운다. */
    public static HeartGraphResponse forInitialEmpty(HeartGraphCurrentResponse current) {
        return new HeartGraphResponse(current, null, List.of());
    }

    public static HeartGraphResponse forRefreshEmpty(HeartGraphCurrentResponse current) {
        return new HeartGraphResponse(current, null, List.of());
    }
}
