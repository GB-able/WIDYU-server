package com.widyu.fcm.api.dto;

import com.widyu.fcm.FcmCategory;
import lombok.Builder;

@Builder
public record FcmSendDto(
        String title,
        String content,
        FcmCategory fcmCategory,
        String scheme
) {
}
