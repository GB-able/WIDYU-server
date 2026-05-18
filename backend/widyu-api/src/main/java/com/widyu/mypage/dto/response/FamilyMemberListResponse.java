package com.widyu.mypage.dto.response;

import com.widyu.member.FamilyConnection;
import com.widyu.member.Member;
import com.widyu.member.SeniorProfile;
import java.util.ArrayList;
import java.util.List;

public record FamilyMemberListResponse(
        boolean isCurrentUserLeader,
        List<FamilyMemberItem> members
) {
    public record FamilyMemberItem(
            Long memberId,
            String name,
            String profileImage,
            boolean isLeader,
            boolean isCurrent,
            boolean isSenior
    ) {}

    public static FamilyMemberListResponse of(
            List<FamilyConnection> connections,
            SeniorProfile seniorProfile,
            Long currentGuardianId
    ) {
        boolean isCurrentUserLeader = connections.stream()
                .anyMatch(c -> c.getGuardian().getId().equals(currentGuardianId) && c.isLeader());

        Member seniorMember = seniorProfile.getMember();
        FamilyMemberItem seniorItem = new FamilyMemberItem(
                seniorMember.getId(),
                seniorMember.getName(),
                seniorMember.getProfileImage(),
                false,
                false,
                true
        );

        List<FamilyMemberItem> guardianItems = connections.stream()
                .map(c -> new FamilyMemberItem(
                        c.getGuardian().getId(),
                        c.getGuardian().getName(),
                        c.getGuardian().getProfileImage(),
                        c.isLeader(),
                        c.getGuardian().getId().equals(currentGuardianId),
                        false
                ))
                .toList();

        List<FamilyMemberItem> allMembers = new ArrayList<>();
        allMembers.add(seniorItem);
        allMembers.addAll(guardianItems);

        return new FamilyMemberListResponse(isCurrentUserLeader, allMembers);
    }
}
