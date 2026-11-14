package com.widyu.location.realtime.dto;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 위치 응답. 실시간 방송, 발신 세션 ACK, REST 마지막 위치가 같이 쓴다.
 *
 * <p>{@code updatedAt}은 이 응답을 만든 서버 시각이다. 위치를 잰 시각이 아니다. 좌표가 언제 잰 것인지는
 * {@code measuredAtMs}, 서버가 받은 시각은 {@code receivedAtMs}, 출처는 {@code locationSource}, 측정 뒤 지난 시간은
 * {@code ageSeconds}로 판단한다(LLD-0141). 네 필드는 모르면 null이다.
 */
public record LocationUpdateResponse(
    Long memberId,
    String name,
    String profileImage,
    Double latitude,
    Double longitude,
    LocalDateTime updatedAt,
    LocalDateTime stayStartTime,
    Long stayDurationMinutes,
    String locationType,
    String locationName,
    Long measuredAtMs,
    Long receivedAtMs,
    LocationSource locationSource,
    Long ageSeconds
) {
    public static LocationUpdateResponse of(Long memberId, String name,
                                              String profileImage,
                                              Double latitude, Double longitude,
                                              LocalDateTime stayStartTime,
                                              String locationType,
                                              String locationName,
                                              LocationFreshness freshness) {
        LocalDateTime now = LocalDateTime.now();
        // 체류 정보가 없으면 시작 시각과 체류 시간을 비운다. 지금 시각으로 채우면 방금 도착한 것처럼 보인다(LLD-0141).
        Long durationMinutes = null;
        if (stayStartTime != null) {
            durationMinutes = ChronoUnit.MINUTES.between(stayStartTime, now);
        }
        return new LocationUpdateResponse(
            memberId, name, profileImage,
            latitude, longitude, now,
            stayStartTime, durationMinutes, locationType, locationName,
            freshness.measuredAtMs(), freshness.receivedAtMs(), freshness.source(), freshness.ageSeconds()
        );
    }

    /** 실시간 경로에 반영하지 않은 위치의 ACK. 받은 좌표만 돌려주고 체류 정보는 비운다(LLD-0114). */
    public static LocationUpdateResponse received(Long memberId, Double latitude, Double longitude,
                                                  LocationFreshness freshness) {
        return new LocationUpdateResponse(
            memberId, null, null,
            latitude, longitude, LocalDateTime.now(),
            null, null, null, null,
            freshness.measuredAtMs(), freshness.receivedAtMs(), freshness.source(), freshness.ageSeconds()
        );
    }
}
