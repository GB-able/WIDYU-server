package com.widyu.location.realtime.dto;

import jakarta.validation.constraints.NotNull;

public record LocationUpdateRequest(
    @NotNull(message = "멤버 ID는 필수입니다")
    Long memberId,

    @NotNull(message = "위도는 필수입니다")
    Double latitude,

    @NotNull(message = "경도는 필수입니다")
    Double longitude,

    Long timestamp  // 클라이언트에서 측정한 시각 (optional)
) {

    public static LocationUpdateRequest of(Long memberId, Double latitude, Double longitude, Long timestamp) {
        return new LocationUpdateRequest(memberId, latitude, longitude, timestamp);
    }
}
