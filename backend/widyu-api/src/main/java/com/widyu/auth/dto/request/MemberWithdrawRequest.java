package com.widyu.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record MemberWithdrawRequest(
        @NotBlank(message = "탈퇴 사유는 필수입니다")
        String reason
) {
    public static MemberWithdrawRequest of(String reason) {
        return new MemberWithdrawRequest(reason);
    }
}