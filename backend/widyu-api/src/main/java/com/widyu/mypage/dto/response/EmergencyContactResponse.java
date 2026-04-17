package com.widyu.mypage.dto.response;

import com.widyu.member.FamilyConnection;
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
        public static FamilyMemberContact from(FamilyConnection connection) {
            return new FamilyMemberContact(
                    connection.getGuardian().getId(),
                    connection.getGuardian().getName(),
                    connection.getGuardian().getPhoneNumber(),
                    connection.isRepresentative()
            );
        }
    }

    public static EmergencyContactResponse of(List<FamilyConnection> connections) {
        List<FamilyMemberContact> members = connections.stream()
                .map(FamilyMemberContact::from)
                .toList();

        RepresentativeContact representative = connections.stream()
                .filter(FamilyConnection::isRepresentative)
                .findFirst()
                .map(c -> new RepresentativeContact(c.getGuardian().getName(), c.getGuardian().getPhoneNumber()))
                .orElse(null);

        return new EmergencyContactResponse(representative, members);
    }
}
