package com.widyu.domain.auth.dto;

public record SocialTemporaryTokenDto(
        Long memberId,
        String provider,
        String oauthId,
        String email
) {
}