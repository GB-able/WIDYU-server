package com.widyu.heart.controller;

import com.widyu.global.annotation.ValidateFamilyAccess;
import com.widyu.global.response.ApiResponseTemplate;
import com.widyu.global.util.SecurityUtil;
import com.widyu.heart.application.HeartMessageService;
import com.widyu.heart.application.HeartRateService;
import com.widyu.heart.controller.docs.HeartRateDocs;
import com.widyu.heart.dto.request.HeartMessageRequest;
import com.widyu.heart.dto.response.HeartRateStatusResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/heart-rate")
public class HeartRateController implements HeartRateDocs {

    private final HeartRateService heartRateService;
    private final HeartMessageService heartMessageService;
    private final SecurityUtil securityUtil;

    @Override
    @GetMapping("/status")
    @ValidateFamilyAccess(memberIdParam = "memberId")
    public ApiResponseTemplate<HeartRateStatusResponse> getHeartRateStatus(
            @RequestParam(required = false) Long memberId
    ) {
        Long targetMemberId = memberId != null ? memberId : securityUtil.getCurrentMemberId();
        HeartRateStatusResponse response = heartRateService.getHeartRateStatus(targetMemberId);
        return ApiResponseTemplate.ok()
                .code("HEART_2001")
                .message("심박수 이상치 조회 완료")
                .body(response);
    }

    @Override
    @PostMapping("/message")
    public ApiResponseTemplate<Void> sendHeartMessage(
            @Valid @RequestBody HeartMessageRequest request
    ) {
        heartMessageService.sendHeartMessage(request);
        return ApiResponseTemplate.ok()
                .code("HEART_2003")
                .message("메시지 전송 완료")
                .build();
    }
}
