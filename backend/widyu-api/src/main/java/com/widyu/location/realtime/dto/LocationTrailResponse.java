package com.widyu.location.realtime.dto;

import java.util.List;

public record LocationTrailResponse(
    Long memberId,
    String name,
    String profileImage,
    List<LocationPoint> trail,
    Integer totalPoints
) {
    public static LocationTrailResponse of(Long memberId, String name,
                                            String profileImage,
                                            List<LocationPoint> trail) {
        return new LocationTrailResponse(
            memberId,
            name,
            profileImage,
            trail,
            trail.size()
        );
    }
}
