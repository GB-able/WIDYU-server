package com.widyu.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record TokenPairResponse(
        @Schema(description = "멤버 ID", example = "1") Long memberId,
        @Schema(description = "액세스 토큰", defaultValue = "accessToken") String accessToken,
        @Schema(description = "리프레시 토큰", defaultValue = "refreshToken") String refreshToken) {

    public static TokenPairResponse of(final Long memberId, final String accessToken, final String refreshToken) {
        return new TokenPairResponse(memberId, accessToken, refreshToken);
    }
}