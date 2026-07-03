package com.widyu.mypage.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateInviteCodeRequest(
        @NotBlank(message = "초대코드는 필수입니다.")
        @Size(min = 7, max = 7, message = "초대코드는 7자리여야 합니다.")
        @Pattern(regexp = "^\\d{7}$", message = "초대코드는 숫자만 7자리로 입력해주세요.")
        String inviteCode
) {}
