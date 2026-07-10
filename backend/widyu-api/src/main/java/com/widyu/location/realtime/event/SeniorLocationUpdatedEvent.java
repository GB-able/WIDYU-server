package com.widyu.location.realtime.event;

public record SeniorLocationUpdatedEvent(
        Long memberId,
        Double latitude,
        Double longitude
) {
}
