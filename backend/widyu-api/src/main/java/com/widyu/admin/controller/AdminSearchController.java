package com.widyu.admin.controller;

import com.widyu.admin.application.AdminSearchService;
import com.widyu.admin.dto.response.AdminSearchResponse;
import com.widyu.global.response.ApiResponseTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
public class AdminSearchController {

    private final AdminSearchService adminSearchService;

    @GetMapping("/search")
    public ApiResponseTemplate<AdminSearchResponse> search(@RequestParam String q) {
        return ApiResponseTemplate.ok()
                .code("200")
                .message("검색 성공")
                .body(adminSearchService.search(q));
    }
}
