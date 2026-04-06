package com.widyu.fcm.dto.request;

import jakarta.validation.constraints.NotNull;

public record UpdateNotificationSettingRequest(
        @NotNull(message = "카테고리는 필수입니다.")
        String category,

        @NotNull(message = "활성화 여부는 필수입니다.")
        Boolean enabled
) {
}