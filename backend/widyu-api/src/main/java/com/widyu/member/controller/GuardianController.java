package com.widyu.member.controller;

import com.widyu.member.application.FamilyConnectionService;
import com.widyu.member.controller.docs.GuardianDocs;
import com.widyu.member.dto.request.FamilyJoinRequest;
import com.widyu.member.dto.response.FamilyJoinResponse;
import com.widyu.global.response.ApiResponseTemplate;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/members/guardians")
public class GuardianController implements GuardianDocs {

    private final FamilyConnectionService familyConnectionService;

    @Override
    @PostMapping("/family/join")
    public ApiResponseTemplate<FamilyJoinResponse> joinFamily(@Valid @RequestBody FamilyJoinRequest request) {
        FamilyJoinResponse response = familyConnectionService.joinFamily(request);
        return ApiResponseTemplate.ok()
                .code("GUARDIAN_2001")
                .message("가족 참여 성공")
                .body(response);
    }
}
