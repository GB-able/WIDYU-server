package com.widyu.goal.addressbookmark.dto.response;

import com.widyu.addressbookmark.AddressBookmark;

public record AddressBookmarkIdResponse(
        Long addressBookmarkId
) {
    public static AddressBookmarkIdResponse of(AddressBookmark addressBookmark) {
        return new AddressBookmarkIdResponse(addressBookmark.getId());
    }
}
