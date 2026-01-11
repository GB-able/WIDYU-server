package com.widyu.location.realtime.dto;

import java.util.List;

public record LocationTrailResponse(
    Long seniorId,
    String seniorName,
    String seniorProfileImage,
    List<LocationPoint> trail,  // 1시간 동안의 이동 경로
    Integer totalPoints         // 총 포인트 개수
) {
    public static LocationTrailResponse of(Long seniorId, String seniorName,
                                            String seniorProfileImage,
                                            List<LocationPoint> trail) {
        return new LocationTrailResponse(
            seniorId,
            seniorName,
            seniorProfileImage,
            trail,
            trail.size()
        );
    }
}
