package com.widyu.fcm.dto.response;

import com.widyu.fcm.FcmNotification;
import java.time.LocalDateTime;

public record FcmNotificationResponse(
        Long notificationId,
        String image,
        String category,
        String title,
        String content,
        LocalDateTime createdAt,
        String scheme
) {
    public static FcmNotificationResponse from(FcmNotification n) {
        return new FcmNotificationResponse(
                n.getId(),
                n.getImage(),
                n.getFcmCategory() != null ? n.getFcmCategory().name() : "ALL",
                n.getTitle(),
                n.getBody(),
                n.getCreatedAt(),
                "" // 기본 스킴 값
        );
    }
}
