package com.widyu.mypage.controller;

import com.widyu.mypage.application.SeniorMyPageService;
import com.widyu.mypage.controller.docs.SeniorMyPageDocs;
import com.widyu.mypage.dto.request.ProfileImageUploadRequest;
import com.widyu.mypage.dto.request.UpdateNameRequest;
import com.widyu.mypage.dto.request.UpdatePhoneRequest;
import com.widyu.mypage.dto.response.EmergencyContactResponse;
import com.widyu.mypage.dto.response.FamilyCodeResponse;
import com.widyu.mypage.dto.response.PointHistoryResponse;
import com.widyu.mypage.dto.response.SeniorInfoResponse;
import com.widyu.mypage.dto.response.SeniorProfileDetailResponse;
import com.widyu.global.response.ApiResponseTemplate;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
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
@RequestMapping("/api/v1/mypage/senior")
public class SeniorMyPageController implements SeniorMyPageDocs {

    private final SeniorMyPageService seniorMyPageService;

    @Override
    @GetMapping
    public ApiResponseTemplate<SeniorInfoResponse> getSeniorInfo() {
        SeniorInfoResponse response = seniorMyPageService.getSeniorInfo();
        return ApiResponseTemplate.ok()
                .code("MYPAGE_2001")
                .message("시니어 내 정보 조회 성공")
                .body(response);
    }

    @Override
    @GetMapping("/family-code")
    public ApiResponseTemplate<FamilyCodeResponse> getFamilyCode() {
        FamilyCodeResponse response = seniorMyPageService.getFamilyCode();
        return ApiResponseTemplate.ok()
                .code("MYPAGE_2002")
                .message("가족코드 조회 성공")
                .body(response);
    }

    @Override
    @GetMapping("/profile")
    public ApiResponseTemplate<SeniorProfileDetailResponse> getProfileDetail() {
        SeniorProfileDetailResponse response = seniorMyPageService.getProfileDetail();
        return ApiResponseTemplate.ok()
                .code("MYPAGE_2003")
                .message("프로필 설정 조회 성공")
                .body(response);
    }

    @Override
    @PatchMapping("/profile/name")
    public ApiResponseTemplate<Void> updateName(@RequestBody @Valid UpdateNameRequest request) {
        seniorMyPageService.updateName(request);
        return ApiResponseTemplate.ok()
                .code("MYPAGE_2004")
                .message("이름 수정 성공")
                .build();
    }

    @Override
    @PatchMapping(value = "/profile/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponseTemplate<Void> updateProfileImage(@ModelAttribute @Valid ProfileImageUploadRequest request) {
        seniorMyPageService.updateProfileImage(request.image());
        return ApiResponseTemplate.ok()
                .code("MYPAGE_2005")
                .message("프로필 이미지 수정 성공")
                .build();
    }

    @Override
    @PatchMapping("/profile/phone")
    public ApiResponseTemplate<Void> updatePhoneNumber(@RequestBody @Valid UpdatePhoneRequest request) {
        seniorMyPageService.updatePhoneNumber(request);
        return ApiResponseTemplate.ok()
                .code("MYPAGE_2006")
                .message("전화번호 수정 성공")
                .build();
    }

    @Override
    @GetMapping("/points/history")
    public ApiResponseTemplate<PointHistoryResponse> getPointHistory() {
        PointHistoryResponse response = seniorMyPageService.getPointHistory();
        return ApiResponseTemplate.ok()
                .code("MYPAGE_2007")
                .message("포인트 내역 조회 성공")
                .body(response);
    }

    @Override
    @GetMapping("/emergency-contact")
    public ApiResponseTemplate<EmergencyContactResponse> getEmergencyContacts() {
        EmergencyContactResponse response = seniorMyPageService.getEmergencyContacts();
        return ApiResponseTemplate.ok()
                .code("MYPAGE_2008")
                .message("비상연락처 조회 성공")
                .body(response);
    }

    @Override
    @PatchMapping("/emergency-contact/{guardianId}")
    public ApiResponseTemplate<Void> updateRepresentativeContact(@PathVariable Long guardianId) {
        seniorMyPageService.updateRepresentativeContact(guardianId);
        return ApiResponseTemplate.ok()
                .code("MYPAGE_2009")
                .message("대표 비상연락처 변경 성공")
                .build();
    }
}
