package com.widyu.mypage.controller;

import com.widyu.global.response.ApiResponseTemplate;
import com.widyu.mypage.application.GuardianMyPageService;
import com.widyu.mypage.controller.docs.GuardianMyPageDocs;
import com.widyu.mypage.dto.request.ProfileImageUploadRequest;
import com.widyu.mypage.dto.request.UpdateNameRequest;
import com.widyu.mypage.dto.request.UpdatePhoneRequest;
import com.widyu.mypage.dto.request.UpdateSeniorAddressRequest;
import com.widyu.mypage.dto.response.ConnectedSeniorResponse;
import com.widyu.mypage.dto.response.FamilyCodeResponse;
import com.widyu.mypage.dto.response.FamilyMemberListResponse;
import com.widyu.mypage.dto.response.GuardianInfoResponse;
import com.widyu.mypage.dto.response.GuardianProfileDetailResponse;
import com.widyu.mypage.dto.response.InviteCodeResponse;
import com.widyu.mypage.dto.response.SeniorProfileForGuardianResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/mypage/guardian")
public class GuardianMyPageController implements GuardianMyPageDocs {

    private final GuardianMyPageService guardianMyPageService;

    @Override
    @GetMapping
    public ApiResponseTemplate<GuardianInfoResponse> getGuardianInfo() {
        return ApiResponseTemplate.ok()
                .code("MYPAGE_2010")
                .message("보호자 내 정보 조회 성공")
                .body(guardianMyPageService.getGuardianInfo());
    }

    @Override
    @GetMapping("/profile")
    public ApiResponseTemplate<GuardianProfileDetailResponse> getProfileDetail() {
        return ApiResponseTemplate.ok()
                .code("MYPAGE_2011")
                .message("보호자 프로필 조회 성공")
                .body(guardianMyPageService.getProfileDetail());
    }

    @Override
    @PatchMapping("/profile/name")
    public ApiResponseTemplate<Void> updateName(@RequestBody @Valid UpdateNameRequest request) {
        guardianMyPageService.updateName(request);
        return ApiResponseTemplate.ok()
                .code("MYPAGE_2012")
                .message("이름 수정 성공")
                .build();
    }

    @Override
    @PatchMapping(value = "/profile/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponseTemplate<Void> updateProfileImage(@ModelAttribute @Valid ProfileImageUploadRequest request) {
        guardianMyPageService.updateProfileImage(request.image());
        return ApiResponseTemplate.ok()
                .code("MYPAGE_2013")
                .message("프로필 이미지 수정 성공")
                .build();
    }

    @Override
    @GetMapping("/seniors")
    public ApiResponseTemplate<ConnectedSeniorResponse> getConnectedSeniors() {
        return ApiResponseTemplate.ok()
                .code("MYPAGE_2014")
                .message("연결된 시니어 목록 조회 성공")
                .body(guardianMyPageService.getConnectedSeniors());
    }

    @Override
    @GetMapping("/seniors/{seniorId}")
    public ApiResponseTemplate<SeniorProfileForGuardianResponse> getSeniorProfile(@PathVariable Long seniorId) {
        return ApiResponseTemplate.ok()
                .code("MYPAGE_2015")
                .message("시니어 프로필 조회 성공")
                .body(guardianMyPageService.getSeniorProfile(seniorId));
    }

    @Override
    @GetMapping("/seniors/{seniorId}/family-code")
    public ApiResponseTemplate<FamilyCodeResponse> getFamilyCode(@PathVariable Long seniorId) {
        return ApiResponseTemplate.ok()
                .code("MYPAGE_2022")
                .message("가족코드 조회 성공")
                .body(guardianMyPageService.getFamilyCode(seniorId));
    }

    @Override
    @GetMapping("/seniors/{seniorId}/invite-code")
    public ApiResponseTemplate<InviteCodeResponse> getInviteCode(@PathVariable Long seniorId) {
        return ApiResponseTemplate.ok()
                .code("MYPAGE_2023")
                .message("초대코드 조회 성공")
                .body(guardianMyPageService.getInviteCode(seniorId));
    }

    @Override
    @PatchMapping("/seniors/{seniorId}/phone")
    public ApiResponseTemplate<Void> updateSeniorPhone(@PathVariable Long seniorId,
                                                        @RequestBody @Valid UpdatePhoneRequest request) {
        guardianMyPageService.updateSeniorPhone(seniorId, request);
        return ApiResponseTemplate.ok()
                .code("MYPAGE_2016")
                .message("시니어 전화번호 수정 성공")
                .build();
    }

    @Override
    @PatchMapping("/seniors/{seniorId}/address")
    public ApiResponseTemplate<Void> updateSeniorAddress(@PathVariable Long seniorId,
                                                          @RequestBody @Valid UpdateSeniorAddressRequest request) {
        guardianMyPageService.updateSeniorAddress(seniorId, request);
        return ApiResponseTemplate.ok()
                .code("MYPAGE_2017")
                .message("시니어 주소 수정 성공")
                .build();
    }

    @Override
    @PatchMapping(value = "/seniors/{seniorId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponseTemplate<Void> updateSeniorProfileImage(@PathVariable Long seniorId,
                                                               @ModelAttribute @Valid ProfileImageUploadRequest request) {
        guardianMyPageService.updateSeniorProfileImage(seniorId, request.image());
        return ApiResponseTemplate.ok()
                .code("MYPAGE_2018")
                .message("시니어 프로필 이미지 수정 성공")
                .build();
    }

    @Override
    @GetMapping("/family/{seniorId}/members")
    public ApiResponseTemplate<FamilyMemberListResponse> getFamilyMembers(@PathVariable Long seniorId) {
        return ApiResponseTemplate.ok()
                .code("MYPAGE_2019")
                .message("가족 멤버 목록 조회 성공")
                .body(guardianMyPageService.getFamilyMembers(seniorId));
    }

    @Override
    @PatchMapping("/family/{seniorId}/members/{guardianId}/leader")
    public ApiResponseTemplate<Void> changeLeader(@PathVariable Long seniorId, @PathVariable Long guardianId) {
        guardianMyPageService.changeLeader(seniorId, guardianId);
        return ApiResponseTemplate.ok()
                .code("MYPAGE_2020")
                .message("방장 변경 성공")
                .build();
    }

    @Override
    @DeleteMapping("/family/{seniorId}/members/{guardianId}")
    public ApiResponseTemplate<Void> deleteFamilyMember(@PathVariable Long seniorId, @PathVariable Long guardianId) {
        guardianMyPageService.deleteFamilyMember(seniorId, guardianId);
        return ApiResponseTemplate.ok()
                .code("MYPAGE_2021")
                .message("가족 멤버 삭제 성공")
                .build();
    }
}
