package com.widyu.domain.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SocialLoginResponse(
        boolean isFirst,
        String accessToken,
        String refreshToken,
        UserProfile profile,
        String socialTemporaryToken
) {
    public static SocialLoginResponse of(boolean isFirst, String accessToken, String refreshToken,
                                         UserProfile profile) {
        return new SocialLoginResponse(isFirst, accessToken, refreshToken, profile, null);
    }

    public static SocialLoginResponse ofWithSocialToken(boolean isFirst, String accessToken, String refreshToken,
                                                        UserProfile profile, String socialTemporaryToken) {
        return new SocialLoginResponse(isFirst, accessToken, refreshToken, profile, socialTemporaryToken);
    }
}
