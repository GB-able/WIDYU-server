package com.widyu.fcm.dto.response;

import com.widyu.fcm.FcmCategory;
import com.widyu.fcm.MemberNotificationSetting;
import lombok.Builder;

@Builder
public record NotificationSettingResponse(
        String category,
        String categoryName,
        boolean enabled
) {
    public static NotificationSettingResponse from(MemberNotificationSetting setting) {
        return NotificationSettingResponse.builder()
                .category(setting.getCategory().name())
                .categoryName(getCategoryName(setting.getCategory()))
                .enabled(setting.isEnabled())
                .build();
    }

    public static NotificationSettingResponse ofDefault(FcmCategory category) {
        return NotificationSettingResponse.builder()
                .category(category.name())
                .categoryName(getCategoryName(category))
                .enabled(true)
                .build();
    }

    private static String getCategoryName(FcmCategory category) {
        return switch (category) {
            case ALL -> "전체";
            case ALBUM -> "앨범";
            case TARGET -> "응원 메시지";
            case HEALTH_SCHEDULE -> "방문 일정";
            case WALK -> "만보계";
            case MEDICINE_SCHEDULE -> "복약 알림";
            case HEART_MESSAGE -> "가족 메시지";
            case SAFE_ZONE -> "안전구역";
        };
    }
}