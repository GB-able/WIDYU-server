package com.widyu.goal.home.dto.response;

import com.widyu.member.FamilyConnection;

public record FamilyMemberResponse(
        Long memberId,
        String name,
        String profileImage
) {
    public static FamilyMemberResponse from(FamilyConnection familyConnection) {
        return new FamilyMemberResponse(
                familyConnection.getSenior().getMember().getId(),
                familyConnection.getSenior().getMember().getName(),
                familyConnection.getSenior().getMember().getProfileImage()
        );
    }
}
