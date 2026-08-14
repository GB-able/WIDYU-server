package com.widyu.fcm.event.album.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.widyu.album.repository.AlbumRepository;
import com.widyu.album.repository.AlbumViewRepository;
import com.widyu.fcm.FcmCategory;
import com.widyu.fcm.application.FcmService;
import com.widyu.fcm.dto.FcmSendDto;
import com.widyu.fcm.event.album.dto.AlbumCommentedEvent;
import com.widyu.fcm.event.album.dto.AlbumCreatedEvent;
import com.widyu.fcm.event.album.dto.AlbumLikedEvent;
import com.widyu.fcm.event.album.dto.AlbumUnlockedEvent;
import com.widyu.fcm.event.album.dto.AlbumViewedEvent;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.member.FamilyMembership;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.MemberRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@ExtendWith(MockitoExtension.class)
@DisplayName("AlbumNotificationListener 예외 처리 단위 테스트")
class AlbumNotificationListenerTest {

    @Mock private FcmService fcmService;
    @Mock private FamilyMembershipRepository familyMembershipRepository;
    @Mock private SeniorProfileRepository seniorProfileRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private AlbumViewRepository albumViewRepository;
    @Mock private AlbumRepository albumRepository;

    @InjectMocks
    private AlbumNotificationListener albumNotificationListener;

    @Test
    @DisplayName("앨범 이벤트 핸들러는 커밋 이후 fallback 실행으로 선언된다")
    void 앨범_이벤트_핸들러는_커밋_이후_fallback_실행으로_선언된다() throws NoSuchMethodException {
        // when & then
        assertAfterCommitHandler("handleAlbumCreated", AlbumCreatedEvent.class);
        assertAfterCommitHandler("handleAlbumViewed", AlbumViewedEvent.class);
        assertAfterCommitHandler("handleAlbumCommented", AlbumCommentedEvent.class);
        assertAfterCommitHandler("handleAlbumLiked", AlbumLikedEvent.class);
        assertAfterCommitHandler("handleAlbumUnlocked", AlbumUnlockedEvent.class);
    }

