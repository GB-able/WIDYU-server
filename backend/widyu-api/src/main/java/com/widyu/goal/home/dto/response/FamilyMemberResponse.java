package com.widyu.goal.home.dto.response;

import com.widyu.member.SeniorProfile;

public record FamilyMemberResponse(
        Long memberId,
        String name,
        String profileImage
) {
    public static FamilyMemberResponse from(SeniorProfile seniorProfile) {
        return new FamilyMemberResponse(
                seniorProfile.getMember().getId(),
                seniorProfile.getMember().getName(),
                seniorProfile.getMember().getProfileImage()
        );
    }
}
