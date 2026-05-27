package com.widyu.mypage.dto.response;

import com.widyu.member.Member;
import com.widyu.member.SeniorProfile;
import java.time.LocalDate;

public record SeniorProfileForGuardianResponse(
        Long memberId,
        String profileImage,
        String name,
        LocalDate birthDate,
        String phoneNumber,
        String address,
        String detailAddress,
        String inviteCode
) {
    public static SeniorProfileForGuardianResponse of(Member seniorMember, SeniorProfile seniorProfile) {
        return new SeniorProfileForGuardianResponse(
                seniorMember.getId(),
                seniorMember.getProfileImage(),
                seniorMember.getName(),
                seniorProfile.getBirthDate(),
                seniorMember.getPhoneNumber(),
                seniorProfile.getAddress(),
                seniorProfile.getDetailAddress(),
                seniorProfile.getInviteCode()
        );
    }
}
