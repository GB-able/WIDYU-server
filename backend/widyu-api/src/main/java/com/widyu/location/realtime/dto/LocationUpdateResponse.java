package com.widyu.location.realtime.dto;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public record LocationUpdateResponse(
    Long seniorId,
    String seniorName,
    String seniorProfileImage,
    Double latitude,
    Double longitude,
    LocalDateTime updatedAt,
    LocalDateTime stayStartTime,
    Long stayDurationMinutes
) {
    public static LocationUpdateResponse of(Long seniorId, String seniorName,
                                              String seniorProfileImage,
                                              Double latitude, Double longitude) {
        LocalDateTime now = LocalDateTime.now();
        return new LocationUpdateResponse(
            seniorId, seniorName, seniorProfileImage,
            latitude, longitude, now,
            now, 0L
        );
    }

    public static LocationUpdateResponse of(Long seniorId, String seniorName,
                                              String seniorProfileImage,
                                              Double latitude, Double longitude,
                                              LocalDateTime stayStartTime) {
        LocalDateTime now = LocalDateTime.now();
        long durationMinutes = ChronoUnit.MINUTES.between(stayStartTime, now);
        return new LocationUpdateResponse(
            seniorId, seniorName, seniorProfileImage,
            latitude, longitude, now,
            stayStartTime, durationMinutes
        );
    }
}
