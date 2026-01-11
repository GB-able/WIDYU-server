package com.widyu.location.realtime.dto;

import com.widyu.member.FamilyConnection;
import com.widyu.member.Member;
import lombok.Builder;

/**
 * 보호자가 추적 가능한 시니어 정보 응답 DTO
 */
@Builder
public record TrackedSeniorResponse(
        Long seniorId,
        String name,
        String profileImage
) {

    public static TrackedSeniorResponse from(FamilyConnection connection) {
        Member seniorMember = connection.getSenior().getMember();

        return TrackedSeniorResponse.builder()
                .seniorId(connection.getSenior().getId())
                .name(seniorMember.getName())
                .profileImage(seniorMember.getProfileImage())
                .build();
    }
}