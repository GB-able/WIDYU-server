package com.widyu.member.controller;

import com.widyu.admin.dto.response.AdminPageResponse;
import com.widyu.global.response.ApiResponseTemplate;
import com.widyu.member.MemberWithdrawalRequestStatus;
import com.widyu.member.application.MemberWithdrawalRequestService;
import com.widyu.member.controller.docs.AdminMemberWithdrawalRequestDocs;
import com.widyu.member.dto.response.MemberWithdrawalRequestResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 어르신 탈퇴 요청 관리자 API(LLD-0137 3.2절). `/api/v1/admin/**`는 SecurityConfig에서 ROLE_ADMIN만 허용한다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/members/withdrawal-requests")
public class AdminMemberWithdrawalRequestController implements AdminMemberWithdrawalRequestDocs {

    private final MemberWithdrawalRequestService withdrawalRequestService;

    @Override
    @GetMapping
    public ApiResponseTemplate<AdminPageResponse<MemberWithdrawalRequestResponse>> getRequests(
            @RequestParam(required = false) MemberWithdrawalRequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponseTemplate.ok()
                .code("200")
                .message("탈퇴 요청 목록 조회 성공")
                .body(withdrawalRequestService.getRequests(status, page, size));
    }

    @Override
    @PostMapping("/{requestId}/complete")
    public ApiResponseTemplate<MemberWithdrawalRequestResponse> complete(@PathVariable Long requestId) {
        return ApiResponseTemplate.ok()
                .code("200")
                .message("탈퇴 요청 처리 완료 기록 성공")
                .body(withdrawalRequestService.complete(requestId));
    }

    @Override
    @PostMapping("/{requestId}/cancel")
    public ApiResponseTemplate<MemberWithdrawalRequestResponse> cancel(@PathVariable Long requestId) {
        return ApiResponseTemplate.ok()
                .code("200")
                .message("탈퇴 요청 취소 기록 성공")
                .body(withdrawalRequestService.cancel(requestId));
    }
}
