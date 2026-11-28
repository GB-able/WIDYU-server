package com.widyu.fcm.event.member.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.widyu.auth.event.SeniorWithdrawnEvent;
import com.widyu.fcm.NotificationType;
import com.widyu.fcm.PushSettingGroup;
import com.widyu.fcm.application.FcmOutboxService;
import com.widyu.fcm.dto.FcmSendDto;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("어르신 탈퇴 보호자 알림 단위 테스트")
class SeniorWithdrawnNotificationListenerTest {

    @Mock private FcmOutboxService outboxService;

    @InjectMocks
    private SeniorWithdrawnNotificationListener listener;

    @Test
    @DisplayName("어르신이 탈퇴하면 가족 보호자 모두에게 끌 수 없는 탈퇴 알림을 접수한다")
    void 어르신이_탈퇴하면_가족_보호자_모두에게_탈퇴_알림을_접수한다() {
        // given
        SeniorWithdrawnEvent event = SeniorWithdrawnEvent.of(7L, "김어르신", List.of(21L, 22L));
        ArgumentCaptor<FcmSendDto> message = ArgumentCaptor.forClass(FcmSendDto.class);

        // when
        listener.onSeniorWithdrawn(event);

        // then
        verify(outboxService).enqueue(eq(21L), message.capture());
        verify(outboxService).enqueue(eq(22L), message.capture());
        assertThat(message.getValue().title()).isEqualTo("김어르신 님이 위듀를 탈퇴하셔서 안부 알림이 멈췄어요");
        assertThat(message.getValue().content()).isNull();
        assertThat(message.getValue().notificationType()).isEqualTo(NotificationType.SENIOR_WITHDRAWN);
        assertThat(NotificationType.SENIOR_WITHDRAWN.settingGroup()).isEqualTo(PushSettingGroup.NONE);
        assertThat(NotificationType.SENIOR_WITHDRAWN.centerFilter()).isEqualTo("ETC");
    }

    @Test
    @DisplayName("어르신 이름이 20글자를 넘으면 이름 자리를 줄여 접수한다")
    void 어르신_이름이_20글자를_넘으면_줄여_접수한다() {
        // given
        SeniorWithdrawnEvent event = SeniorWithdrawnEvent.of(7L, "가".repeat(30), List.of(21L));
        ArgumentCaptor<FcmSendDto> message = ArgumentCaptor.forClass(FcmSendDto.class);

        // when
        listener.onSeniorWithdrawn(event);

        // then
        verify(outboxService).enqueue(eq(21L), message.capture());
        assertThat(message.getValue().title()).startsWith("가".repeat(19) + "…")
                .endsWith(" 님이 위듀를 탈퇴하셔서 안부 알림이 멈췄어요");
    }
}
