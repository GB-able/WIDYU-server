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
        assertThat(sent.title()).isEqualTo("보호자에게 알렸어요.");
        assertThat(sent.content()).isEqualTo("연락을 기다리는 동안 안전한 곳에서 잠시 기다려주세요.");
    }

    @Test
    @DisplayName("제목·본문을 빈 문자열로 주면 문구표의 정해진 문구로 보낸다")
    void 제목_본문을_빈_문자열로_주면_정해진_문구로_보낸다() {
        // given
        givenMemberWithToken(28L);
        given(fcmService.sendTestMessageToUser(eq(28L), any())).willReturn(1);
        AdminFcmTestRequest request = new AdminFcmTestRequest(
                28L, "", " ", null, NotificationType.MEDICATION_SCHEDULE_CHANGED, null, null);

        // when
        adminFcmService.sendTestNotification(request);

        // then
        assertThat(sentMessage(28L).title()).isEqualTo("관리자 님이 약 알람을 변경했어요.");
    }

    @Test
    @DisplayName("건강 일정 알림의 기본 문구는 서버가 보내는 OS 문구라 일정명을 드러내지 않는다")
    void 건강_일정_알림의_기본_문구는_서버가_보내는_OS_문구다() {
        // given
        givenMemberWithToken(28L);
        given(fcmService.sendTestMessageToUser(eq(28L), any())).willReturn(1);
        AdminFcmTestRequest request = new AdminFcmTestRequest(
                28L, null, null, null, NotificationType.HEALTH_SCHEDULE_UPCOMING, null, null);

        // when
        adminFcmService.sendTestNotification(request);

        // then
        FcmSendDto sent = sentMessage(28L);
        assertThat(sent.title()).isEqualTo("건강 일정이 곧 있어요.");
        assertThat(sent.content()).isEqualTo("앱에서 일정 시간과 내용을 확인해주세요.");
    }

    @Test
    @DisplayName("OS·앱 내 문구가 나뉜 알림은 OS 문구를 기본으로 보낸다")
    void OS와_앱_내_문구가_나뉜_알림은_OS_문구를_기본으로_보낸다() {
        // given
        givenMemberWithToken(28L);
        given(fcmService.sendTestMessageToUser(eq(28L), any())).willReturn(1);
        AdminFcmTestRequest request = new AdminFcmTestRequest(
                28L, null, null, null, NotificationType.HEART_MESSAGE_RECEIVED, null, null);

        // when
        adminFcmService.sendTestNotification(request);

        // then
        assertThat(sentMessage(28L).content()).isEqualTo("앱에서 메시지를 확인해주세요.");
    }

    @Test
    @DisplayName("문구 변수를 주지 않으면 모두 관리자로 채워 보낸다")
    void 문구_변수를_주지_않으면_모두_관리자로_채워_보낸다() {
        // given
        givenMemberWithToken(28L);
        given(fcmService.sendTestMessageToUser(eq(28L), any())).willReturn(1);
        AdminFcmTestRequest request = new AdminFcmTestRequest(
                28L, null, null, null, NotificationType.WALK_GOAL_UNMET, null, null);

        // when
        adminFcmService.sendTestNotification(request);

        // then
        assertThat(sentMessage(28L).title()).isEqualTo("오늘 관리자걸음 걸으셨어요.");
    }

    @Test
    @DisplayName("종류와 맞지 않는 문구 코드를 주면 예외가 발생한다")
    void 종류와_맞지_않는_문구_코드를_주면_예외가_발생한다() {
        // given
        givenMemberWithToken(28L);
        AdminFcmTestRequest request = new AdminFcmTestRequest(
                28L, null, null, null, NotificationType.WALK_GOAL_UNMET, "M05", null);

        // when · then
        assertThatThrownBy(() -> adminFcmService.sendTestNotification(request))
                .isInstanceOf(BusinessException.class);
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
