package com.widyu.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.widyu.admin.dto.request.AdminFcmTestRequest;
import com.widyu.fcm.FcmCategory;
import com.widyu.fcm.NotificationType;
import com.widyu.fcm.application.FcmService;
import com.widyu.fcm.application.FcmTestAttempt;
import com.widyu.admin.dto.response.AdminFcmTestResponse;
import com.widyu.fcm.DeliveryMode;
import com.widyu.fcm.dto.FcmSendDto;
import com.widyu.global.error.BusinessException;
import com.widyu.member.repository.MemberRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
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
    @Mock private AdminAuditLogService adminAuditLogService;

    @InjectMocks private AdminFcmService adminFcmService;

    private void givenMemberWithToken(Long memberId) {
        given(memberRepository.existsById(memberId)).willReturn(true);
    }

    private static List<FcmTestAttempt> sentToOneToken() {
        return List.of(new FcmTestAttempt(1L, FcmTestAttempt.Outcome.SENT, null, null));
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
        given(fcmService.sendTestMessageToUser(eq(28L), any())).willReturn(sentToOneToken());
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
        given(fcmService.sendTestMessageToUser(eq(28L), any())).willReturn(sentToOneToken());
        AdminFcmTestRequest request = new AdminFcmTestRequest(
                28L, null, null, null, NotificationType.MEDICATION_SCHEDULE_CHANGED, null,
                Map.of("보호자 이름", "방장"));

        // when
        adminFcmService.sendTestNotification(request);

        // then
        FcmSendDto sent = sentMessage(28L);
        assertThat(sent.notificationType()).isEqualTo(NotificationType.MEDICATION_SCHEDULE_CHANGED);
        assertThat(sent.title()).isEqualTo("약 알람이 변경되었습니다.");
        assertThat(sent.content()).isEqualTo("이미 인증한 포인트는 사라지지 않아요.");
    }

    @Test
    @DisplayName("문구 코드를 지정하면 그 코드의 문구로 보낸다")
    void 문구_코드를_지정하면_그_코드의_문구로_보낸다() {
        // given
        givenMemberWithToken(28L);
        given(fcmService.sendTestMessageToUser(eq(28L), any())).willReturn(sentToOneToken());
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
        given(fcmService.sendTestMessageToUser(eq(28L), any())).willReturn(sentToOneToken());
        AdminFcmTestRequest request = new AdminFcmTestRequest(
                28L, "", " ", null, NotificationType.MEDICATION_SCHEDULE_CHANGED, null, null);

        // when
        adminFcmService.sendTestNotification(request);

        // then
        assertThat(sentMessage(28L).title()).isEqualTo("약 알람이 변경되었습니다.");
    }

    @Test
    @DisplayName("건강 일정 알림의 기본 문구는 서버가 보내는 OS 문구라 일정명을 드러내지 않는다")
    void 건강_일정_알림의_기본_문구는_서버가_보내는_OS_문구다() {
        // given
        givenMemberWithToken(28L);
        given(fcmService.sendTestMessageToUser(eq(28L), any())).willReturn(sentToOneToken());
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
        given(fcmService.sendTestMessageToUser(eq(28L), any())).willReturn(sentToOneToken());
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
        given(fcmService.sendTestMessageToUser(eq(28L), any())).willReturn(sentToOneToken());
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
        given(fcmService.sendTestMessageToUser(eq(28L), any())).willReturn(sentToOneToken());
        AdminFcmTestRequest request = new AdminFcmTestRequest(28L, "제목", "본문", null, null, null, null);

        // when
        adminFcmService.sendTestNotification(request);

        // then
        FcmSendDto sent = sentMessage(28L);
        assertThat(sent.notificationType()).isNull();
        assertThat(sent.fcmCategory()).isEqualTo(FcmCategory.ETC);
        assertThat(sent.title()).isEqualTo("제목");
    }

    @Test
    @DisplayName("토큰별 결과를 받으면 성공·실패·미시도 수와 토큰을 뺀 전송 본문을 반환한다")
    void 토큰별_결과를_받으면_건수와_전송_본문을_반환한다() throws Exception {
        // given
        givenMemberWithToken(28L);
        JsonNode body = new ObjectMapper().readTree("{\"validateOnly\":false,\"message\":{\"data\":{}}}");
        given(fcmService.sendTestMessageToUser(eq(28L), any())).willReturn(List.of(
                new FcmTestAttempt(1L, FcmTestAttempt.Outcome.SENT, null, body),
                new FcmTestAttempt(2L, FcmTestAttempt.Outcome.FAILED, "HTTP_400 INVALID_ARGUMENT", body),
                new FcmTestAttempt(3L, FcmTestAttempt.Outcome.NOT_ATTEMPTED, FcmTestAttempt.TIME_BUDGET_EXHAUSTED, null)));
        AdminFcmTestRequest request = new AdminFcmTestRequest(
                28L, null, null, null, NotificationType.WALK_DATA_SYNC, null, null);

        // when
        AdminFcmTestResponse response = adminFcmService.sendTestNotification(request);

        // then
        assertThat(response.tokenCount()).isEqualTo(3);
        assertThat(response.sent()).isEqualTo(1);
        assertThat(response.failed()).isEqualTo(1);
        assertThat(response.notAttempted()).isEqualTo(1);
        assertThat(response.deliveryMode()).isEqualTo(DeliveryMode.DATA_ONLY);
        assertThat(response.attempts()).extracting(AdminFcmTestResponse.Attempt::detail)
                .containsExactly(null, "HTTP_400 INVALID_ARGUMENT", FcmTestAttempt.TIME_BUDGET_EXHAUSTED);
        assertThat(response.attempts().getFirst().payload()).isEqualTo(body);
        assertThat(response.notes()).anyMatch(note -> note.contains("무음 동기화"));
    }

    @Test
    @DisplayName("알림센터 전용 종류를 보내면 실제 업무에서는 푸시가 나가지 않는다고 안내한다")
    void 알림센터_전용_종류를_보내면_푸시가_나가지_않는다고_안내한다() {
        // given
        givenMemberWithToken(28L);
        given(fcmService.sendTestMessageToUser(eq(28L), any())).willReturn(sentToOneToken());
        AdminFcmTestRequest request = new AdminFcmTestRequest(
                28L, null, null, null, NotificationType.POINT_USED, null, null);

        // when
        AdminFcmTestResponse response = adminFcmService.sendTestNotification(request);

        // then
        assertThat(response.deliveryMode()).isEqualTo(DeliveryMode.CENTER_ONLY);
        assertThat(response.notes()).anyMatch(note -> note.contains("알림센터에만 저장하고 푸시를 보내지 않습니다"));
    }

    @Test
    @DisplayName("활성 토큰이 없으면 보내지 않았다고 안내하고 건수를 영으로 반환한다")
    void 활성_토큰이_없으면_보내지_않았다고_안내한다() {
        // given
        givenMemberWithToken(28L);
        given(fcmService.sendTestMessageToUser(eq(28L), any())).willReturn(List.of());
        AdminFcmTestRequest request = new AdminFcmTestRequest(28L, "제목", "본문", null, null, null, null);

        // when
        AdminFcmTestResponse response = adminFcmService.sendTestNotification(request);

        // then
        assertThat(response.tokenCount()).isZero();
        assertThat(response.attempts()).isEmpty();
        assertThat(response.notes().getFirst()).isEqualTo("등록된 활성 FCM 토큰이 없어 보내지 않았습니다.");
    }

    @Test
    @DisplayName("없는 회원에게 보내면 예외가 발생한다")
    void 없는_회원에게_보내면_예외가_발생한다() {
        // given
        given(memberRepository.existsById(99L)).willReturn(false);
        AdminFcmTestRequest request = new AdminFcmTestRequest(99L, "제목", "본문", null, null, null, null);

        // when · then
        assertThatThrownBy(() -> adminFcmService.sendTestNotification(request))
                .isInstanceOf(BusinessException.class);
    }
}
