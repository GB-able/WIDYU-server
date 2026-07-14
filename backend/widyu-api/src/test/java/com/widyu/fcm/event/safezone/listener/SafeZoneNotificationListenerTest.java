package com.widyu.fcm.event.safezone.listener;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.widyu.fcm.application.FcmService;
import com.widyu.fcm.dto.FcmSendDto;
import com.widyu.fcm.event.safezone.dto.SafeZoneExitEvent;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.MemberRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SafeZoneNotificationListener 예외 처리 단위 테스트")
class SafeZoneNotificationListenerTest {

    @Mock private FcmService fcmService;
    @Mock private MemberRepository memberRepository;
    @Mock private FamilyMembershipRepository familyMembershipRepository;

    @InjectMocks
    private SafeZoneNotificationListener safeZoneNotificationListener;

    @Test
    @DisplayName("안전구역 이탈 시니어 회원이 없으면 MEMBER_NOT_FOUND 예외를 던지고 FCM을 전송하지 않는다")
    void 안전구역_이탈_시니어_회원이_없으면_예외가_발생한다() {
        // given
        given(memberRepository.findById(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> safeZoneNotificationListener.handleSafeZoneExit(new SafeZoneExitEvent(1L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND)
                .hasMessageContaining("회원을 찾을 수 없습니다.");
        then(fcmService).should(never()).sendMessageToUser(anyLong(), any(FcmSendDto.class));
    }

    @Test
    @DisplayName("시니어 프로필이 없으면 FCM을 전송하지 않는다")
    void 시니어_프로필이_없으면_FCM을_전송하지_않는다() {
        // given
        Member senior = Member.createMember(MemberType.SENIOR, "부모님", "01011112222");
        given(memberRepository.findById(1L)).willReturn(Optional.of(senior));

        // when
        safeZoneNotificationListener.handleSafeZoneExit(new SafeZoneExitEvent(1L));

        // then
        then(fcmService).should(never()).sendMessageToUser(anyLong(), any(FcmSendDto.class));
    }
}
