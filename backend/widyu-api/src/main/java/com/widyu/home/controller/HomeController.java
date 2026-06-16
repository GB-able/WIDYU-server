package com.widyu.home.controller;

import com.widyu.global.annotation.ValidateFamilyAccess;
import com.widyu.global.response.ApiResponseTemplate;
import com.widyu.home.application.GuardianHomeService;
import com.widyu.home.application.SeniorHomeService;
import com.widyu.home.controller.docs.HomeDocs;
import com.widyu.home.dto.response.GuardianHomeCardsResponse;
import com.widyu.home.dto.response.GuardianSeniorListResponse;
import com.widyu.home.dto.response.SeniorHomeCardsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/home")
public class HomeController implements HomeDocs {

    private final SeniorHomeService seniorHomeService;
    private final GuardianHomeService guardianHomeService;

    @Override
    @GetMapping("/senior/cards")
    public ApiResponseTemplate<SeniorHomeCardsResponse> getSeniorHomeCards() {
        SeniorHomeCardsResponse response = seniorHomeService.getHomeCards();
        return ApiResponseTemplate.ok()
                .code("HOME_2001")
                .message("시니어 홈 카드 조회 성공")
                .body(response);
    }

    @Override
    @GetMapping("/guardian/cards")
    @ValidateFamilyAccess(memberIdParam = "memberId")
    public ApiResponseTemplate<GuardianHomeCardsResponse> getGuardianHomeCards(
            @RequestParam(required = false) Long memberId
    ) {
        GuardianHomeCardsResponse response = guardianHomeService.getHomeCards(memberId);
        return ApiResponseTemplate.ok()
                .code("HOME_2002")
                .message("보호자 홈 카드 조회 성공")
                .body(response);
    }

    @Override
    @GetMapping("/guardian/seniors")
    public ApiResponseTemplate<GuardianSeniorListResponse> getGuardianSeniors() {
        GuardianSeniorListResponse response = guardianHomeService.getFamilySeniors();
        return ApiResponseTemplate.ok()
                .code("HOME_2001")
                .message("보호자 가족 시니어 목록 조회 성공")
                .body(response);
    }
}
