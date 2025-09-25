package com.widyu.domain.fcm.api.dto;

import com.widyu.domain.fcm.domain.FcmCategory;
import lombok.Builder;

@Builder
public record FcmSendDto(
        String title,
        String content,
        FcmCategory fcmCategory,
        String scheme
) {
}
