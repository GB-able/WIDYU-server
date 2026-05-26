package com.widyu.admin.controller;

import com.widyu.admin.application.AdminFcmStatsService;
import com.widyu.admin.dto.response.AdminFcmStatsResponse;
import com.widyu.global.response.ApiResponseTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
public class AdminFcmStatsController {

    private final AdminFcmStatsService adminFcmStatsService;

    @GetMapping("/fcm/stats")
    public ApiResponseTemplate<AdminFcmStatsResponse> getStats() {
        return ApiResponseTemplate.ok()
                .code("200")
                .message("FCM 통계 조회 성공")
                .body(adminFcmStatsService.getStats());
    }
}
