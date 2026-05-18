package com.widyu.location.realtime.dto;

import com.widyu.member.Member;
import com.widyu.member.SeniorProfile;
import lombok.Builder;

@Builder
public record TrackedSeniorResponse(
        Long memberId,
        String name,
        String profileImage
) {

    public static TrackedSeniorResponse from(SeniorProfile seniorProfile) {
        Member seniorMember = seniorProfile.getMember();
        return TrackedSeniorResponse.builder()
                .memberId(seniorMember.getId())
                .name(seniorMember.getName())
                .profileImage(seniorMember.getProfileImage())
                .build();
    }
}
