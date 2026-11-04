package com.widyu.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.widyu.admin.dto.request.AdminFcmTestRequest;
import com.widyu.fcm.FcmCategory;
import com.widyu.fcm.MemberFcmToken;
import com.widyu.fcm.NotificationType;
import com.widyu.fcm.application.FcmService;
import com.widyu.fcm.dto.FcmSendDto;
import com.widyu.fcm.repository.MemberFcmTokenRepository;
import com.widyu.global.error.BusinessException;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.repository.MemberRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminFcmService 테스트 발송")
class AdminFcmServiceTest {

    @Mock private FcmService fcmService;
    @Mock private MemberRepository memberRepository;
    @Mock private MemberFcmTokenRepository memberFcmTokenRepository;
    @Mock private AdminAuditLogService adminAuditLogService;

    @InjectMocks private AdminFcmService adminFcmService;

    private void givenMemberWithToken(Long memberId) {
        Member member = Member.createMember(MemberType.SENIOR, "시니어", "01012345678");
        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));
        given(memberFcmTokenRepository.findAllByMemberIdAndActiveTrue(memberId))
                .willReturn(List.of(mock(MemberFcmToken.class)));
    }

    private FcmSendDto sentMessage(Long memberId) {
        ArgumentCaptor<FcmSendDto> captor = ArgumentCaptor.forClass(FcmSendDto.class);
        then(fcmService).should().sendTestMessageToUser(eq(memberId), captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("데이터 전용 종류를 지정하면 표시 문구 없이 그 종류로 보낸다")
    void 데이터_전용_종류를_지정하면_표시_문구_없이_그_종류로_보낸다() {
        // given
        givenMemberWithToken(28L);
        given(fcmService.sendTestMessageToUser(eq(28L), any())).willReturn(1);
        AdminFcmTestRequest request = new AdminFcmTestRequest(
                28L, null, null, null, NotificationType.WALK_DATA_SYNC, null, null);

        // when
        adminFcmService.sendTestNotification(request);

        // then
        FcmSendDto sent = sentMessage(28L);
        assertThat(sent.notificationType()).isEqualTo(NotificationType.WALK_DATA_SYNC);
        assertThat(sent.fcmCategory()).isEqualTo(FcmCategory.WALK);
        assertThat(sent.title()).isNull();
        assertThat(sent.content()).isNull();
    }

    @Test
    @DisplayName("표시 알림 종류를 제목·본문 없이 지정하면 문구표의 정해진 문구로 보낸다")
    void 표시_알림_종류를_제목_본문_없이_지정하면_정해진_문구로_보낸다() {
        // given
        givenMemberWithToken(28L);
        given(fcmService.sendTestMessageToUser(eq(28L), any())).willReturn(1);
        AdminFcmTestRequest request = new AdminFcmTestRequest(
                28L, null, null, null, NotificationType.MEDICATION_SCHEDULE_CHANGED, null,
                Map.of("보호자 이름", "방장"));

        // when
        adminFcmService.sendTestNotification(request);

        // then
        FcmSendDto sent = sentMessage(28L);
        assertThat(sent.notificationType()).isEqualTo(NotificationType.MEDICATION_SCHEDULE_CHANGED);
        assertThat(sent.title()).isEqualTo("방장 님이 약 알람을 변경했어요.");
        assertThat(sent.content()).isEqualTo("바뀐 약 알람은 오늘부터 적용돼요.");
    }

    @Test
    @DisplayName("문구 코드를 지정하면 그 코드의 문구로 보낸다")
    void 문구_코드를_지정하면_그_코드의_문구로_보낸다() {
        // given
        givenMemberWithToken(28L);
        given(fcmService.sendTestMessageToUser(eq(28L), any())).willReturn(1);
        AdminFcmTestRequest request = new AdminFcmTestRequest(
                28L, null, null, null, NotificationType.HEART_RATE_EMERGENCY, "S03", Map.of("시니어 이름", "박춘자"));

        // when
        adminFcmService.sendTestNotification(request);

        // then
        FcmSendDto sent = sentMessage(28L);
        assertThat(sent.notificationType()).isEqualTo(NotificationType.HEART_RATE_EMERGENCY);
        assertThat(sent.title()).isNotBlank();
    }

    @Test
    @DisplayName("문구에 필요한 값이 빠지면 빠진 값을 알려 주는 예외가 발생한다")
    void 문구에_필요한_값이_빠지면_예외가_발생한다() {
        // given
        givenMemberWithToken(28L);
        AdminFcmTestRequest request = new AdminFcmTestRequest(
                28L, null, null, null, NotificationType.WALK_GOAL_UNMET, null, null);

        // when · then
        assertThatThrownBy(() -> adminFcmService.sendTestNotification(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("실제 걸음 수");
    }

    @Test
    @DisplayName("종류 없이 제목·본문을 주면 기존처럼 그 문구로 보낸다")
    void 종류_없이_제목_본문을_주면_기존처럼_그_문구로_보낸다() {
        // given
        givenMemberWithToken(28L);
        given(fcmService.sendTestMessageToUser(eq(28L), any())).willReturn(1);
        AdminFcmTestRequest request = new AdminFcmTestRequest(28L, "제목", "본문", null, null, null, null);

        // when
        adminFcmService.sendTestNotification(request);

        // then
        FcmSendDto sent = sentMessage(28L);
        assertThat(sent.notificationType()).isNull();
        assertThat(sent.fcmCategory()).isEqualTo(FcmCategory.ETC);
        assertThat(sent.title()).isEqualTo("제목");
    }
}
