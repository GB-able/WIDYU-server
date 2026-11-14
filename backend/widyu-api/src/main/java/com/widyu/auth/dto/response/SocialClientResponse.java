package com.widyu.auth.dto.response;

import lombok.Builder;

@Builder
public record SocialClientResponse(
        String oauthId,
        String email,
        String name,
        String phoneNumber,
        String refreshToken,
        // 공급자가 이메일 소유를 확인했는지. 확인되지 않은 이메일로는 기존 회원을 찾지 않는다 (LLD-0149)
        boolean emailVerified
) {
    public static SocialClientResponse of(
            final String oauthId,
            final String email,
            final String name,
            final String phoneNumber
    ) {
        return of(oauthId, email, name, phoneNumber, null);
    }

    public static SocialClientResponse of(
            final String oauthId,
            final String email,
            final String name,
            final String phoneNumber,
            final String refreshToken
    ) {
        return of(oauthId, email, name, phoneNumber, refreshToken, false);
    }

    public static SocialClientResponse of(
            final String oauthId,
            final String email,
            final String name,
            final String phoneNumber,
            final String refreshToken,
            final boolean emailVerified
    ) {
        return SocialClientResponse.builder()
                .oauthId(oauthId)
                .email(email)
                .name(name)
                .phoneNumber(phoneNumber)
                .refreshToken(refreshToken)
                .emailVerified(emailVerified)
                .build();
    }
}
