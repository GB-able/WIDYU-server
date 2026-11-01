package com.widyu.notification;

import com.widyu.fcm.FcmCategory;
import com.widyu.fcm.NotificationType;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.member.MemberType;
import java.util.Arrays;
import java.util.List;

/** 알림 목록 탭. 선언 순서가 앱에 내려가는 탭 순서다(LLD-0117). */
public enum NotificationCenterFilter {
    ALL("전체", List.of()),
    UNREAD("안 읽음", List.of()),
    MESSAGE("메시지", List.of(FcmCategory.HEART_MESSAGE)),
    GOAL("목표", List.of(FcmCategory.TARGET, FcmCategory.HEALTH_SCHEDULE,
            FcmCategory.WALK, FcmCategory.MEDICINE_SCHEDULE)),
    ALBUM("앨범", List.of(FcmCategory.ALBUM)),
    LOCATION("위치", List.of(FcmCategory.SAFE_ZONE)),
    ETC("기타", List.of(FcmCategory.ETC, FcmCategory.INCIDENT_SELF_CHECK, FcmCategory.LOCATION_NOTICE));

    private final String label;
    private final List<FcmCategory> legacyCategories;

    NotificationCenterFilter(String label, List<FcmCategory> legacyCategories) {
        this.label = label;
        this.legacyCategories = legacyCategories;
    }

    public static NotificationCenterFilter parse(String value, MemberType memberType) {
        if (value == null) {
            return ALL;
        }
        try {
            NotificationCenterFilter filter = valueOf(value);
            if (filter == LOCATION && memberType != MemberType.GUARDIAN) {
                throw new BusinessException(ErrorCode.INVALID_NOTIFICATION_CENTER_FILTER);
            }
            return filter;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_NOTIFICATION_CENTER_FILTER);
        }
    }

    /** 회원에게 보이는 탭. 위치는 보호자에게만 보인다. */
    public static List<NotificationCenterFilter> tabs(MemberType memberType) {
        return Arrays.stream(values())
                .filter(filter -> filter != LOCATION || memberType == MemberType.GUARDIAN)
                .toList();
    }

    public static List<NotificationCenterFilter> countedFilters(MemberType memberType) {
        return tabs(memberType).stream().filter(filter -> filter != ALL).toList();
    }

    public static String categoryOfLegacy(FcmCategory category) {
        if (category == null) {
            return null;
        }
        for (NotificationCenterFilter filter : values()) {
            if (filter.legacyCategories.contains(category)) {
                return filter.name();
            }
        }
        return null;
    }

    public boolean isCategory() {
        return this != ALL && this != UNREAD;
    }

    public String label() {
        return label;
    }

    public List<FcmCategory> legacyCategories() {
        return legacyCategories;
    }

    public List<NotificationType> types() {
        return Arrays.stream(NotificationType.values())
                .filter(type -> name().equals(type.centerFilter()))
                .toList();
    }
}