    @Test
    @DisplayName("앨범 생성 이벤트 작성자가 없으면 NOTIFICATION_MEMBER_NOT_FOUND 예외를 던지고 FCM을 전송하지 않는다")
    void 앨범_생성_작성자가_없으면_예외가_발생한다() {
        // given
        given(memberRepository.findById(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> albumNotificationListener.handleAlbumCreated(new AlbumCreatedEvent(10L, 1L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOTIFICATION_MEMBER_NOT_FOUND)
                .hasMessageContaining("회원을 찾을 수 없습니다.");
        then(fcmService).should(never()).sendMessageToUser(anyLong(), any(FcmSendDto.class));
    }

    @Test
    @DisplayName("앨범 생성이 완료되면 업로더에게 완료 알림을 전송한다")
    void 앨범_생성_완료_시_업로더에게_완료_알림을_전송한다() {
        // given
        Member author = member(1L);
        given(memberRepository.findById(1L)).willReturn(Optional.of(author));

        // when
        albumNotificationListener.handleAlbumCreated(new AlbumCreatedEvent(10L, 1L));

        // then
        then(fcmService).should().sendMessageToUser(eq(1L), argThat(dto ->
                dto.title().equals("앨범 업로드가 완료되었어요!")
                        && dto.content().equals("업로드한 앨범을 확인해보세요.")
                        && dto.fcmCategory() == FcmCategory.ALBUM
        ));
    }

    @Test
    @DisplayName("댓글 작성자가 없으면 NOTIFICATION_MEMBER_NOT_FOUND 예외를 던지고 FCM을 전송하지 않는다")
    void 댓글_작성자가_없으면_예외가_발생한다() {
        // given
        given(memberRepository.findById(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> albumNotificationListener.handleAlbumCommented(new AlbumCommentedEvent(10L, 1L, 2L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOTIFICATION_MEMBER_NOT_FOUND)
                .hasMessageContaining("회원을 찾을 수 없습니다.");
        then(fcmService).should(never()).sendMessageToUser(anyLong(), any(FcmSendDto.class));
    }

    @Test
    @DisplayName("잠금해제 이벤트 앨범이 없으면 ALBUM_NOT_FOUND 예외를 던지고 FCM을 전송하지 않는다")
    void 잠금해제_앨범이_없으면_예외가_발생한다() {
        // given
        Member parent = member(1L);
        given(memberRepository.findById(1L)).willReturn(Optional.of(parent));
        given(albumRepository.findByIdWithMember(10L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> albumNotificationListener.handleAlbumUnlocked(new AlbumUnlockedEvent(10L, 1L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALBUM_NOT_FOUND)
                .hasMessageContaining("앨범을 찾을 수 없습니다.");
        then(fcmService).should(never()).sendMessageToUser(anyLong(), any(FcmSendDto.class));
    }

    @Test
    @DisplayName("댓글 알림 FCM 발송이 실패하면 예외를 전파하지 않는다")
    void 댓글_알림_FCM_발송_실패_시_예외를_전파하지_않는다() {
        // given
        Member commenter = member(1L);
        Member albumAuthor = member(2L);
        given(memberRepository.findById(1L)).willReturn(Optional.of(commenter));
        given(memberRepository.findById(2L)).willReturn(Optional.of(albumAuthor));
        willThrow(new RuntimeException("fcm failed"))
                .given(fcmService)
                .sendMessageToUser(eq(2L), any(FcmSendDto.class));

        // when & then
        assertThatCode(() -> albumNotificationListener.handleAlbumCommented(new AlbumCommentedEvent(10L, 1L, 2L)))
                .doesNotThrowAnyException();
        then(fcmService).should().sendMessageToUser(eq(2L), any(FcmSendDto.class));
    }

    @Test
    @DisplayName("가족 대상 알림 중 한 수신자 FCM 발송이 실패해도 다음 수신자에게 발송을 시도한다")
    void 가족_대상_알림_일부_FCM_발송_실패_시_다음_수신자에게_발송을_시도한다() {
        // given
        Member author = member(1L);
        FamilyMembership firstMembership = membership(member(2L));
        FamilyMembership secondMembership = membership(member(3L));

        given(memberRepository.findById(1L)).willReturn(Optional.of(author));
        given(seniorProfileRepository.findFamilyIdByMemberId(1L)).willReturn(Optional.of(100L));
        given(familyMembershipRepository.findAllByFamilyIdWithGuardian(100L))
                .willReturn(List.of(firstMembership, secondMembership));
        willThrow(new RuntimeException("fcm failed"))
                .given(fcmService)
                .sendMessageToUser(eq(2L), any(FcmSendDto.class));

        // when & then
        assertThatCode(() -> albumNotificationListener.handleAlbumCreated(new AlbumCreatedEvent(10L, 1L)))
                .doesNotThrowAnyException();
        then(fcmService).should().sendMessageToUser(eq(2L), any(FcmSendDto.class));
        then(fcmService).should().sendMessageToUser(eq(3L), any(FcmSendDto.class));
    }

    @Test
    @DisplayName("본인 댓글 이벤트는 FCM을 전송하지 않는다")
    void 본인_댓글_이벤트는_FCM을_전송하지_않는다() {
        // given
        Member member = member(1L);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        // when
        albumNotificationListener.handleAlbumCommented(new AlbumCommentedEvent(10L, 1L, 1L));

        // then
        then(fcmService).should(never()).sendMessageToUser(anyLong(), any(FcmSendDto.class));
    }

    private void assertAfterCommitHandler(String methodName, Class<?> eventType) throws NoSuchMethodException {
        Method method = AlbumNotificationListener.class.getDeclaredMethod(methodName, eventType);
        TransactionalEventListener listener = method.getAnnotation(TransactionalEventListener.class);

        assertThat(listener).isNotNull();
        assertThat(listener.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
        assertThat(listener.fallbackExecution()).isTrue();
    }

    private FamilyMembership membership(Member guardian) {
        FamilyMembership membership = mock(FamilyMembership.class);
        given(membership.getGuardian()).willReturn(guardian);
        return membership;
    }

    private Member member(Long id) {
        Member member = Member.createMember(MemberType.GUARDIAN, "보호자", "01011112222");
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}
