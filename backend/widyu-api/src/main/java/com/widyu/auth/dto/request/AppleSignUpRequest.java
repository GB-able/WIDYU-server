package com.widyu.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

// 번호를 채울 회원은 Authorization의 액세스 토큰으로 정한다. 이 임시 토큰은 SMS로 인증한 번호만 증명한다 (LLD-0145)
public record AppleSignUpRequest(
        @NotBlank(message = "임시 토큰은 필수입니다.")
        String temporaryToken
) {
}
