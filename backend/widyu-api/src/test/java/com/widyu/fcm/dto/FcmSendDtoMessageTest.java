package com.widyu.fcm.dto;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.widyu.fcm.NotificationType;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.member.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("가족 메시지 알림 본문 길이 검사")
class FcmSendDtoMessageTest {

    @Test
    @DisplayName("50자 이내지만 NFC 정규화 뒤 알림센터 본문이 255 코드포인트를 넘으면 400을 던진다")
    void NFC_정규화_뒤_본문이_255_코드포인트를_넘으면_예외가_발생한다() {
        // given: U+0344는 NFC에서 두 코드포인트로 풀린다. 결합 문자뿐이라 화면에서는 한 글자다(#1103 리뷰)
        Member sender = mock(Member.class);
        given(sender.getName()).willReturn("보호자");
        String message = "가" + "\u0344".repeat(128);

        // when & then
        assertThatThrownBy(() -> FcmSendDto.ofMessage(NotificationType.CHEER_MESSAGE_RECEIVED, sender, message))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE)
                .hasMessageContaining("쓸 수 없는 글자");
    }
}
