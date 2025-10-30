package com.widyu.addressbookmark.dto.response;

import com.widyu.addressbookmark.AddressBookmark;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class AddressBookmarkResponse {
    private Long id;
    private String roadAddress;
    private String address;

    public static AddressBookmarkResponse of(AddressBookmark addressBookmark) {
        return AddressBookmarkResponse.builder()
            .id(addressBookmark.getId())
            .roadAddress(addressBookmark.getRoadAddress())
            .address(addressBookmark.getAddress())
            .build();
    }
}
