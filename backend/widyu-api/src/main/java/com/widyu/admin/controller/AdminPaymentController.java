package com.widyu.admin.controller;

import com.widyu.admin.application.AdminPaymentService;
import com.widyu.admin.dto.response.AdminPageResponse;
import com.widyu.admin.dto.response.AdminPaymentResponse;
import com.widyu.global.response.ApiResponseTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
public class AdminPaymentController {

    private final AdminPaymentService adminPaymentService;

    @GetMapping("/payments")
    public ApiResponseTemplate<AdminPageResponse<AdminPaymentResponse>> getPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponseTemplate.ok()
                .code("200")
                .message("결제 목록 조회 성공")
                .body(adminPaymentService.getPaymentPage(page, size));
    }
}
