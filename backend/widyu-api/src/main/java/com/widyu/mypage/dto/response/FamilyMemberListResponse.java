package com.widyu.mypage.dto.response;

import com.widyu.member.FamilyConnection;
import java.util.List;

public record FamilyMemberListResponse(
        boolean isCurrentUserLeader,
        List<FamilyMemberItem> members
) {
    public record FamilyMemberItem(
            Long memberId,
            String name,
            boolean isLeader
    ) {
        public static FamilyMemberItem from(FamilyConnection connection) {
            return new FamilyMemberItem(
                    connection.getGuardian().getId(),
                    connection.getGuardian().getName(),
                    connection.isLeader()
            );
        }
    }

    public static FamilyMemberListResponse of(List<FamilyConnection> connections, Long currentGuardianId) {
        boolean isLeader = connections.stream()
                .anyMatch(c -> c.getGuardian().getId().equals(currentGuardianId) && c.isLeader());

        return new FamilyMemberListResponse(
                isLeader,
                connections.stream().map(FamilyMemberItem::from).toList()
        );
    }
}
