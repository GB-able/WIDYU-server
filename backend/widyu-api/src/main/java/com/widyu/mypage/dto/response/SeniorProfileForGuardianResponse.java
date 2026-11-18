package com.widyu.mypage.dto.response;

import com.widyu.member.Member;
import com.widyu.member.SeniorProfile;
import com.widyu.parentlocation.ParentLocation;
import java.time.LocalDate;
import java.util.Optional;

public record SeniorProfileForGuardianResponse(
        Long memberId,
        String profileImage,
        String name,
        LocalDate birthDate,
        String phoneNumber,
        String address,
        String inviteCode,
        Double latitude,
        Double longitude
) {
    // 좌표는 집(HOME) 안심구역에서 가져온다. 집 위치가 없으면 null이다.
    public static SeniorProfileForGuardianResponse of(Member seniorMember, SeniorProfile seniorProfile,
                                                      Optional<ParentLocation> homeLocation) {
        return new SeniorProfileForGuardianResponse(
                seniorMember.getId(),
                seniorMember.getProfileImage(),
                seniorMember.getName(),
                seniorProfile.getBirthDate(),
                seniorMember.getPhoneNumber(),
                seniorProfile.getAddress(),
                seniorProfile.getInviteCode(),
                homeLocation.map(ParentLocation::getLatitude).orElse(null),
                homeLocation.map(ParentLocation::getLongitude).orElse(null)
        );
    }
}
