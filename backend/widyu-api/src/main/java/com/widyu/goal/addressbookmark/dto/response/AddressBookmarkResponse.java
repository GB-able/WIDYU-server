package com.widyu.goal.addressbookmark.dto.response;

import com.widyu.addressbookmark.AddressBookmark;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class AddressBookmarkResponse {
    private Long addressBookmarkId;
    private String roadAddress;
    private String address;
    private String name;
    private Double latitude;
    private Double longitude;
    private String road;
    private String jibun;

    public static AddressBookmarkResponse of(AddressBookmark addressBookmark) {
        return AddressBookmarkResponse.builder()
            .addressBookmarkId(addressBookmark.getId())
            .roadAddress(addressBookmark.getRoadAddress())
            .address(addressBookmark.getAddress())
                .name(addressBookmark.getName())
                .latitude(addressBookmark.getLatitude())
                .longitude(addressBookmark.getLongitude())
                .road(addressBookmark.getRoad())
                .jibun(addressBookmark.getJibun())
            .build();
    }
}
