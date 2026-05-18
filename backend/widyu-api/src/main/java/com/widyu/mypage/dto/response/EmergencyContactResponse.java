package com.widyu.mypage.dto.response;

import com.widyu.member.FamilyMembership;
import java.util.List;

public record EmergencyContactResponse(
        RepresentativeContact representative,
        List<FamilyMemberContact> familyMembers
) {
    public record RepresentativeContact(
            String name,
            String phoneNumber
    ) {}

    public record FamilyMemberContact(
            Long memberId,
            String name,
            String phoneNumber,
            boolean isRepresentative
    ) {
        public static FamilyMemberContact from(FamilyMembership membership) {
            return new FamilyMemberContact(
                    membership.getGuardian().getId(),
                    membership.getGuardian().getName(),
                    membership.getGuardian().getPhoneNumber(),
                    membership.isRepresentative()
            );
        }
    }

    public static EmergencyContactResponse of(List<FamilyMembership> memberships) {
        List<FamilyMemberContact> members = memberships.stream()
                .map(FamilyMemberContact::from)
                .toList();

        RepresentativeContact representative = memberships.stream()
                .filter(FamilyMembership::isRepresentative)
                .findFirst()
                .map(m -> new RepresentativeContact(m.getGuardian().getName(), m.getGuardian().getPhoneNumber()))
                .orElse(null);

        return new EmergencyContactResponse(representative, members);
    }
}
