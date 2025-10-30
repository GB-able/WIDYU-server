package com.widyu.addressbookmark.controller;

import com.widyu.addressbookmark.AddressBookmark;
import com.widyu.addressbookmark.application.AddressBookmarkService;
import com.widyu.addressbookmark.controller.docs.AddressBookmarkDocs;
import com.widyu.addressbookmark.dto.request.AddressBookmarkCreateRequest;
import com.widyu.addressbookmark.dto.response.AddressBookmarkResponse;
import com.widyu.global.response.ApiResponseTemplate;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/address-bookmarks")
public class AddressBookmarkController implements AddressBookmarkDocs {

    private final AddressBookmarkService addressBookmarkService;

    @PostMapping
    public ApiResponseTemplate<AddressBookmarkResponse> createAddressBookmark(
        @Valid @RequestBody AddressBookmarkCreateRequest request
    ) {
        AddressBookmark addressBookmark = addressBookmarkService.create(request);
        return ApiResponseTemplate.ok()
            .code("ADR_2001")
            .message("주소 즐겨찾기가 생성되었습니다.")
            .body(AddressBookmarkResponse.of(addressBookmark));
    }
}
