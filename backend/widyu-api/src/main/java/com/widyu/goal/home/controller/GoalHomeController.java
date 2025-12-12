package com.widyu.goal.home.controller;

import com.widyu.global.response.ApiResponseTemplate;
import com.widyu.goal.home.application.GoalHomeService;
import com.widyu.goal.home.controller.docs.GoalHomeDocs;
import com.widyu.goal.home.dto.response.FamilyListResponse;
import com.widyu.goal.home.dto.response.GuardianGoalHomeResponse;
import com.widyu.goal.home.dto.response.GuardianGoalStatsResponse;
import com.widyu.goal.home.dto.response.SeniorGoalHomeResponse;
import com.widyu.goal.home.dto.response.SeniorWeeklyGoalStatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/goals/home")
public class GoalHomeController implements GoalHomeDocs {

    private final GoalHomeService goalHomeService;

    @Override
    @GetMapping("/families")
    public ApiResponseTemplate<FamilyListResponse> getFamilyList() {
        FamilyListResponse response = goalHomeService.getFamilyList();
        return ApiResponseTemplate.ok()
                .code("GOAL_HOME_2001")
                .message("가족 목록 조회 성공")
                .body(response);
    }

    @Override
    @GetMapping("/senior")
    public ApiResponseTemplate<SeniorGoalHomeResponse> getSeniorGoalHome() {
        SeniorGoalHomeResponse response = goalHomeService.getSeniorGoalHome();
        return ApiResponseTemplate.ok()
                .code("GOAL_HOME_2002")
                .message("시니어 목표 홈 조회 성공")
                .body(response);
    }

    @Override
    @GetMapping("/senior/weekly-status")
    public ApiResponseTemplate<SeniorWeeklyGoalStatusResponse> getSeniorWeeklyGoalStatus() {
        SeniorWeeklyGoalStatusResponse response = goalHomeService.getSeniorWeeklyGoalStatus();
        return ApiResponseTemplate.ok()
                .code("GOAL_HOME_2003")
                .message("시니어 주간 목표 달성률 조회 성공")
                .body(response);
    }

    @Override
    @GetMapping("/guardian/stats")
    public ApiResponseTemplate<GuardianGoalStatsResponse> getGuardianGoalStats(
            @RequestParam(required = false) Long memberId
    ) {
        GuardianGoalStatsResponse response = goalHomeService.getGuardianGoalStats(memberId);
        return ApiResponseTemplate.ok()
                .code("GOAL_HOME_2004")
                .message("보호자 목표 현황 조회 성공")
                .body(response);
    }

    @Override
    @GetMapping("/guardian")
    public ApiResponseTemplate<GuardianGoalHomeResponse> getGuardianGoalHome(
            @RequestParam(required = false) Long memberId
    ) {
        GuardianGoalHomeResponse response = goalHomeService.getGuardianGoalHome(memberId);
        return ApiResponseTemplate.ok()
                .code("GOAL_HOME_2005")
                .message("보호자 목표 홈 조회 성공")
                .body(response);
    }
}
