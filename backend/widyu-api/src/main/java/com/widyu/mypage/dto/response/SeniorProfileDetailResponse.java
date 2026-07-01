package com.widyu.mypage.dto.response;

import com.widyu.member.Member;
import com.widyu.member.SeniorProfile;
import java.time.LocalDate;

public record SeniorProfileDetailResponse(
        String profileImage,
        String name,
        LocalDate birthDate,
        String phoneNumber,
        String address,
        String inviteCode
) {
    public static SeniorProfileDetailResponse of(Member member, SeniorProfile seniorProfile) {
        return new SeniorProfileDetailResponse(
                member.getProfileImage(),
                member.getName(),
                seniorProfile.getBirthDate(),
                member.getPhoneNumber(),
                seniorProfile.getAddress(),
                seniorProfile.getInviteCode()
        );
    }
}
