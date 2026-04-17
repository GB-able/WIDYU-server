package com.widyu.mypage.dto.response;

import com.widyu.member.Member;
import com.widyu.member.SeniorProfile;

public record SeniorProfileDetailResponse(
        String profileImage,
        String name,
        String phoneNumber,
        String address,
        String detailAddress,
        String inviteCode
) {
    public static SeniorProfileDetailResponse of(Member member, SeniorProfile seniorProfile) {
        return new SeniorProfileDetailResponse(
                member.getProfileImage(),
                member.getName(),
                member.getPhoneNumber(),
                seniorProfile.getAddress(),
                seniorProfile.getDetailAddress(),
                seniorProfile.getInviteCode()
        );
    }
}
