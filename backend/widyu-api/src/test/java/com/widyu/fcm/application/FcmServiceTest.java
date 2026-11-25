package com.widyu.fcm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.widyu.album.repository.AlbumViewRepository;
import com.widyu.fcm.NotificationType;
import com.widyu.fcm.dto.FcmSendDto;
import com.widyu.fcm.dto.request.SendNotificationRequest;
import com.widyu.fcm.dto.response.ToastResDto;
import com.widyu.fcm.repository.FcmNotificationRepository;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.util.MemberUtil;
import com.widyu.member.Family;
import com.widyu.member.FamilyMembership;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.SeniorProfile;
import com.widyu.member.application.FamilyAccessService;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.SeniorProfileRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FcmServiceTest {

    @Mock private MemberUtil memberUtil;
    @Mock private FcmOutboxService outboxService;
    @Mock private FcmNotificationRepository fcmNotificationRepository;
    @Mock private FamilyAccessService familyAccessService;
    @Mock private AlbumViewRepository albumViewRepository;
    @Mock private FamilyMembershipRepository familyMembershipRepository;
    @Mock private SeniorProfileRepository seniorProfileRepository;
    @InjectMocks private FcmService service;

    private static final String NOT_FAMILY_MESSAGE = "접근 권한이 없습니다. - 같은 가족에게만 응원 메시지를 보낼 수 있습니다.";

    @Test
    @DisplayName("응원 메시지를 보내면 잠금화면 문구에는 원문을 넣지 않고 앱 안 문구에 미리보기를 싣는다")
    void 응원_메시지를_보내면_잠금화면에_원문을_넣지_않는다() {
        // given
        Member sender = member(1L, MemberType.GUARDIAN, "김보호");
        given(memberUtil.getCurrentMember()).willReturn(sender);
        given(familyAccessService.getFamilyMemberIds(sender)).willReturn(List.of(1L, 2L));
        String message = "고유원문" + "가".repeat(40);
        ArgumentCaptor<FcmSendDto> captor = ArgumentCaptor.forClass(FcmSendDto.class);

        // when
        service.sendNotificationToMember(new SendNotificationRequest(2L, message));

        // then
        then(outboxService).should().enqueue(eq(2L), captor.capture());
        FcmSendDto dto = captor.getValue();
        assertThat(dto.title()).isEqualTo("김보호 님이 응원메시지를 보냈어요!");
        assertThat(dto.content()).isEqualTo("앱에서 응원메시지를 확인해주세요.");
        assertThat(dto.title() + dto.content()).doesNotContain("고유원문").doesNotContain("가");
        String preview = "고유원문" + "가".repeat(35) + "…";
        assertThat(dto.centerBodyOrContent()).isEqualTo(preview);
        assertThat(dto.notificationType()).isEqualTo(NotificationType.CHEER_MESSAGE_RECEIVED);
        assertThat(dto.dataForEnqueue("cheer-event")).isEqualTo(Map.of(
                "eventId", "cheer-event",
                "notificationType", "CHEER_MESSAGE_RECEIVED",
                "deepLink", "",
                "actorDisplayName", "김보호",
                "inAppTitle", "김보호 님이 응원메시지를 보냈어요!",
                "inAppBody", preview));
    }

    @Test
    @DisplayName("이모지 50글자 응원 메시지를 보내면 UTF-16 길이와 상관없이 접수한다")
    void 이모지_50글자_응원_메시지를_접수한다() {
        // given
        Member sender = member(1L, MemberType.GUARDIAN, "김보호");
        given(memberUtil.getCurrentMember()).willReturn(sender);
        given(familyAccessService.getFamilyMemberIds(sender)).willReturn(List.of(1L, 2L));
        ArgumentCaptor<FcmSendDto> captor = ArgumentCaptor.forClass(FcmSendDto.class);

        // when
        service.sendNotificationToMember(new SendNotificationRequest(2L, "😀".repeat(50)));

        // then
        then(outboxService).should().enqueue(eq(2L), captor.capture());
        assertThat(captor.getValue().centerBodyOrContent()).isEqualTo("😀".repeat(39) + "…");
    }

    @Test
    @DisplayName("51글자 응원 메시지를 보내면 입력값 예외가 발생하고 알림을 접수하지 않는다")
    void 쉰한글자_응원_메시지를_보내면_예외가_발생한다() {
        // given
        Member sender = member(1L, MemberType.GUARDIAN, "김보호");
        given(memberUtil.getCurrentMember()).willReturn(sender);
        given(familyAccessService.getFamilyMemberIds(sender)).willReturn(List.of(1L, 2L));

        // when & then
        assertThatThrownBy(() -> service.sendNotificationToMember(new SendNotificationRequest(2L, "가".repeat(51))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        then(outboxService).should(never()).enqueue(any(), any());
    }

    @Test
    @DisplayName("정리하면 빈 값인 응원 메시지를 보내면 입력값 예외가 발생하고 알림을 접수하지 않는다")
    void 정리하면_빈_응원_메시지를_보내면_예외가_발생한다() {
        // given
        Member sender = member(1L, MemberType.GUARDIAN, "김보호");
        given(memberUtil.getCurrentMember()).willReturn(sender);
        given(familyAccessService.getFamilyMemberIds(sender)).willReturn(List.of(1L, 2L));

        // when & then
        assertThatThrownBy(() -> service.sendNotificationToMember(new SendNotificationRequest(2L, "\u3000\u200B")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        then(outboxService).should(never()).enqueue(any(), any());
    }

    @Test
    @DisplayName("다른 가족 회원에게 응원 메시지를 보내면 권한 예외가 발생하고 알림을 보내지 않는다")
    void 다른_가족_회원에게_응원_메시지를_보내면_권한_예외가_발생하고_알림을_보내지_않는다() {
        // given
        Member sender = member(1L, MemberType.GUARDIAN);
        given(memberUtil.getCurrentMember()).willReturn(sender);
        given(familyAccessService.getFamilyMemberIds(sender)).willReturn(List.of(1L, 2L));

        // when & then
        assertThatThrownBy(() -> service.sendNotificationToMember(new SendNotificationRequest(3L, "힘내세요")))
                .isInstanceOf(BusinessException.class)
                .hasMessage(NOT_FAMILY_MESSAGE)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
        then(outboxService).should(never()).enqueue(any(), any());
    }

    @Test
    @DisplayName("없는 회원에게 응원 메시지를 보내면 다른 가족과 같은 권한 예외가 발생한다")
    void 없는_회원에게_응원_메시지를_보내면_다른_가족과_같은_권한_예외가_발생한다() {
        // given
        Member sender = member(1L, MemberType.GUARDIAN);
        given(memberUtil.getCurrentMember()).willReturn(sender);
        given(familyAccessService.getFamilyMemberIds(sender)).willReturn(List.of(1L, 2L));

        // when & then
        assertThatThrownBy(() -> service.sendNotificationToMember(new SendNotificationRequest(999L, "힘내세요")))
                .isInstanceOf(BusinessException.class)
                .hasMessage(NOT_FAMILY_MESSAGE)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
        then(outboxService).should(never()).enqueue(any(), any());
    }

    @Test
    @DisplayName("어르신이 보호자 게시물을 모두 봤으면 토스트를 모두 확인 알림(A07)과 같은 문구로 돌려준다")
    void 어르신이_모두_봤으면_A07_문구_토스트를_돌려준다() {
        // given
        givenToastFamily(5L, 5L);

        // when
        ToastResDto toast = service.getToastNotification();

        // then
        assertThat(toast.title()).isEqualTo("김어른 님이 새 게시물을 모두 확인했어요!");
        assertThat(toast.content()).isEqualTo("새로운 소식을 올려주세요.");
        assertThat(toast.scheme()).isEmpty();
    }

    @Test
    @DisplayName("어르신이 볼 게시물이 조금 남았으면 토스트는 기존 남은 소식 문구를 그대로 쓴다")
    void 남은_게시물이_조금_있으면_기존_토스트_문구를_쓴다() {
        // given
        givenToastFamily(5L, 3L);

        // when
        ToastResDto toast = service.getToastNotification();

        // then
        assertThat(toast.title()).isEqualTo("김어른님께서 보실 소식이 2개밖에 남지 않았어요.");
        assertThat(toast.content()).isEqualTo("새로운 게시물을 올려주세요.");
    }

    private void givenToastFamily(long totalCount, long viewedCount) {
        Member guardian = member(1L, MemberType.GUARDIAN, "김보호");
        Member senior = member(2L, MemberType.SENIOR, "김어른");
        Family family = mock(Family.class);
        given(family.getId()).willReturn(10L);
        FamilyMembership membership = mock(FamilyMembership.class);
        given(membership.getFamily()).willReturn(family);
        SeniorProfile seniorProfile = mock(SeniorProfile.class);
        given(seniorProfile.getMember()).willReturn(senior);
        given(memberUtil.getCurrentMember()).willReturn(guardian);
        given(familyMembershipRepository.findByGuardianId(1L)).willReturn(Optional.of(membership));
        given(seniorProfileRepository.findAllByFamilyIdWithMember(10L)).willReturn(List.of(seniorProfile));
        given(albumViewRepository.countTotalAlbumsByParent(1L)).willReturn(totalCount);
        given(albumViewRepository.countViewedAlbumsByGuardianAndParent(2L, 1L)).willReturn(viewedCount);
    }

    private Member member(Long id, MemberType type) {
        return member(id, type, "회원");
    }

    private Member member(Long id, MemberType type, String name) {
        Member member = Member.createMember(type, name, "01011112222");
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}
