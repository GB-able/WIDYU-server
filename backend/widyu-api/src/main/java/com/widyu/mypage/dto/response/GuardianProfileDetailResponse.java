package com.widyu.mypage.dto.response;

import com.widyu.member.Member;
import java.util.List;

public record GuardianProfileDetailResponse(
        String profileImage,
        String name,
        String birthDate,
        String phoneNumber,
        String email,
        List<String> socialProviders
) {
    public static GuardianProfileDetailResponse from(Member member) {
        String email = member.getLocalAccount() != null
                ? member.getLocalAccount().getEmail()
                : member.getSocialAccounts().stream()
                        .map(sa -> sa.getEmail())
                        .filter(e -> e != null && !e.isBlank())
                        .findFirst()
                        .orElse(null);

        List<String> providers = member.getSocialAccounts().stream()
                .map(sa -> sa.getProvider())
                .toList();

        return new GuardianProfileDetailResponse(
                member.getProfileImage(),
                member.getName(),
                member.getBirthDate(),
                member.getPhoneNumber(),
                email,
                providers
        );
    }
}
