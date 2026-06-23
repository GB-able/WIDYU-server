package com.widyu.admin.dto.response;

public record AdminLoginResponse(
        Long memberId,
        String accessToken
) {
    public static AdminLoginResponse of(Long memberId, String accessToken) {
        return new AdminLoginResponse(memberId, accessToken);
    }
}
