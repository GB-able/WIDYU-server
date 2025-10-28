package com.widyu.member.dto.response;

import com.widyu.member.ConnectionRole;
import com.widyu.member.ConnectionStatus;
import com.widyu.member.FamilyConnection;
import com.widyu.member.Member;
import com.widyu.member.SeniorProfile;

import java.util.List;

public record SeniorProfileResponse(
        Long seniorId,
        String name,
        String profileImage,
        String birthDate,
        String address,
        String detailAddress,
        String inviteCode,
        Long points,
        List<GuardianInfo> guardians
) {

    public record GuardianInfo(
            Long guardianId,
            String name,
            String profileImage,
            String role,
            String status,
            String nickname
    ) {
        public static GuardianInfo from(FamilyConnection connection) {
            Member guardian = connection.getGuardian();
            return new GuardianInfo(
                    guardian.getId(),
                    guardian.getName(),
                    guardian.getProfileImage(),
                    connection.getRole().getDescription(),
                    connection.getStatus().getDescription(),
                    connection.getNickname()
            );
        }
    }

    public static SeniorProfileResponse from(SeniorProfile seniorProfile) {
        List<GuardianInfo> guardianInfos = seniorProfile.getFamilyConnections().stream()
                .filter(connection -> connection.getStatus() == ConnectionStatus.ACTIVE)
                .map(GuardianInfo::from)
                .toList();

        return new SeniorProfileResponse(
                seniorProfile.getMember().getId(),
                seniorProfile.getMember().getName(),
                seniorProfile.getMember().getProfileImage(),
                seniorProfile.getBirthDate(),
                seniorProfile.getAddress(),
                seniorProfile.getDetailAddress(),
                seniorProfile.getInviteCode(),
                seniorProfile.getPoints(),
                guardianInfos
        );
    }
}
