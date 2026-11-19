package com.widyu.fcm.dto.response;

import com.widyu.fcm.PushSettingGroup;

public record NotificationSettingResponse(
        String group,
        String groupName,
        String section,
        int order,
        boolean enabled,
        boolean recipientEnabled,
        boolean productPushEnabled,
        boolean mandatoryProductPush,
        boolean canEditProductPush,
        String devicePushStatus,
        long policyRevision
) {
    public static NotificationSettingResponse of(PushSettingGroup group, boolean enabled,
                                                  boolean mandatory, long policyRevision) {
        return new NotificationSettingResponse(group.name(), group.getDescription(), group.getSection(),
                group.displayOrder(), enabled, true, enabled, mandatory, !mandatory, "unknown", policyRevision);
    }
}
