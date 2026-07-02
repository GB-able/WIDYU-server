package com.widyu.location.realtime.dto;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

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
    String locationName
) {
    public static LocationUpdateResponse of(Long memberId, String name,
                                              String profileImage,
                                              Double latitude, Double longitude,
                                              LocalDateTime stayStartTime,
                                              String locationType,
                                              String locationName) {
        LocalDateTime now = LocalDateTime.now();
        long durationMinutes = ChronoUnit.MINUTES.between(stayStartTime, now);
        return new LocationUpdateResponse(
            memberId, name, profileImage,
            latitude, longitude, now,
            stayStartTime, durationMinutes, locationType, locationName
        );
    }
}
