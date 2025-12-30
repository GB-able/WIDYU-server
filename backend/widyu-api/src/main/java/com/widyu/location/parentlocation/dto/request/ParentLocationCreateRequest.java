package com.widyu.location.parentlocation.dto.request;

import com.widyu.global.entity.Status;
import com.widyu.member.Member;
import com.widyu.parentlocation.LocationType;
import com.widyu.parentlocation.ParentLocation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ParentLocationCreateRequest(
    @NotNull(message = "시니어 회원 ID는 필수입니다.")
    Long memberId,
    @NotNull(message = "장소 타입은 필수입니다.")
    LocationType locationType,
    @NotBlank(message = "장소 주소는 필수입니다.")
    String placeAddress,
    @NotBlank(message = "위도는 필수입니다.")
    String latitude,
    @NotBlank(message = "경도는 필수입니다.")
    String longitude
) {
    public ParentLocation toEntity(Member seniorMember) {
        return ParentLocation.builder()
            .member(seniorMember)
            .locationType(locationType)
            .placeAddress(placeAddress)
            .latitude(latitude)
            .longitude(longitude)
            .status(Status.ACTIVE)
            .build();
    }
}
