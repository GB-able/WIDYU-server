package com.widyu.mypage.application;

import com.widyu.global.util.MemberUtil;
import com.widyu.member.application.FamilyAccessService;
import com.widyu.mypage.dto.response.ConnectedSeniorResponse;
import com.widyu.mypage.dto.response.FamilyCodeResponse;
import com.widyu.mypage.dto.response.FamilyMemberListResponse;
import com.widyu.mypage.dto.response.GuardianInfoResponse;
import com.widyu.mypage.dto.response.GuardianProfileDetailResponse;
import com.widyu.mypage.dto.response.SeniorProfileForGuardianResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GuardianMyPageQueryService {

    private final GuardianMyPageService guardianMyPageService;
    private final MemberUtil memberUtil;
    private final FamilyAccessService familyAccessService;

    public GuardianInfoResponse getGuardianInfo() {
        return guardianMyPageService.getGuardianInfo();
    }

    public GuardianProfileDetailResponse getProfileDetail() {
        return guardianMyPageService.getProfileDetail();
    }

    public ConnectedSeniorResponse getConnectedSeniors() {
        return guardianMyPageService.getConnectedSeniors();
    }

    public SeniorProfileForGuardianResponse getSeniorProfile(Long memberId) {
        familyAccessService.verifyFamilyAccess(memberUtil.getCurrentMember().getId(), memberId);
        return guardianMyPageService.getSeniorProfile(memberId);
    }

    public FamilyCodeResponse getFamilyCode() {
        return guardianMyPageService.getFamilyCode();
    }

    public FamilyMemberListResponse getFamilyMembers() {
        return guardianMyPageService.getFamilyMembers();
    }
}
