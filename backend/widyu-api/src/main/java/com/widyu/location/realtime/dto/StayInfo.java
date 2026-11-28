package com.widyu.location.realtime.dto;

import com.widyu.location.stay.SeniorStayState;
import java.time.LocalDateTime;

/**
 * 체류 정보. {@code startTime}은 30m 체류 시작이고, 응답의 체류 시간은 {@code zoneChangedAt}(안전구역 상태가 바뀐 시각)부터
 * 센다(LLD-0191). 예전 Redis 체류 값을 읽을 때도 쓰며, 그 값에는 {@code zoneChangedAt}이 없어 null이다.
 */
public record StayInfo(
    Double latitude,
    Double longitude,
    LocalDateTime startTime,
    String locationType,
    String locationName,
    LocalDateTime zoneChangedAt
) {
    public static StayInfo from(SeniorStayState state) {
        return new StayInfo(state.getLatitude(), state.getLongitude(), state.getStayStartedAt(),
                state.getLocationType(), state.getLocationName(), state.getZoneChangedAt());
    }
}
