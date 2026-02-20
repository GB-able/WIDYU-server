package com.widyu.goal.addressbookmark.dto.request;

import com.widyu.addressbookmark.AddressBookmark;
import com.widyu.global.entity.Status;
import com.widyu.member.Member;
import jakarta.validation.constraints.NotBlank;

public record AddressBookmarkCreateRequest(
        @NotBlank(message = "도로명 주소는 필수입니다.")
        String roadAddress,
        @NotBlank(message = "지번 주소는 필수입니다.")
        String address,
        String name,
        String latitude,
        String longitude,
        String road,
        String jibun
) {
    public AddressBookmark toEntity(Member member) {
        return AddressBookmark.builder()
                .member(member)
                .roadAddress(roadAddress)
                .address(address)
                .name(name)
                .latitude(latitude)
                .longitude(longitude)
                .road(road)
                .jibun(jibun)
                .status(Status.ACTIVE)
                .build();
    }
}
