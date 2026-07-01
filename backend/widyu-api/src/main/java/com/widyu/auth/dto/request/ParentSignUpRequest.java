package com.widyu.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ParentSignUpRequest(
        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 50, message = "이름은 최대 50자입니다.")
        String name,

        @NotBlank(message = "전화번호는 필수입니다.")
        @Pattern(
                regexp = "^01[016789][0-9]{7,8}$",
                message = "전화번호는 하이픈 없이 10~11자리 숫자여야 합니다. (예: 01012345678)"
        )
        String phoneNumber,

        @NotBlank(message = "주소는 필수입니다.")
        @Size(max = 200, message = "주소는 최대 200자입니다.")
        String address,

        @NotBlank(message = "초대코드는 필수입니다.")
        @Pattern(regexp = "^\\d{7}$", message = "초대코드는 숫자만 7자리로 입력해주세요.")
        String inviteCode
) { }
