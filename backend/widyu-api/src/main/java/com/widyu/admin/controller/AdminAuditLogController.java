package com.widyu.admin.controller;

import com.widyu.admin.AdminAction;
import com.widyu.admin.application.AdminAuditLogService;
import com.widyu.admin.dto.response.AdminAuditLogResponse;
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
public class AdminAuditLogController {

    private final AdminAuditLogService adminAuditLogService;

    @GetMapping("/audit-logs")
    public ApiResponseTemplate<AdminPageResponse<AdminAuditLogResponse>> getLogs(
            @RequestParam(required = false) AdminAction action,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return ApiResponseTemplate.ok()
                .code("200")
                .message("감사 로그 조회 성공")
                .body(adminAuditLogService.getLogs(action, page, size));
    }
}
