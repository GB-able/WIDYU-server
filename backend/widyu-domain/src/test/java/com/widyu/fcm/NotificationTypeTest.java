package com.widyu.fcm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationTypeTest {
    @Test
    @DisplayName("승인된 서버 알림을 등록하면 34개 타입과 네 전달 방식을 반환한다")
    void 승인된_서버_알림을_등록하면_타입과_전달_방식을_반환한다() {
        // given / when
        NotificationType[] types = NotificationType.values();

        // then
        assertThat(types).hasSize(36);
        assertThat(Arrays.stream(types).map(NotificationType::deliveryMode).distinct())
                .containsExactlyInAnyOrder(DeliveryMode.values());
        assertThat(Arrays.stream(types).map(NotificationType::name))
                .doesNotContain("MEDICATION_DUE", "SAFETY_SENIOR_OK_NOTICE");
        assertThat(types).allMatch(type -> type.fcmCategory() != null
                && type.deliveryMode() != null && type.priority() != null
                && type.retentionClass() != null && type.settingGroup() != null
                && type.foregroundPresentation() != null);
    }

    @Test
    @DisplayName("안전 상황별 타입을 조회하면 위치 필터와 서로 다른 보존등급을 반환한다")
    void 안전_상황별_타입을_조회하면_위치_필터와_보존등급을_반환한다() {
        // given / when
        NotificationType emergency = NotificationType.HEART_RATE_EMERGENCY;
        NotificationType heartOk = NotificationType.SAFETY_SENIOR_OK_NOTICE_HEART;
        NotificationType zoneExit = NotificationType.SAFE_ZONE_EXITED;
        NotificationType zoneEnter = NotificationType.SAFE_ZONE_ENTERED;

        // then
        assertThat(emergency.centerFilter()).isEqualTo("LOCATION");
        assertThat(emergency.priority()).isEqualTo(NotificationPriority.CRITICAL);
        assertThat(emergency.settingGroup()).isEqualTo(PushSettingGroup.SAFETY);
        assertThat(NotificationType.SELF_HELP_REQUESTED.priority()).isEqualTo(NotificationPriority.CRITICAL);
        assertThat(NotificationType.SELF_HELP_REQUESTED.foregroundPresentation()).isEqualTo("MODAL");
        assertThat(NotificationType.SELF_HELP_REQUESTED.settingGroup()).isEqualTo(PushSettingGroup.SAFETY);
        assertThat(NotificationType.SELF_HELP_REQUEST_CANCELLED.priority())
                .isEqualTo(NotificationPriority.INTERACTION);
        assertThat(NotificationType.SELF_HELP_REQUEST_CANCELLED.settingGroup())
                .isEqualTo(PushSettingGroup.GENERAL);
        assertThat(heartOk.retentionClass()).isEqualTo(RetentionClass.HEART_EMERGENCY_180D);
        assertThat(zoneExit.retentionClass()).isEqualTo(RetentionClass.SAFE_ZONE_90D);
        assertThat(zoneEnter.retentionClass()).isEqualTo(RetentionClass.SAFE_ZONE_90D);
        assertThat(heartOk.priority()).isEqualTo(NotificationPriority.INTERACTION);
        assertThat(zoneExit.settingGroup()).isEqualTo(PushSettingGroup.SAFE_ZONE);
        assertThat(zoneEnter.settingGroup()).isEqualTo(PushSettingGroup.SAFE_ZONE);
        assertThat(zoneExit.priority()).isEqualTo(NotificationPriority.INTERACTION);
        assertThat(zoneEnter.priority()).isEqualTo(NotificationPriority.INTERACTION);
        assertThat(NotificationType.SAFETY_SELF_CHECK.centerFilter()).isNull();
        assertThat(NotificationType.SAFETY_SELF_CHECK.deliveryMode())
                .isEqualTo(DeliveryMode.PUSH_ONLY);
        assertThat(NotificationType.SAFETY_SELF_CHECK.settingGroup()).isEqualTo(PushSettingGroup.NONE);
        assertThat(NotificationType.FAMILY_LEADER_CHANGED.centerFilter()).isEqualTo("ETC");
    }

    @Test
    @DisplayName("정책서가 푸시+센터로 정한 타입을 조회하면 푸시와 센터를 모두 쓰고 동기화 타입은 데이터 전용이다")
    void 정책서가_푸시_센터로_정한_타입을_조회하면_푸시와_센터를_모두_쓴다() {
        // given / when / then
        assertThat(List.of(NotificationType.ALBUM_UPLOAD_COMPLETE, NotificationType.ALBUM_LIKED,
                NotificationType.MEDICATION_REMINDER_10, NotificationType.MEDICATION_REMINDER_20,
                NotificationType.WALK_GOAL_UNMET))
                .allSatisfy(type -> assertThat(type.deliveryMode()).isEqualTo(DeliveryMode.PUSH_AND_CENTER));
        assertThat(NotificationType.ALBUM_LIKED.settingGroup()).isEqualTo(PushSettingGroup.GENERAL);
        assertThat(NotificationType.ALBUM_LIKED.priority()).isEqualTo(NotificationPriority.INTERACTION);
        assertThat(NotificationType.ALBUM_UPLOAD_COMPLETE.priority()).isEqualTo(NotificationPriority.PASSIVE);
        assertThat(NotificationType.WALK_GOAL_UNMET.priority()).isEqualTo(NotificationPriority.PASSIVE);
        assertThat(NotificationType.MEDICATION_SCHEDULE_SYNC.deliveryMode()).isEqualTo(DeliveryMode.DATA_ONLY);
        assertThat(NotificationType.MEDICATION_SCHEDULE_SYNC.deepLinkTemplate()).isNull();
    }

    @Test
    @DisplayName("레지스트리의 딥링크 템플릿을 조회하면 커스텀 스킴 없이 앱 내부 경로만 반환한다")
    void 레지스트리의_딥링크_템플릿을_조회하면_앱_내부_경로만_반환한다() {
        // given / when
        List<String> templates = Arrays.stream(NotificationType.values())
                .map(NotificationType::deepLinkTemplate)
                .filter(template -> template != null)
                .toList();

        // then
        assertThat(templates).allSatisfy(template -> assertThat(template).startsWith("/"));
        assertThat(NotificationType.ALBUM_COMMENTED.deepLinkTemplate())
                .isEqualTo("/album/post?postId={entityId}&commentId={commentId}");
        assertThat(NotificationType.POINT_EARNED.deepLinkTemplate()).isEqualTo("/my/point");
        assertThat(NotificationType.SAFETY_SELF_CHECK.deepLinkTemplate()).isNull();
    }

    @Test
    @DisplayName("우선순위를 조회하면 iOS 표시 수준을 구분한다")
    void 우선순위를_조회하면_iOS_표시_수준을_구분한다() {
        // given / when / then
        assertThat(NotificationPriority.CRITICAL.interruptionLevel()).isEqualTo("time-sensitive");
        assertThat(NotificationPriority.TIME_SENSITIVE.interruptionLevel()).isEqualTo("time-sensitive");
        assertThat(NotificationPriority.INTERACTION.interruptionLevel()).isEqualTo("active");
        assertThat(NotificationPriority.PASSIVE.interruptionLevel()).isEqualTo("passive");
    }

    @Test
    @DisplayName("초기 안전 알림과 일정 알림 정책을 조회하면 모달과 조정된 시간 민감 범위를 반환한다")
    void 초기_안전_알림과_일정_정책을_조회하면_모달과_시간_민감_범위를_반환한다() {
        // given / when
        NotificationType emergency = NotificationType.HEART_RATE_EMERGENCY;

        // then
        assertThat(emergency.foregroundPresentation()).isEqualTo("MODAL");
        assertThat(emergency.priority()).isEqualTo(NotificationPriority.CRITICAL);
        assertThat(emergency.deliveryMode()).isEqualTo(DeliveryMode.PUSH_AND_CENTER);
        assertThat(emergency.deepLinkTemplate()).isEqualTo("/location?seniorId={seniorId}");
        // 복약 10·20분 리마인더는 공유 전송 규격대로 INTERACTION이다(LLD-0164가 LLD-0109의 TIME_SENSITIVE를 대체).
        assertThat(NotificationType.MEDICATION_REMINDER_10.priority()).isEqualTo(NotificationPriority.INTERACTION);
        assertThat(NotificationType.MEDICATION_REMINDER_20.priority()).isEqualTo(NotificationPriority.INTERACTION);
        assertThat(NotificationType.HEALTH_SCHEDULE_UPCOMING.priority())
                .isEqualTo(NotificationPriority.INTERACTION);
        assertThat(NotificationType.MEDICATION_PROOF_MISSING.priority())
                .isEqualTo(NotificationPriority.TIME_SENSITIVE);
    }
}
