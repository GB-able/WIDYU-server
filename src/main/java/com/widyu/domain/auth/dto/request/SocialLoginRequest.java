package com.widyu.domain.auth.dto.request;

import com.widyu.global.constant.Platform;
import lombok.Builder;

@Builder
public record SocialLoginRequest(
        String accessToken,
        String authorizationCode,
        String refreshToken,
        AppleProfile profile,
        String platform
) {
    public record AppleProfile(
            String email,
            String name
    ) {}
    
    public static SocialLoginRequest of(String accessToken) {
        return SocialLoginRequest.builder()
                .accessToken(accessToken)
                .build();
    }
}
