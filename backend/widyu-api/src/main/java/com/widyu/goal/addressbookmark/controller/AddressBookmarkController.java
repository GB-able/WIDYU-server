package com.widyu.goal.addressbookmark.controller;

import com.widyu.goal.addressbookmark.application.AddressBookmarkService;
import com.widyu.goal.addressbookmark.controller.docs.AddressBookmarkDocs;
import com.widyu.goal.addressbookmark.dto.request.AddressBookmarkCreateRequest;
import com.widyu.goal.addressbookmark.dto.response.AddressBookmarkResponse;
import com.widyu.global.response.ApiResponseTemplate;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/address-bookmarks")
public class AddressBookmarkController implements AddressBookmarkDocs {

    private final AddressBookmarkService addressBookmarkService;

    @PostMapping
    public ApiResponseTemplate<Void> createAddressBookmark(
        @Valid @RequestBody AddressBookmarkCreateRequest request
    ) {
        addressBookmarkService.create(request);
        return ApiResponseTemplate.ok()
            .code("ADR_2001")
            .message("주소 즐겨찾기가 생성되었습니다.")
            .build();
    }

    @DeleteMapping("/{addressBookmarkId}")
    public ApiResponseTemplate<Void> deleteAddressBookmark(
        @PathVariable Long addressBookmarkId
    ) {
        addressBookmarkService.delete(addressBookmarkId);
        return ApiResponseTemplate.ok()
            .code("ADR_2002")
            .message("주소 즐겨찾기가 삭제되었습니다.")
            .build();
    }

    @GetMapping
    public ApiResponseTemplate<List<AddressBookmarkResponse>> getAddressBookmarks() {
        List<AddressBookmarkResponse> data = addressBookmarkService.findAll();
        return ApiResponseTemplate.ok()
                .code("ADR_2000")
                .message("주소 즐겨찾기 목록이 조회되었습니다.")
                .body(data);
    }

}
