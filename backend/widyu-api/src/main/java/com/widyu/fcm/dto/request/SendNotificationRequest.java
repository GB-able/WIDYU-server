package com.widyu.fcm.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SendNotificationRequest(
        @NotNull(message = "받는 사람 ID는 필수입니다.")
        Long receiverId,

        @NotBlank(message = "알림 내용은 필수입니다.")
        String content
) {
}
