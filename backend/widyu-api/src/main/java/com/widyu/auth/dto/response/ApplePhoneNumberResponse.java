package com.widyu.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

// merged=false면 토큰 필드를 싣지 않는다
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApplePhoneNumberResponse(
        @Schema(description = "인증한 번호의 기존 회원에 애플 계정을 합쳤는지. true면 아래 토큰으로 기존 회원에 로그인한다", example = "false")
        boolean merged,
        @Schema(description = "합친 기존 회원 ID. merged=false면 없음", example = "9") Long memberId,
        @Schema(description = "기존 회원의 액세스 토큰. merged=false면 없음") String accessToken,
        @Schema(description = "기존 회원의 리프레시 토큰. merged=false면 없음") String refreshToken) {

    public static ApplePhoneNumberResponse filled() {
        return new ApplePhoneNumberResponse(false, null, null, null);
    }

    public static ApplePhoneNumberResponse merged(final TokenPairResponse tokenPair) {
        return new ApplePhoneNumberResponse(true, tokenPair.memberId(), tokenPair.accessToken(), tokenPair.refreshToken());
    }
}
