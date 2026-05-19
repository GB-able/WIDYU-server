package com.widyu.admin.controller;

import com.widyu.admin.application.AdminDashboardService;
import com.widyu.admin.dto.response.AdminDashboardResponse;
import com.widyu.global.response.ApiResponseTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @GetMapping("/dashboard")
    public ApiResponseTemplate<AdminDashboardResponse> getDashboard() {
        return ApiResponseTemplate.ok()
                .code("200")
                .message("대시보드 조회 성공")
                .body(adminDashboardService.getDashboard());
    }
}
