package com.widyu.mypage.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdatePhoneRequest(
        @NotBlank(message = "전화번호는 필수입니다.")
        @Pattern(
                regexp = "^01[016789][0-9]{7,8}$",
                message = "전화번호는 하이픈 없이 10~11자리 숫자여야 합니다. (예: 01012345678)"
        )
        String phoneNumber
) {}
