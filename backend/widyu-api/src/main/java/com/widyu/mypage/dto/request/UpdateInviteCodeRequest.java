package com.widyu.mypage.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateInviteCodeRequest(
        @NotBlank(message = "초대코드는 필수입니다.")
        @Size(min = 7, max = 7, message = "초대코드는 7자리여야 합니다.")
        @Pattern(regexp = "^[A-Za-z0-9]{7}$", message = "초대코드는 영문자와 숫자로만 구성되어야 합니다.")
        String inviteCode
) {}
