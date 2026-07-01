package com.widyu.member.dto.response;

import com.widyu.member.FamilyMembership;
import com.widyu.member.Member;
import com.widyu.member.SeniorProfile;

import java.util.List;

public record SeniorProfileResponse(
        Long memberId,
        String name,
        String profileImage,
        String address,
        String inviteCode,
        Long points,
        List<GuardianInfo> guardians
) {

    public record GuardianInfo(
            Long memberId,
            String name,
            String profileImage,
            String nickname
    ) {
        public static GuardianInfo from(FamilyMembership membership) {
            Member guardian = membership.getGuardian();
            return new GuardianInfo(
                    guardian.getId(),
                    guardian.getName(),
                    guardian.getProfileImage(),
                    membership.getNickname()
            );
        }
    }

    public static SeniorProfileResponse from(SeniorProfile seniorProfile, List<FamilyMembership> memberships) {
        List<GuardianInfo> guardianInfos = memberships.stream()
                .map(GuardianInfo::from)
                .toList();

        return new SeniorProfileResponse(
                seniorProfile.getMember().getId(),
                seniorProfile.getMember().getName(),
                seniorProfile.getMember().getProfileImage(),
                seniorProfile.getAddress(),
                seniorProfile.getInviteCode(),
                seniorProfile.getPoints(),
                guardianInfos
        );
    }
}
