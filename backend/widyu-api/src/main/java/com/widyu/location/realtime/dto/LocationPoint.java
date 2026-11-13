package com.widyu.location.realtime.dto;

import java.time.LocalDateTime;

/**
 * 이동 경로의 점. {@code timestamp}는 서버가 점을 저장한 시각, {@code measuredAtMs}는 그 위치의 순서 시각
 * {@code min(ts_ms, 서버 수신 시각)}이다(LLD-0141). 최근 15분 경로는 {@code measuredAtMs}로 거르고, 이 필드가 없던 예전 점은
 * {@code timestamp}로 거른다.
 */
public record LocationPoint(
        Double latitude,
        Double longitude,
        LocalDateTime timestamp,
        Long measuredAtMs
) {
    public static LocationPoint of(Double latitude, Double longitude, Long measuredAtMs) {
        return new LocationPoint(latitude, longitude, LocalDateTime.now(), measuredAtMs);
    }
}
