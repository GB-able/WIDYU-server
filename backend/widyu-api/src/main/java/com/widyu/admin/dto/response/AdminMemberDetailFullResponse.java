package com.widyu.admin.dto.response;

import com.widyu.global.entity.Status;
import com.widyu.member.MemberRole;
import com.widyu.member.MemberType;
import com.widyu.pay.PaymentStatus;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;

public record AdminMemberDetailFullResponse(
        Long id,
        String name,
        String phoneNumber,
        MemberType type,
        MemberRole role,
        Status status,
        LocalDateTime createdAt,

        FamilyInfo familyInfo,
        long activeFcmTokens,
        List<RecentAlbum> recentAlbums,
        List<RecentPayment> recentPayments,
        long heartEmergencyCount
) {
    public record FamilyInfo(
            String familyCode,
            // 시니어 전용
            String inviteCode,
            String address,
            Long points,
            // 보호자 전용
            Boolean isLeader,
            Boolean isRepresentative,
            String nickname,
            LocalDateTime connectedAt
    ) {}

    public record RecentAlbum(
            Long id,
            String thumbnail,
            Status status,
            LocalDateTime createdAt
    ) {}

    public record RecentPayment(
            Long id,
            String orderName,
            int amount,
            PaymentStatus status,
            ZonedDateTime approvedAt
    ) {}
}
