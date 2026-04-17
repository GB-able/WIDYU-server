package com.widyu.member.dto.response;

import com.widyu.member.SeniorProfile;

public record FamilyJoinResponse(
        Long memberId,
        String seniorName,
        String seniorProfileImage
) {
    public static FamilyJoinResponse from(SeniorProfile seniorProfile) {
        return new FamilyJoinResponse(
                seniorProfile.getMember().getId(),
                seniorProfile.getMember().getName(),
                seniorProfile.getMember().getProfileImage()
        );
    }
}
