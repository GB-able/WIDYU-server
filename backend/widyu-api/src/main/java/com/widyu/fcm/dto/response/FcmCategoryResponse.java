package com.widyu.fcm.dto.response;

import com.widyu.notification.NotificationCenterFilter;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FcmCategoryResponse {
    private String label;
    private String name;
    private long count;

    public static FcmCategoryResponse of(NotificationCenterFilter tab, long count) {
        return FcmCategoryResponse.builder()
                .label(tab.name())
                .name(tab.label())
                .count(count)
                .build();
    }
}
