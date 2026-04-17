package com.widyu.member.dto.response;

import com.widyu.member.FamilyConnection;
import com.widyu.member.Member;
import com.widyu.member.SeniorProfile;

import java.util.List;

public record SeniorProfileResponse(
        Long memberId,
        String name,
        String profileImage,
        String address,
        String detailAddress,
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
        public static GuardianInfo from(FamilyConnection connection) {
            Member guardian = connection.getGuardian();
            return new GuardianInfo(
                    guardian.getId(),
                    guardian.getName(),
                    guardian.getProfileImage(),
                    connection.getNickname()
            );
        }
    }

    public static SeniorProfileResponse from(SeniorProfile seniorProfile) {
        List<GuardianInfo> guardianInfos = seniorProfile.getFamilyConnections().stream()
                .map(GuardianInfo::from)
                .toList();

        return new SeniorProfileResponse(
                seniorProfile.getMember().getId(),
                seniorProfile.getMember().getName(),
                seniorProfile.getMember().getProfileImage(),
                seniorProfile.getAddress(),
                seniorProfile.getDetailAddress(),
                seniorProfile.getInviteCode(),
                seniorProfile.getPoints(),
                guardianInfos
        );
    }
}
