package com.widyu.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SocialLoginResponse(
        @Schema(description = "최초 가입 여부") boolean isFirst,
        @Schema(description = "멤버 ID") Long memberId,
        @Schema(description = "액세스 토큰") String accessToken,
        @Schema(description = "리프레시 토큰") String refreshToken,
        @Schema(description = "사용자 프로필") UserProfile profile,
        @Schema(description = "소셜 임시 토큰 (계정 연동 시 사용)") String socialTemporaryToken
) {
    public static SocialLoginResponse of(boolean isFirst, Long memberId, String accessToken, String refreshToken,
                                         UserProfile profile) {
        return new SocialLoginResponse(isFirst, memberId, accessToken, refreshToken, profile, null);
    }

    public static SocialLoginResponse ofWithSocialToken(boolean isFirst, String accessToken, String refreshToken,
                                                        UserProfile profile, String socialTemporaryToken) {
        return new SocialLoginResponse(isFirst, null, accessToken, refreshToken, profile, socialTemporaryToken);
    }
}
