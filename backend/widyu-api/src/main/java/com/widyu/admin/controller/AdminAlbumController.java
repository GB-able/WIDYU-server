package com.widyu.admin.controller;

import com.widyu.admin.application.AdminAlbumService;
import com.widyu.admin.dto.response.AdminAlbumResponse;
import com.widyu.admin.dto.response.AdminPageResponse;
import com.widyu.global.response.ApiResponseTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
public class AdminAlbumController {

    private final AdminAlbumService adminAlbumService;

    @GetMapping("/albums")
    public ApiResponseTemplate<AdminPageResponse<AdminAlbumResponse>> getAlbums(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponseTemplate.ok()
                .code("200")
                .message("앨범 목록 조회 성공")
                .body(adminAlbumService.getAlbumPage(page, size));
    }
}
