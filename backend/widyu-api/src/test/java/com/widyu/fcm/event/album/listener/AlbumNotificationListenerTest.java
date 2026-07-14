package com.widyu.fcm.event.album.listener;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.widyu.album.Album;
import com.widyu.album.repository.AlbumRepository;
import com.widyu.album.repository.AlbumViewRepository;
import com.widyu.fcm.application.FcmService;
import com.widyu.fcm.dto.FcmSendDto;
import com.widyu.fcm.event.album.dto.AlbumCommentedEvent;
import com.widyu.fcm.event.album.dto.AlbumCreatedEvent;
import com.widyu.fcm.event.album.dto.AlbumUnlockedEvent;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.MemberRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
        given(albumRepository.findById(10L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> albumNotificationListener.handleAlbumUnlocked(new AlbumUnlockedEvent(10L, 1L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALBUM_NOT_FOUND)
                .hasMessageContaining("앨범을 찾을 수 없습니다.");
        then(fcmService).should(never()).sendMessageToUser(anyLong(), any(FcmSendDto.class));
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

    private Member member(Long id) {
        Member member = Member.createMember(MemberType.GUARDIAN, "보호자", "01011112222");
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}
