package com.widyu.goal.home.controller;

import com.widyu.global.response.ApiResponseTemplate;
import com.widyu.goal.home.application.GoalHomeService;
import com.widyu.goal.home.controller.docs.FamilyHomeDocs;
import com.widyu.goal.home.dto.response.FamilyListResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/goal/home")
public class GoalHomeController implements FamilyHomeDocs {

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
}
