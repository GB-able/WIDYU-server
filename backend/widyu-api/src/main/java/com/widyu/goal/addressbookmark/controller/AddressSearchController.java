package com.widyu.goal.addressbookmark.controller;

import com.widyu.global.response.ApiResponseTemplate;
import com.widyu.goal.addressbookmark.application.AddressSearchService;
import com.widyu.goal.addressbookmark.application.GeocodingService;
import com.widyu.goal.addressbookmark.controller.docs.AddressSearchDocs;
import com.widyu.goal.addressbookmark.dto.response.AddressSearchResponse;
import com.widyu.goal.addressbookmark.dto.response.GeocodingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/goals/address-search")
public class AddressSearchController implements AddressSearchDocs {

    private final AddressSearchService addressSearchService;
    private final GeocodingService geocodingService;

    @GetMapping
    public ApiResponseTemplate<AddressSearchResponse> searchAddress(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponseTemplate.ok()
                .code("200")
                .message("주소 검색 성공")
                .body(addressSearchService.search(keyword, page, size));
    }

    @GetMapping("/geocode")
    public ApiResponseTemplate<GeocodingResponse> geocodeAddress(
            @RequestParam String address
    ) {
        return ApiResponseTemplate.ok()
                .code("200")
                .message("좌표 변환 성공")
                .body(geocodingService.geocode(address));
    }
}
