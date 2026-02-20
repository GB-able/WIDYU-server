package com.widyu.heart.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record HeartMessageRequest(

        @NotNull(message = "받는 사람 ID는 필수입니다.")
        Long receiverId,

        @NotBlank(message = "메시지 내용은 필수입니다.")
        @Size(max = 50, message = "메시지는 50자 이내로 입력해주세요.")
        String message
) {
}
