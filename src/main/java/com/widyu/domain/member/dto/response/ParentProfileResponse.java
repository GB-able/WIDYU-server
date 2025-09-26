package com.widyu.domain.member.dto.response;

import com.widyu.domain.member.entity.ParentProfile;

public record ParentProfileResponse(
        Long parentId,
        String name,
        String profileImage,
        String birthDate,
        String address,
        String detailAddress,
        String inviteCode,
        Long points,
        GuardianInfo guardian
) {

    public record GuardianInfo(
            Long guardianId,
            String name,
            String profileImage
    ) {
        public static GuardianInfo from(com.widyu.domain.member.entity.Member guardian) {
            return new GuardianInfo(
                    guardian.getId(),
                    guardian.getName(),
                    guardian.getProfileImage()
            );
        }
    }

    public static ParentProfileResponse from(ParentProfile parentProfile) {
        return new ParentProfileResponse(
                parentProfile.getMember().getId(),
                parentProfile.getMember().getName(),
                parentProfile.getMember().getProfileImage(),
                parentProfile.getBirthDate(),
                parentProfile.getAddress(),
                parentProfile.getDetailAddress(),
                parentProfile.getInviteCode(),
                parentProfile.getPoints(),
                GuardianInfo.from(parentProfile.getGuardian())
        );
    }
}