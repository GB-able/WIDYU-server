package com.widyu.fcm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.widyu.member.MemberType;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PushSettingGroupTest {
    @Test
    @DisplayName("기존 카테고리를 변환하면 타입 없는 알림의 그룹을 반환한다")
    void 기존_카테고리를_변환하면_설정_그룹을_반환한다() {
        // when / then
        assertThat(PushSettingGroup.fromLegacy(FcmCategory.MEDICINE_SCHEDULE))
                .isEqualTo(PushSettingGroup.MEDICATION_CHECK);
        assertThat(PushSettingGroup.fromLegacy(FcmCategory.SAFE_ZONE))
                .isEqualTo(PushSettingGroup.SAFE_ZONE);
        assertThat(PushSettingGroup.fromLegacy(FcmCategory.ALBUM))
                .isEqualTo(PushSettingGroup.ALBUM_NEW_POST);
        assertThat(PushSettingGroup.fromLegacy(FcmCategory.HEART_MESSAGE))
                .isEqualTo(PushSettingGroup.CHEER_MESSAGE);
        assertThat(PushSettingGroup.fromLegacy(FcmCategory.LOCATION_NOTICE)).isNull();
    }

    @Test
    @DisplayName("회원 역할로 설정 그룹을 고르면 시니어 네 항목과 보호자 정책서 스위치 14개가 분리된다")
    void 역할별_설정_그룹을_고르면_시니어_네_항목과_보호자_14개가_분리된다() {
        // when / then
        assertThat(PushSettingGroup.stream().filter(group -> group.visibleFor(MemberType.SENIOR)).toList())
                .containsExactly(PushSettingGroup.ALBUM, PushSettingGroup.GOAL,
                        PushSettingGroup.HOME, PushSettingGroup.ETC);
        assertThat(PushSettingGroup.stream().filter(group -> group.visibleFor(MemberType.GUARDIAN)).toList())
                .containsExactly(PushSettingGroup.SAFETY, PushSettingGroup.SAFE_ZONE,
                        PushSettingGroup.SENIOR_OK_NOTICE, PushSettingGroup.MEDICATION_CHECK,
                        PushSettingGroup.HEALTH_SCHEDULE, PushSettingGroup.GOAL_ACHIEVED,
                        PushSettingGroup.ALBUM_NEW_POST, PushSettingGroup.ALBUM_COMMENT,
                        PushSettingGroup.ALBUM_LIKE, PushSettingGroup.ALBUM_SENIOR_VIEWED,
                        PushSettingGroup.ALBUM_UPLOAD_COMPLETE, PushSettingGroup.CHEER_MESSAGE,
                        PushSettingGroup.LEADER_CHANGED, PushSettingGroup.SERVICE_NOTICE);
        assertThat(PushSettingGroup.GENERAL.visibleFor(MemberType.GUARDIAN)).isFalse();
    }

    @Test
    @DisplayName("보호자 스위치의 묶음과 순서를 조회하면 정책서 2절 표의 묶음과 순서를 반환한다")
    void 보호자_스위치의_묶음과_순서를_조회하면_정책서_표를_반환한다() {
        // when / then
        assertThat(PushSettingGroup.stream().filter(group -> group.visibleFor(MemberType.GUARDIAN))
                .map(PushSettingGroup::getSection).distinct().toList())
                .containsExactly("LOCATION", "GOAL", "ALBUM", "MESSAGE", "ETC");
        assertThat(PushSettingGroup.SAFETY.displayOrder()).isEqualTo(1);
        assertThat(PushSettingGroup.MEDICATION_CHECK.displayOrder()).isEqualTo(4);
        assertThat(PushSettingGroup.SERVICE_NOTICE.displayOrder()).isEqualTo(14);
        assertThat(PushSettingGroup.ALBUM.getSection()).isNull();
        assertThat(PushSettingGroup.ALBUM.displayOrder()).isEqualTo(1);
        assertThat(PushSettingGroup.ETC.displayOrder()).isEqualTo(4);
    }

    @Test
    @DisplayName("그룹의 방장 필수 속성을 조회하면 안전 두 그룹만 필수로 반환한다")
    void 그룹의_방장_필수_속성을_조회하면_안전_두_그룹만_필수로_반환한다() {
        // when / then
        assertThat(PushSettingGroup.SAFETY.isMandatoryForLeader()).isTrue();
        assertThat(PushSettingGroup.SAFE_ZONE.isMandatoryForLeader()).isTrue();
        assertThat(PushSettingGroup.MEDICATION_CHECK.isMandatoryForLeader()).isFalse();
        assertThat(PushSettingGroup.stream().filter(PushSettingGroup::isMandatoryForLeader).toList())
                .containsExactly(PushSettingGroup.SAFETY, PushSettingGroup.SAFE_ZONE);
        assertThat(PushSettingGroup.NONE.isMandatoryForLeader()).isFalse();
        assertThat(PushSettingGroup.stream().toList()).doesNotContain(PushSettingGroup.NONE);
    }

    @Test
    @DisplayName("그룹의 보호자 전원 필수 속성을 조회하면 안전 그룹만 필수로 반환한다")
    void 그룹의_보호자_전원_필수_속성을_조회하면_안전_그룹만_필수로_반환한다() {
        // when / then
        assertThat(PushSettingGroup.stream().filter(PushSettingGroup::isMandatoryForGuardians).toList())
                .containsExactly(PushSettingGroup.SAFETY);
    }

    @Test
    @DisplayName("NONE 그룹을 설정 행으로 만들면 예외가 발생한다")
    void NONE_그룹을_설정_행으로_만들면_예외가_발생한다() {
        // when / then
        assertThatThrownBy(() -> MemberNotificationSetting.create(null, PushSettingGroup.NONE, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("이벤트 타입의 설정 그룹을 읽으면 카테고리와 무관하게 승인된 정책을 반환한다")
    void 이벤트_타입의_설정_그룹을_읽으면_승인된_정책을_반환한다() {
        // when / then
        assertThat(NotificationType.HEART_RATE_EMERGENCY.settingGroup()).isEqualTo(PushSettingGroup.SAFETY);
        assertThat(NotificationType.SAFE_ZONE_EXITED.settingGroup()).isEqualTo(PushSettingGroup.SAFE_ZONE);
        assertThat(NotificationType.MEDICATION_PROOF_MISSING.settingGroup())
                .isEqualTo(PushSettingGroup.MEDICATION_CHECK);
        assertThat(NotificationType.MEDICATION_SCHEDULE_CHANGED.settingGroup())
                .isEqualTo(PushSettingGroup.GOAL);
        assertThat(NotificationType.SAFETY_SENIOR_OK_NOTICE_HEART.settingGroup())
                .isEqualTo(PushSettingGroup.SENIOR_OK_NOTICE);
        assertThat(NotificationType.SAFE_ZONE_ENTERED.settingGroup())
                .isEqualTo(PushSettingGroup.SAFE_ZONE);
        assertThat(NotificationType.GOAL_ACHIEVED.settingGroup()).isEqualTo(PushSettingGroup.GOAL_ACHIEVED);
        assertThat(NotificationType.HEALTH_SCHEDULE_UPCOMING.settingGroup())
                .isEqualTo(PushSettingGroup.HEALTH_SCHEDULE);
        assertThat(NotificationType.ALBUM_CREATED.settingGroup()).isEqualTo(PushSettingGroup.ALBUM_NEW_POST);
        assertThat(NotificationType.ALBUM_REPLIED.settingGroup()).isEqualTo(PushSettingGroup.ALBUM_COMMENT);
        assertThat(NotificationType.ALBUM_ALL_VIEWED.settingGroup())
                .isEqualTo(PushSettingGroup.ALBUM_SENIOR_VIEWED);
        assertThat(NotificationType.CHEER_MESSAGE_RECEIVED.settingGroup())
                .isEqualTo(PushSettingGroup.CHEER_MESSAGE);
        assertThat(NotificationType.FAMILY_LEADER_CHANGED.settingGroup())
                .isEqualTo(PushSettingGroup.LEADER_CHANGED);
    }

    @Test
    @DisplayName("모든 알림 타입의 설정 그룹을 읽으면 옛 일반 그룹을 쓰는 타입이 없다")
    void 모든_알림_타입의_설정_그룹을_읽으면_옛_일반_그룹을_쓰지_않는다() {
        // when / then
        assertThat(Arrays.stream(NotificationType.values()).map(NotificationType::settingGroup))
                .doesNotContain(PushSettingGroup.GENERAL);
    }
}
