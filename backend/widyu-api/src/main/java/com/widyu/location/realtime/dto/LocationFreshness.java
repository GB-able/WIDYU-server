package com.widyu.location.realtime.dto;

/**
 * 좌표의 신선도(LLD-0141). {@code measuredAtMs}는 앱이 보낸 측정 시각 그대로이고(기기 시계가 앞서면 서버보다 늦을 수 있다),
 * {@code receivedAtMs}는 서버가 그 위치를 받은 시각이다. 모르면 null이다. {@code ageSeconds}는 응답을 만든 서버 시각에서
 * 측정 시각을 뺀 값이며 측정 시각이 미래면 0이다. 오래됐다는 것만으로 기기가 꺼졌다고 판단하지 않는다.
 */
public record LocationFreshness(Long measuredAtMs, Long receivedAtMs, LocationSource source, Long ageSeconds) {

    public static LocationFreshness of(Long measuredAtMs, Long receivedAtMs, LocationSource source, long nowMs) {
        return new LocationFreshness(measuredAtMs, receivedAtMs, source, ageSeconds(measuredAtMs, nowMs));
    }

    /** 체류 상태 좌표처럼 측정 시각을 모르는 경우. */
    public static LocationFreshness unknown(LocationSource source) {
        return new LocationFreshness(null, null, source, null);
    }

    private static Long ageSeconds(Long measuredAtMs, long nowMs) {
        if (measuredAtMs == null) {
            return null;
        }
        return Math.max(0L, nowMs - measuredAtMs) / 1000;
    }
}
