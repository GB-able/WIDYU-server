package com.widyu.mypage.dto.response;

import com.widyu.member.Member;

public record GuardianInfoResponse(
        String profileImage,
        String name
) {
    public static GuardianInfoResponse from(Member member) {
        return new GuardianInfoResponse(member.getProfileImage(), member.getName());
    }
}
