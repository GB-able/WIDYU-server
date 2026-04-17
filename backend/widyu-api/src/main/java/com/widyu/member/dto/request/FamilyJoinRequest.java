package com.widyu.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record FamilyJoinRequest(

        @NotBlank(message = "가족코드는 필수입니다.")
        @Pattern(regexp = "^[A-Z0-9]{6}$", message = "가족코드는 영문 대문자와 숫자로 이루어진 6자리여야 합니다.")
        String familyCode
) {
}
