package com.widyu.walk.controller;

import com.widyu.global.annotation.ValidateFamilyAccess;
import com.widyu.global.response.ApiResponseTemplate;
import com.widyu.walk.application.WalkService;
import com.widyu.walk.controller.docs.WalkDocs;
import com.widyu.walk.dto.request.SetGoalRequest;
import com.widyu.walk.dto.request.UpdateStepsRequest;
import com.widyu.walk.dto.response.UpdateStepsResponse;
import com.widyu.walk.dto.response.WalkDetailResponse;
import com.widyu.walk.dto.response.WalkMonthlyResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/walks")
public class WalkController implements WalkDocs {

    private final WalkService walkService;

    @Override
    @GetMapping("/monthly")
    @ValidateFamilyAccess(memberIdParam = "memberId")
    public ApiResponseTemplate<WalkMonthlyResponse> getMonthlyStats(
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam(required = false) Long memberId
    ) {
        WalkMonthlyResponse response = walkService.getMonthlyStats(year, month, memberId);
        return ApiResponseTemplate.ok()
                .code("WALK_2001")
                .message("월별 걷기 현황 조회 성공")
                .body(response);
    }

    @Override
    @GetMapping("/detail")
    @ValidateFamilyAccess(memberIdParam = "memberId")
    public ApiResponseTemplate<WalkDetailResponse> getWalkDetail(
            @RequestParam(required = false) Long memberId
    ) {
        WalkDetailResponse response = walkService.getWalkDetail(memberId);
        return ApiResponseTemplate.ok()
                .code("WALK_2002")
                .message("걷기 상세 조회 성공")
                .body(response);
    }

    @Override
    @PostMapping("/goal")
    @ValidateFamilyAccess(memberIdParam = "memberId")
    public ApiResponseTemplate<Void> setOrUpdateGoal(
            @RequestParam(required = false) Long memberId,
            @Valid @RequestBody SetGoalRequest request
    ) {
        walkService.setOrUpdateGoal(memberId, request);
        return ApiResponseTemplate.ok()
                .code("WALK_2003")
                .message("걷기 목표 설정/수정 완료")
                .build();
    }

    @Override
    @PostMapping("/sync")
    public ApiResponseTemplate<UpdateStepsResponse> syncSteps(
            @Valid @RequestBody UpdateStepsRequest request
    ) {
        UpdateStepsResponse response = walkService.updateSteps(request);
        return ApiResponseTemplate.ok()
                .code("WALK_2005")
                .message("걸음 수 연동 완료")
                .body(response);
    }
}
