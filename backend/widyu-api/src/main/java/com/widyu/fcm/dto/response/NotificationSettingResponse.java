package com.widyu.fcm.dto.response;

import com.widyu.fcm.FcmCategory;
import com.widyu.fcm.MemberNotificationSetting;
import lombok.Builder;

@Builder
public record NotificationSettingResponse(
        String group,
        String groupName,
        boolean enabled
) {
}