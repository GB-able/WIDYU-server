package com.widyu.location.realtime.dto;

import java.time.LocalDateTime;

public record LocationUpdateResponse(
    Long seniorId,
    String seniorName,
    String seniorProfileImage,
    Double latitude,
    Double longitude,
    LocalDateTime updatedAt
) {
    public static LocationUpdateResponse of(Long seniorId, String seniorName,
                                              String seniorProfileImage,
                                              Double latitude, Double longitude) {
        return new LocationUpdateResponse(
            seniorId, seniorName, seniorProfileImage,
            latitude, longitude, LocalDateTime.now()
        );
    }
}
