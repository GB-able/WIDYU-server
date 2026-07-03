package com.widyu.location.realtime.dto;

import com.widyu.member.Member;
import com.widyu.member.SeniorProfile;
import lombok.Builder;

@Builder
public record TrackedSeniorResponse(
        Long memberId,
        String name,
        String profileImage,
        Double latitude,
        Double longitude
) {

    public static TrackedSeniorResponse of(SeniorProfile seniorProfile, Double latitude, Double longitude) {
        Member seniorMember = seniorProfile.getMember();
        return TrackedSeniorResponse.builder()
                .memberId(seniorMember.getId())
                .name(seniorMember.getName())
                .profileImage(seniorMember.getProfileImage())
                .latitude(latitude)
                .longitude(longitude)
                .build();
    }
}
