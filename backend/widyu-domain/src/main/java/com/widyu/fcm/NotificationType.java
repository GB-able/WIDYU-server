package com.widyu.fcm;

/**
 * 제품 이벤트의 전달·표시 정책.
 * 설정 그룹은 보호자 수신에만 쓴다(시니어는 켜짐 고정). 시니어만 받는 알림은 시니어 그룹(ALBUM·GOAL)에 둔다(LLD-0175).
 */
public enum NotificationType {
    ALBUM_UPLOAD_COMPLETE(FcmCategory.ALBUM, "ALBUM", DeliveryMode.PUSH_AND_CENTER, NotificationPriority.PASSIVE, RetentionClass.ROUTINE_90D, PushSettingGroup.ALBUM_UPLOAD_COMPLETE, "NONE", "/album/post?postId={entityId}", "A01"),
    ALBUM_CREATED(FcmCategory.ALBUM, "ALBUM", DeliveryMode.PUSH_AND_CENTER, NotificationPriority.INTERACTION, RetentionClass.ROUTINE_90D, PushSettingGroup.ALBUM_NEW_POST, "BANNER", "/album/post?postId={entityId}", "A02-S/C"),
    ALBUM_COMMENTED(FcmCategory.ALBUM, "ALBUM", DeliveryMode.PUSH_AND_CENTER, NotificationPriority.INTERACTION, RetentionClass.ROUTINE_90D, PushSettingGroup.ALBUM_COMMENT, "BANNER", "/album/post?postId={entityId}&commentId={commentId}", "A03"),
    ALBUM_REPLIED(FcmCategory.ALBUM, "ALBUM", DeliveryMode.PUSH_AND_CENTER, NotificationPriority.INTERACTION, RetentionClass.ROUTINE_90D, PushSettingGroup.ALBUM_COMMENT, "BANNER", "/album/post?postId={entityId}&commentId={commentId}", "A04"),
    ALBUM_LIKED(FcmCategory.ALBUM, "ALBUM", DeliveryMode.PUSH_AND_CENTER, NotificationPriority.INTERACTION, RetentionClass.ROUTINE_90D, PushSettingGroup.ALBUM_LIKE, "BANNER", "/album/post?postId={entityId}", "A05"),
    ALBUM_UNLOCKED(FcmCategory.ALBUM, "ALBUM", DeliveryMode.PUSH_AND_CENTER, NotificationPriority.INTERACTION, RetentionClass.ROUTINE_90D, PushSettingGroup.ALBUM_SENIOR_VIEWED, "BANNER", "/post?postId={entityId}", "A06-L/Z"),
    ALBUM_ALL_VIEWED(FcmCategory.ALBUM, "ALBUM", DeliveryMode.PUSH_AND_CENTER, NotificationPriority.INTERACTION, RetentionClass.ROUTINE_90D, PushSettingGroup.ALBUM_SENIOR_VIEWED, "BANNER", "/album", "A07"),
    ALBUM_OPENED_BY_SENIOR(FcmCategory.ALBUM, "ALBUM", DeliveryMode.PUSH_AND_CENTER, NotificationPriority.INTERACTION, RetentionClass.ROUTINE_90D, PushSettingGroup.ALBUM, "BANNER", "/album/post?postId={entityId}", "A08"),
    MEDICATION_REMINDER_10(FcmCategory.MEDICINE_SCHEDULE, "GOAL", DeliveryMode.PUSH_AND_CENTER, NotificationPriority.INTERACTION, RetentionClass.ROUTINE_90D, PushSettingGroup.GOAL, "BANNER", "/goal", "M02"),
    MEDICATION_REMINDER_20(FcmCategory.MEDICINE_SCHEDULE, "GOAL", DeliveryMode.PUSH_AND_CENTER, NotificationPriority.INTERACTION, RetentionClass.ROUTINE_90D, PushSettingGroup.GOAL, "BANNER", "/goal", "M03"),
    MEDICATION_PROOF_MISSING(FcmCategory.MEDICINE_SCHEDULE, "GOAL", DeliveryMode.PUSH_AND_CENTER, NotificationPriority.TIME_SENSITIVE, RetentionClass.ROUTINE_90D, PushSettingGroup.MEDICATION_CHECK, "BANNER", "/goal/medicine?seniorId={seniorId}", "M04"),
    MEDICATION_SCHEDULE_CHANGED(FcmCategory.MEDICINE_SCHEDULE, "GOAL", DeliveryMode.PUSH_AND_CENTER, NotificationPriority.INTERACTION, RetentionClass.ROUTINE_90D, PushSettingGroup.GOAL, "BANNER", "/goal", "M05"),
    HEALTH_SCHEDULE_UPCOMING(FcmCategory.HEALTH_SCHEDULE, "GOAL", DeliveryMode.PUSH_AND_CENTER, NotificationPriority.INTERACTION, RetentionClass.ROUTINE_90D, PushSettingGroup.HEALTH_SCHEDULE, "BANNER", "/goal", "H01-S/C-SELF/C-SENIOR-OS/INAPP"),
    WALK_GOAL_UNMET(FcmCategory.WALK, "GOAL", DeliveryMode.PUSH_AND_CENTER, NotificationPriority.PASSIVE, RetentionClass.ROUTINE_90D, PushSettingGroup.GOAL, "BANNER", "/goal", "W01"),
    GOAL_ACHIEVED(FcmCategory.TARGET, "GOAL", DeliveryMode.PUSH_AND_CENTER, NotificationPriority.INTERACTION, RetentionClass.ROUTINE_90D, PushSettingGroup.GOAL_ACHIEVED, "BANNER", "/goal", "G01-S/C"),
    GOALS_ACHIEVED_GROUPED(FcmCategory.TARGET, "GOAL", DeliveryMode.PUSH_AND_CENTER, NotificationPriority.INTERACTION, RetentionClass.ROUTINE_90D, PushSettingGroup.GOAL_ACHIEVED, "BANNER", "/goal", "G02-S/C"),
    POINT_EARNED(FcmCategory.TARGET, "ETC", DeliveryMode.CENTER_ONLY, NotificationPriority.PASSIVE, RetentionClass.ROUTINE_90D, PushSettingGroup.NONE, "NONE", "/my/point", "P01"),
    POINT_USED(FcmCategory.TARGET, "ETC", DeliveryMode.CENTER_ONLY, NotificationPriority.PASSIVE, RetentionClass.ROUTINE_90D, PushSettingGroup.NONE, "NONE", "/my/point", "P02"),
    HEART_MESSAGE_RECEIVED(FcmCategory.HEART_MESSAGE, "MESSAGE", DeliveryMode.PUSH_AND_CENTER, NotificationPriority.INTERACTION, RetentionClass.ROUTINE_90D, PushSettingGroup.CHEER_MESSAGE, "BANNER", null, "X01-OS/INAPP"),
    CHEER_MESSAGE_RECEIVED(FcmCategory.HEART_MESSAGE, "MESSAGE", DeliveryMode.PUSH_AND_CENTER, NotificationPriority.INTERACTION, RetentionClass.ROUTINE_90D, PushSettingGroup.CHEER_MESSAGE, "BANNER", null, "X02-OS/INAPP"),
    SAFETY_SELF_CHECK(FcmCategory.INCIDENT_SELF_CHECK, null, DeliveryMode.PUSH_ONLY, NotificationPriority.CRITICAL, RetentionClass.ROUTINE_90D, PushSettingGroup.NONE, "BANNER", null, "S01"),
    HEART_RATE_EMERGENCY(FcmCategory.HEART_MESSAGE, "LOCATION", DeliveryMode.PUSH_AND_CENTER, NotificationPriority.CRITICAL, RetentionClass.HEART_EMERGENCY_180D, PushSettingGroup.SAFETY, "MODAL", "/location?seniorId={seniorId}", "S01/S03/S04/S06"),
    SELF_HELP_REQUESTED(FcmCategory.HEART_MESSAGE, "LOCATION", DeliveryMode.PUSH_AND_CENTER, NotificationPriority.CRITICAL, RetentionClass.HEART_EMERGENCY_180D, PushSettingGroup.SAFETY, "MODAL", "/location?seniorId={seniorId}", "K01"),
    SELF_HELP_REQUEST_CANCELLED(FcmCategory.HEART_MESSAGE, "LOCATION", DeliveryMode.PUSH_AND_CENTER, NotificationPriority.INTERACTION, RetentionClass.HEART_EMERGENCY_180D, PushSettingGroup.SAFETY, "BANNER", "/location?seniorId={seniorId}", "K02"),
    SAFE_ZONE_EXITED(FcmCategory.SAFE_ZONE, "LOCATION", DeliveryMode.PUSH_AND_CENTER, NotificationPriority.INTERACTION, RetentionClass.SAFE_ZONE_90D, PushSettingGroup.SAFE_ZONE, "BANNER", "/location?seniorId={seniorId}", "Z01"),
    SAFE_ZONE_ENTERED(FcmCategory.SAFE_ZONE, "LOCATION", DeliveryMode.PUSH_AND_CENTER, NotificationPriority.INTERACTION, RetentionClass.SAFE_ZONE_90D, PushSettingGroup.SAFE_ZONE, "BANNER", "/location?seniorId={seniorId}", "Z02"),
    SAFETY_SENIOR_OK_NOTICE_HEART(FcmCategory.HEART_MESSAGE, "LOCATION", DeliveryMode.PUSH_AND_CENTER, NotificationPriority.INTERACTION, RetentionClass.HEART_EMERGENCY_180D, PushSettingGroup.SENIOR_OK_NOTICE, "BANNER", "/location?seniorId={seniorId}", "S08"),
    FAMILY_LEADER_CHANGED(FcmCategory.ETC, "ETC", DeliveryMode.PUSH_AND_CENTER, NotificationPriority.INTERACTION, RetentionClass.ROUTINE_90D, PushSettingGroup.LEADER_CHANGED, "BANNER", "/family-manage", "R01"),
    MEDICATION_SCHEDULE_CREATED(FcmCategory.MEDICINE_SCHEDULE, "GOAL", DeliveryMode.PUSH_AND_CENTER, NotificationPriority.INTERACTION, RetentionClass.ROUTINE_90D, PushSettingGroup.GOAL, "BANNER", "/goal", "M08"),
    MEDICATION_SCHEDULE_DELETED(FcmCategory.MEDICINE_SCHEDULE, "GOAL", DeliveryMode.PUSH_AND_CENTER, NotificationPriority.INTERACTION, RetentionClass.ROUTINE_90D, PushSettingGroup.GOAL, "BANNER", "/goal", "M09"),
    MEDICATION_SCHEDULE_SYNC(FcmCategory.MEDICINE_SCHEDULE, null, DeliveryMode.DATA_ONLY, NotificationPriority.PASSIVE, RetentionClass.ROUTINE_90D, PushSettingGroup.NONE, "NONE", null, null),
    HEALTH_SCHEDULE_CREATED(FcmCategory.HEALTH_SCHEDULE, "GOAL", DeliveryMode.PUSH_AND_CENTER, NotificationPriority.INTERACTION, RetentionClass.ROUTINE_90D, PushSettingGroup.GOAL, "BANNER", "/goal", "H02"),
    HEALTH_SCHEDULE_UPDATED(FcmCategory.HEALTH_SCHEDULE, "GOAL", DeliveryMode.PUSH_AND_CENTER, NotificationPriority.INTERACTION, RetentionClass.ROUTINE_90D, PushSettingGroup.GOAL, "BANNER", "/goal", "H03"),
    HEALTH_SCHEDULE_DELETED(FcmCategory.HEALTH_SCHEDULE, "GOAL", DeliveryMode.PUSH_AND_CENTER, NotificationPriority.INTERACTION, RetentionClass.ROUTINE_90D, PushSettingGroup.GOAL, "BANNER", "/goal", "H04"),
    WALK_GOAL_CHANGED(FcmCategory.WALK, "GOAL", DeliveryMode.PUSH_AND_CENTER, NotificationPriority.INTERACTION, RetentionClass.ROUTINE_90D, PushSettingGroup.GOAL, "BANNER", "/goal", "W02"),
    WALK_DATA_SYNC(FcmCategory.WALK, null, DeliveryMode.DATA_ONLY, NotificationPriority.CRITICAL, RetentionClass.ROUTINE_90D, PushSettingGroup.NONE, "NONE", null, null),
    // 위치정보법 제3자 제공 통보라 끌 수 없다(NONE). 조용히(apns 5)는 도달이 늦을 수 있어 보통으로 둔다(LLD-0179).
    LOCATION_ACCESS_NOTICE(FcmCategory.LOCATION_NOTICE, "ETC", DeliveryMode.PUSH_AND_CENTER, NotificationPriority.INTERACTION, RetentionClass.ROUTINE_90D, PushSettingGroup.NONE, "BANNER", null, "L01"),
    LOCATION_ACCESS_DIGEST(FcmCategory.LOCATION_NOTICE, "ETC", DeliveryMode.PUSH_AND_CENTER, NotificationPriority.INTERACTION, RetentionClass.ROUTINE_90D, PushSettingGroup.NONE, "BANNER", null, "L02"),
    // 누르면 위듀 앱 설문 화면(/survey)으로 간다. 앱이 GET /followups/current로 카드를 다시 읽는다. 어르신 스위치는 켜짐 고정이다(LLD-0180).
    FOLLOWUP_SURVEY_REQUESTED(FcmCategory.ETC, "ETC", DeliveryMode.PUSH_AND_CENTER, NotificationPriority.INTERACTION, RetentionClass.ROUTINE_90D, PushSettingGroup.ETC, "BANNER", "/survey", "F01");

