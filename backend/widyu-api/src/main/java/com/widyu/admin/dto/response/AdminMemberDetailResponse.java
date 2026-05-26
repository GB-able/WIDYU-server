package com.widyu.admin.dto.response;

import com.widyu.global.entity.Status;
import com.widyu.member.Member;
import com.widyu.member.MemberRole;
import com.widyu.member.MemberType;
import java.time.LocalDateTime;

public record AdminMemberDetailResponse(
        Long id,
        String name,
        String phoneNumber,
        MemberType type,
        MemberRole role,
        Status status,
        LocalDateTime createdAt
) {
    public static AdminMemberDetailResponse from(Member member) {
        return new AdminMemberDetailResponse(
                member.getId(),
                member.getName(),
                member.getPhoneNumber(),
                member.getType(),
                member.getRole(),
                member.getStatus(),
                member.getCreatedAt()
        );
    }
}
