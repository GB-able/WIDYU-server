package com.widyu.admin.dto.response;

import com.widyu.member.Member;
import com.widyu.member.MemberType;

public record AdminMemberResponse(
        Long id,
        String name,
        String phoneNumber,
        MemberType type
) {
    public static AdminMemberResponse from(Member member) {
        return new AdminMemberResponse(
                member.getId(),
                member.getName(),
                member.getPhoneNumber(),
                member.getType()
        );
    }
}
