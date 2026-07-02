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
    @NotNull(message = "위도는 필수입니다.")
    Double latitude,
    @NotNull(message = "경도는 필수입니다.")
    Double longitude,
    String name
) {
    public ParentLocation toEntity(Member seniorMember) {
        return ParentLocation.builder()
            .member(seniorMember)
            .locationType(locationType)
            .placeAddress(placeAddress)
            .latitude(latitude)
            .longitude(longitude)
            .name(name)
            .status(Status.ACTIVE)
            .build();
    }
}
