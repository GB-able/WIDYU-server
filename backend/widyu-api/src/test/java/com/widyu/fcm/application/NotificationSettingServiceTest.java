package com.widyu.fcm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.widyu.fcm.MemberNotificationSetting;
import com.widyu.fcm.PushSettingGroup;
import com.widyu.fcm.dto.request.UpdateNotificationSettingRequest;
import com.widyu.fcm.dto.response.NotificationSettingResponse;
import com.widyu.fcm.repository.MemberNotificationSettingRepository;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.util.MemberUtil;
import com.widyu.member.FamilyMembership;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.MemberRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotificationSettingServiceTest {
    @Mock private MemberNotificationSettingRepository settings;
    @Mock private FamilyMembershipRepository memberships;
    @Mock private MemberRepository members;
    @Mock private MemberUtil memberUtil;
    @InjectMocks private NotificationSettingService service;

    @Test
    @DisplayName("보호자가 설정을 조회하면 기본 켜짐인 네 그룹을 반환한다")
    void 보호자가_설정을_조회하면_네_그룹을_반환한다() {
        // given
        Member member = member(MemberType.GUARDIAN);
        given(memberUtil.getCurrentMember()).willReturn(member);
        given(settings.findAllByMemberId(1L)).willReturn(List.of());

        // when
        List<NotificationSettingResponse> result = service.getNotificationSettings();

        // then
        assertThat(result).extracting(NotificationSettingResponse::group)
                .containsExactly("SAFETY", "SAFE_ZONE", "MEDICATION_CHECK", "GENERAL");
        assertThat(result).allSatisfy(row -> {
            assertThat(row.productPushEnabled()).isTrue();
            assertThat(row.recipientEnabled()).isTrue();
            assertThat(row.devicePushStatus()).isEqualTo("unknown");
            assertThat(row.policyRevision()).isZero();
        });
    }

    @Test
    @DisplayName("시니어가 설정을 조회하면 네 항목을 끌 수 없는 켜짐으로 반환한다")
    void 시니어가_설정을_조회하면_네_항목을_잠긴_켜짐으로_반환한다() {
        // given
        Member member = member(MemberType.SENIOR);
        given(memberUtil.getCurrentMember()).willReturn(member);
        given(settings.findAllByMemberId(1L)).willReturn(List.of());

        // when
        List<NotificationSettingResponse> result = service.getNotificationSettings();

        // then
        assertThat(result).extracting(NotificationSettingResponse::group)
                .containsExactly("ALBUM", "GOAL", "HOME", "ETC");
        assertThat(result).allSatisfy(row -> {
            assertThat(row.enabled()).isTrue();
            assertThat(row.mandatoryProductPush()).isTrue();
            assertThat(row.canEditProductPush()).isFalse();
        });
    }

    @Test
    @DisplayName("시니어의 꺼진 행이 남아 있어도 조회하면 네 항목 모두 켜짐을 반환한다")
    void 시니어의_꺼진_행이_남아_있어도_조회하면_켜짐을_반환한다() {
        // given
        Member member = member(MemberType.SENIOR);
        MemberNotificationSetting album = MemberNotificationSetting.create(member, PushSettingGroup.ALBUM, false);
        MemberNotificationSetting goal = MemberNotificationSetting.create(member, PushSettingGroup.GOAL, false);
        given(memberUtil.getCurrentMember()).willReturn(member);
        given(settings.findAllByMemberId(1L)).willReturn(List.of(album, goal));

        // when
        List<NotificationSettingResponse> result = service.getNotificationSettings();

        // then
        assertThat(result).extracting(NotificationSettingResponse::enabled)
                .containsExactly(true, true, true, true);
        assertThat(result).extracting(NotificationSettingResponse::mandatoryProductPush)
                .containsExactly(true, true, true, true);
    }

    @Test
    @DisplayName("시니어가 목표 푸시를 끄면 필수 알림 예외가 발생하고 revision이 그대로다")
    void 시니어가_목표_푸시를_끄면_필수_알림_예외가_발생한다() {
        // given
        Member member = member(MemberType.SENIOR);
        given(memberUtil.getCurrentMember()).willReturn(member);
        given(members.findByIdForUpdate(1L)).willReturn(Optional.of(member));

        // when / then
        assertThatThrownBy(() -> service.updateNotificationSetting(
                new UpdateNotificationSettingRequest("GOAL", false, 0L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MANDATORY_NOTIFICATION_PUSH);
        assertThat(member.getNotificationPolicyRevision()).isZero();
    }

    @Test
    @DisplayName("시니어가 앨범 푸시를 켜면 저장 없이 잠긴 켜짐을 반환한다")
    void 시니어가_앨범_푸시를_켜면_잠긴_켜짐을_반환한다() {
        // given
        Member member = member(MemberType.SENIOR);
        given(memberUtil.getCurrentMember()).willReturn(member);
        given(members.findByIdForUpdate(1L)).willReturn(Optional.of(member));

        // when
        NotificationSettingResponse result = service.updateNotificationSetting(
                new UpdateNotificationSettingRequest("ALBUM", true, 0L));

        // then
        assertThat(result.enabled()).isTrue();
        assertThat(result.mandatoryProductPush()).isTrue();
        assertThat(result.canEditProductPush()).isFalse();
        assertThat(member.getNotificationPolicyRevision()).isZero();
    }

    @Test
    @DisplayName("방장이 설정을 조회하면 안전 그룹이 저장값과 무관하게 잠금 켜짐이다")
    void 방장이_설정을_조회하면_안전_그룹을_잠금_켜짐으로_반환한다() {
        // given
        Member member = member(MemberType.GUARDIAN);
        FamilyMembership membership = mock(FamilyMembership.class);
        MemberNotificationSetting safetyOff = MemberNotificationSetting.create(member, PushSettingGroup.SAFETY, false);
        given(memberUtil.getCurrentMember()).willReturn(member);
        given(memberships.findByGuardianId(1L)).willReturn(Optional.of(membership));
        given(membership.isLeader()).willReturn(true);
        given(settings.findAllByMemberId(1L)).willReturn(List.of(safetyOff));

        // when
        NotificationSettingResponse safety = service.getNotificationSettings().getFirst();

        // then
        assertThat(safety.productPushEnabled()).isTrue();
        assertThat(safety.mandatoryProductPush()).isTrue();
        assertThat(safety.canEditProductPush()).isFalse();
    }

    @Test
    @DisplayName("방장의 안심구역 푸시 자격을 판정하면 꺼진 저장값과 무관하게 허용한다")
    void 방장의_안심구역_푸시를_판정하면_저장값과_무관하게_허용한다() {
        // given
        FamilyMembership membership = mock(FamilyMembership.class);
        given(memberships.findByGuardianId(1L)).willReturn(Optional.of(membership));
        given(membership.isLeader()).willReturn(true);

        // when
        boolean enabled = service.isNotificationEnabled(1L, PushSettingGroup.SAFE_ZONE);

        // then
        assertThat(enabled).isTrue();
    }

    @Test
    @DisplayName("비방장의 안전 푸시 자격을 판정하면 꺼진 저장값이 있어도 허용한다")
    void 비방장의_안전_푸시를_판정하면_꺼진_저장값이_있어도_허용한다() {
        // when
        boolean enabled = service.isNotificationEnabled(1L, PushSettingGroup.SAFETY);

        // then
        assertThat(enabled).isTrue();
    }

    @Test
    @DisplayName("비방장이 설정을 조회하면 안전은 꺼진 저장값과 무관하게 잠금 켜짐이고 안심구역은 수정할 수 있다")
    void 비방장이_설정을_조회하면_안전만_잠금_켜짐이다() {
        // given
        Member member = member(MemberType.GUARDIAN);
        MemberNotificationSetting safetyOff = MemberNotificationSetting.create(member, PushSettingGroup.SAFETY, false);
        given(memberUtil.getCurrentMember()).willReturn(member);
        given(settings.findAllByMemberId(1L)).willReturn(List.of(safetyOff));

        // when
        List<NotificationSettingResponse> result = service.getNotificationSettings();

        // then
        NotificationSettingResponse safety = result.get(0);
        NotificationSettingResponse safeZone = result.get(1);
        assertThat(safety.productPushEnabled()).isTrue();
        assertThat(safety.mandatoryProductPush()).isTrue();
        assertThat(safety.canEditProductPush()).isFalse();
        assertThat(safeZone.mandatoryProductPush()).isFalse();
        assertThat(safeZone.canEditProductPush()).isTrue();
    }

    @Test
    @DisplayName("비방장이 안전 푸시를 끄면 충돌 예외가 발생하고 revision이 그대로다")
    void 비방장이_안전_푸시를_끄면_충돌_예외가_발생한다() {
        // given
        Member member = member(MemberType.GUARDIAN);
        given(memberUtil.getCurrentMember()).willReturn(member);
        given(members.findByIdForUpdate(1L)).willReturn(Optional.of(member));

        // when / then
        assertThatThrownBy(() -> service.updateNotificationSetting(
                new UpdateNotificationSettingRequest("SAFETY", false, 0L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MANDATORY_NOTIFICATION_PUSH);
        assertThat(member.getNotificationPolicyRevision()).isZero();
    }

    @Test
    @DisplayName("방장이 안전 푸시를 끄면 충돌 예외가 발생한다")
    void 방장이_안전_푸시를_끄면_충돌_예외가_발생한다() {
        // given
        Member member = member(MemberType.GUARDIAN);
        FamilyMembership membership = mock(FamilyMembership.class);
        given(memberUtil.getCurrentMember()).willReturn(member);
        given(members.findByIdForUpdate(1L)).willReturn(Optional.of(member));
        given(memberships.findByGuardianId(1L)).willReturn(Optional.of(membership));
        given(membership.isLeader()).willReturn(true);

        // when / then
        assertThatThrownBy(() -> service.updateNotificationSetting(
                new UpdateNotificationSettingRequest("SAFETY", false, 0L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MANDATORY_NOTIFICATION_PUSH);
        assertThat(member.getNotificationPolicyRevision()).isZero();
    }

    @Test
    @DisplayName("비방장이 안심구역 푸시를 끄면 자기 설정과 revision이 바뀐다")
    void 비방장이_안심구역_푸시를_끄면_자기_설정과_revision이_바뀐다() {
        // given
        Member member = member(MemberType.GUARDIAN);
        given(memberUtil.getCurrentMember()).willReturn(member);
        given(members.findByIdForUpdate(1L)).willReturn(Optional.of(member));
        given(settings.findByMemberIdAndCategory(1L, PushSettingGroup.SAFE_ZONE))
                .willReturn(Optional.empty());

        // when
        NotificationSettingResponse result = service.updateNotificationSetting(
                new UpdateNotificationSettingRequest("SAFE_ZONE", false, 0L));

        // then
        assertThat(result.productPushEnabled()).isFalse();
        assertThat(result.canEditProductPush()).isTrue();
        assertThat(result.policyRevision()).isEqualTo(1L);
        assertThat(member.getNotificationPolicyRevision()).isEqualTo(1L);
    }

    @Test
    @DisplayName("비방장이 꺼진 안심구역 푸시를 켜면 저장값과 revision이 바뀐다")
    void 비방장이_꺼진_안심구역_푸시를_켜면_저장값이_바뀐다() {
        // given
        Member member = member(MemberType.GUARDIAN);
        MemberNotificationSetting setting = MemberNotificationSetting.create(member, PushSettingGroup.SAFE_ZONE, false);
        given(memberUtil.getCurrentMember()).willReturn(member);
        given(members.findByIdForUpdate(1L)).willReturn(Optional.of(member));
        given(settings.findByMemberIdAndCategory(1L, PushSettingGroup.SAFE_ZONE))
                .willReturn(Optional.of(setting));

        // when
        NotificationSettingResponse result = service.updateNotificationSetting(
                new UpdateNotificationSettingRequest("SAFE_ZONE", true, 0L));

        // then
        assertThat(setting.isEnabled()).isTrue();
        assertThat(result.productPushEnabled()).isTrue();
        assertThat(result.policyRevision()).isEqualTo(1L);
    }

    @Test
    @DisplayName("낡은 revision으로 저장하면 설정이 바뀌지 않고 예외가 발생한다")
    void 낡은_revision으로_저장하면_충돌_예외가_발생한다() {
        // given
        Member member = member(MemberType.GUARDIAN);
        member.incrementNotificationPolicyRevision();
        given(memberUtil.getCurrentMember()).willReturn(member);
        given(members.findByIdForUpdate(1L)).willReturn(Optional.of(member));

        // when / then
        assertThatThrownBy(() -> service.updateNotificationSetting(
                new UpdateNotificationSettingRequest("GENERAL", false, 0L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOTIFICATION_POLICY_REVISION_CONFLICT);
        assertThat(member.getNotificationPolicyRevision()).isEqualTo(1L);
    }

    @Test
    @DisplayName("알 수 없는 그룹으로 저장하면 카테고리 예외가 발생한다")
    void 알_수_없는_그룹으로_저장하면_예외가_발생한다() {
        // when / then
        assertThatThrownBy(() -> service.updateNotificationSetting(
                new UpdateNotificationSettingRequest("UNKNOWN", true, 0L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_FCM_CATEGORY);
    }

    @Test
    @DisplayName("NONE 그룹으로 저장하면 카테고리 예외가 발생한다")
    void NONE_그룹으로_저장하면_예외가_발생한다() {
        // when / then
        assertThatThrownBy(() -> service.updateNotificationSetting(
                new UpdateNotificationSettingRequest("NONE", true, 0L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_FCM_CATEGORY);
    }

    @Test
    @DisplayName("시니어가 안전 그룹을 저장하면 카테고리 예외가 발생한다")
    void 시니어가_안전_그룹을_저장하면_예외가_발생한다() {
        // given
        Member member = member(MemberType.SENIOR);
        given(memberUtil.getCurrentMember()).willReturn(member);
        given(members.findByIdForUpdate(1L)).willReturn(Optional.of(member));

        // when / then
        assertThatThrownBy(() -> service.updateNotificationSetting(
                new UpdateNotificationSettingRequest("SAFETY", false, 0L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_FCM_CATEGORY);
    }

    @Test
    @DisplayName("시니어가 이전 일반 그룹을 저장하면 카테고리 예외가 발생한다")
    void 시니어가_GENERAL을_저장하면_예외가_발생한다() {
        // given
        Member member = member(MemberType.SENIOR);
        given(memberUtil.getCurrentMember()).willReturn(member);
        given(members.findByIdForUpdate(1L)).willReturn(Optional.of(member));

        // when / then
        assertThatThrownBy(() -> service.updateNotificationSetting(
                new UpdateNotificationSettingRequest("GENERAL", false, 0L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_FCM_CATEGORY);
    }

    @Test
    @DisplayName("보호자가 시니어 앨범 그룹을 저장하면 카테고리 예외가 발생한다")
    void 보호자가_시니어_그룹을_저장하면_예외가_발생한다() {
        // given
        Member member = member(MemberType.GUARDIAN);
        given(memberUtil.getCurrentMember()).willReturn(member);
        given(members.findByIdForUpdate(1L)).willReturn(Optional.of(member));

        // when / then
        assertThatThrownBy(() -> service.updateNotificationSetting(
                new UpdateNotificationSettingRequest("ALBUM", false, 0L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_FCM_CATEGORY);
    }

    private Member member(MemberType type) {
        Member member = Member.createMember(type, "회원", "01011112222");
        ReflectionTestUtils.setField(member, "id", 1L);
        return member;
    }
}
