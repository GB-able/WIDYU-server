package com.widyu.member.dto.response;

import com.widyu.member.Family;
import com.widyu.member.SeniorProfile;
import java.util.List;

public record FamilyJoinResponse(
        String familyCode,
        List<SeniorInfo> seniors
) {
    public record SeniorInfo(
            Long memberId,
            String name,
            String profileImage
    ) {}

    public static FamilyJoinResponse from(Family family, List<SeniorProfile> seniors) {
        List<SeniorInfo> seniorInfos = seniors.stream()
                .map(sp -> new SeniorInfo(
                        sp.getMember().getId(),
                        sp.getMember().getName(),
                        sp.getMember().getProfileImage()
                ))
                .toList();
        return new FamilyJoinResponse(family.getFamilyCode(), seniorInfos);
    }
}