    private final FcmCategory fcmCategory;
    private final String centerFilter;
    private final DeliveryMode deliveryMode;
    private final NotificationPriority priority;
    private final RetentionClass retentionClass;
    private final PushSettingGroup settingGroup;
    private final String foregroundPresentation;
    private final String deepLinkTemplate;
    private final String copyCode;

    NotificationType(FcmCategory fcmCategory, String centerFilter, DeliveryMode deliveryMode,
            NotificationPriority priority, RetentionClass retentionClass, PushSettingGroup settingGroup,
            String foregroundPresentation, String deepLinkTemplate, String copyCode) {
        this.fcmCategory = fcmCategory;
        this.centerFilter = centerFilter;
        this.deliveryMode = deliveryMode;
        this.priority = priority;
        this.retentionClass = retentionClass;
        this.settingGroup = settingGroup;
        this.foregroundPresentation = foregroundPresentation;
        this.deepLinkTemplate = deepLinkTemplate;
        this.copyCode = copyCode;
    }

    public FcmCategory fcmCategory() { return fcmCategory; }
    public String centerFilter() { return centerFilter; }
    public DeliveryMode deliveryMode() { return deliveryMode; }
    public NotificationPriority priority() { return priority; }
    public RetentionClass retentionClass() { return retentionClass; }
    public PushSettingGroup settingGroup() { return settingGroup; }
    public String foregroundPresentation() { return foregroundPresentation; }
    public String deepLinkTemplate() { return deepLinkTemplate; }
    public String copyCode() { return copyCode; }
}
