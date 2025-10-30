package com.widyu.addressbookmark.dto.request;

import com.widyu.addressbookmark.AddressBookmark;
import com.widyu.member.Member;
import jakarta.validation.constraints.NotBlank;

public record AddressBookmarkCreateRequest(
    @NotBlank(message = "도로명 주소는 필수입니다.")
    String roadAddress,
    @NotBlank(message = "지번 주소는 필수입니다.")
    String address
) {
    public AddressBookmark toEntity(Member member) {
        return AddressBookmark.builder()
            .member(member)
            .roadAddress(roadAddress)
            .address(address)
            .build();
    }
}
