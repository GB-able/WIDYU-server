package com.widyu.fcm;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import lombok.Getter;

@Getter
public enum NotificationSettingGroup {
    GOAL("목표 관련 알림", FcmCategory.TARGET, FcmCategory.HEALTH_SCHEDULE, FcmCategory.WALK, FcmCategory.MEDICINE_SCHEDULE),
    ALBUM("앨범 관련 알림", FcmCategory.ALBUM),
    HOME("안전/소통 관련 알림", FcmCategory.SAFE_ZONE),
    ETC("기타 알림", FcmCategory.ETC);

    private final String description;
    private final FcmCategory[] categories;

    NotificationSettingGroup(String description, FcmCategory... categories) {
        this.description = description;
        this.categories = categories;
    }

    public static Stream<NotificationSettingGroup> stream() {
        return Stream.of(NotificationSettingGroup.values());
    }

    public List<FcmCategory> getCategories() {
        return Arrays.asList(this.categories);
    }
}
