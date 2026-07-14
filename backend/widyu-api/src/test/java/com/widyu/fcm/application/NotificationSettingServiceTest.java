package com.widyu.fcm.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.widyu.fcm.dto.request.UpdateNotificationSettingRequest;
import com.widyu.fcm.repository.MemberNotificationSettingRepository;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.util.MemberUtil;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationSettingService 예외 처리 단위 테스트")
class NotificationSettingServiceTest {

    @Mock private MemberNotificationSettingRepository notificationSettingRepository;
    @Mock private MemberUtil memberUtil;

    @InjectMocks
    private NotificationSettingService notificationSettingService;

    @Test
    @DisplayName("알림 그룹이 null이면 INVALID_FCM_CATEGORY 예외를 던진다")
    void 알림_그룹이_null이면_예외가_발생한다() {
        // given
        given(memberUtil.getCurrentMember()).willReturn(member());

        // when & then
        assertThatThrownBy(() -> notificationSettingService.updateNotificationSetting(
                new UpdateNotificationSettingRequest(null, true)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_FCM_CATEGORY)
                .hasMessageContaining("유효하지 않은 알림 카테고리입니다.");
    }

    @Test
    @DisplayName("존재하지 않는 알림 그룹이면 INVALID_FCM_CATEGORY 예외를 던진다")
    void 존재하지_않는_알림_그룹이면_예외가_발생한다() {
        // given
        given(memberUtil.getCurrentMember()).willReturn(member());

        // when & then
        assertThatThrownBy(() -> notificationSettingService.updateNotificationSetting(
                new UpdateNotificationSettingRequest("UNKNOWN", true)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_FCM_CATEGORY)
                .hasMessageContaining("유효하지 않은 알림 카테고리입니다.");
    }

    private Member member() {
        Member member = Member.createMember(MemberType.GUARDIAN, "보호자", "01011112222");
        ReflectionTestUtils.setField(member, "id", 1L);
        return member;
    }
}
