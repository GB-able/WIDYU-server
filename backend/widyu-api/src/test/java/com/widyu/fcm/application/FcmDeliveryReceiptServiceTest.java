package com.widyu.fcm.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.widyu.decision.repository.DecisionRecordRepository;
import com.widyu.fcm.FcmOutbox;
import com.widyu.fcm.NotificationType;
import com.widyu.fcm.repository.FcmOutboxRepository;
import com.widyu.global.error.BusinessException;
import com.widyu.global.util.MemberUtil;
import com.widyu.member.Member;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("긴급 알림 폰 도착 신호")
class FcmDeliveryReceiptServiceTest {

    private static final long GUARDIAN_ID = 31L;
    private static final long DELIVERY_ID = 4072L;

    @Mock private MemberUtil memberUtil;
    @Mock private FcmOutboxRepository outbox;
    @Mock private DecisionRecordRepository decisions;
    @InjectMocks private FcmDeliveryReceiptService service;

    @Test
    @DisplayName("본인 긴급 알림의 첫 도착 신호를 받으면 판정 기록에도 첫 폰 도착 시각을 남긴다")
    void 본인_긴급_알림의_첫_도착_신호를_받으면_판정_기록에도_첫_폰_도착_시각을_남긴다() {
        // given
        signedIn();
        given(outbox.findByIdAndRecipientMember_Id(DELIVERY_ID, GUARDIAN_ID))
                .willReturn(Optional.of(row(NotificationType.HEART_RATE_EMERGENCY, "dec-01")));
        given(outbox.markDeviceReceivedIfFirst(eq(DELIVERY_ID), any(), eq(1_763_548_800_123L))).willReturn(1);

        // when
        service.recordReceived(DELIVERY_ID, 1_763_548_800_123L);

        // then
        then(decisions).should().markDeviceReceivedIfFirst(eq("dec-01"), anyLong());
    }

    @Test
    @DisplayName("이미 도착을 기록한 전송 건이면 판정 기록을 다시 건드리지 않는다")
    void 이미_도착을_기록한_전송_건이면_판정_기록을_다시_건드리지_않는다() {
        // given
        signedIn();
        given(outbox.findByIdAndRecipientMember_Id(DELIVERY_ID, GUARDIAN_ID))
                .willReturn(Optional.of(row(NotificationType.HEART_RATE_EMERGENCY, "dec-01")));
        given(outbox.markDeviceReceivedIfFirst(eq(DELIVERY_ID), any(), isNull())).willReturn(0);

        // when
        service.recordReceived(DELIVERY_ID, null);

        // then
        then(decisions).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("긴급이 아닌 알림의 도착 신호를 보내면 예외가 발생한다")
    void 긴급이_아닌_알림의_도착_신호를_보내면_예외가_발생한다() {
        // given
        signedIn();
        given(outbox.findByIdAndRecipientMember_Id(DELIVERY_ID, GUARDIAN_ID))
                .willReturn(Optional.of(row(NotificationType.ALBUM_CREATED, null)));

        // when, then
        assertThatThrownBy(() -> service.recordReceived(DELIVERY_ID, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("긴급으로 깨우는 데이터 신호의 도착 신호를 보내면 예외가 발생한다")
    void 긴급으로_깨우는_데이터_신호의_도착_신호를_보내면_예외가_발생한다() {
        // given
        signedIn();
        given(outbox.findByIdAndRecipientMember_Id(DELIVERY_ID, GUARDIAN_ID))
                .willReturn(Optional.of(row(NotificationType.WALK_DATA_SYNC, null)));

        // when, then
        assertThatThrownBy(() -> service.recordReceived(DELIVERY_ID, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("본인에게 간 전송 건이 아니면 예외가 발생한다")
    void 본인에게_간_전송_건이_아니면_예외가_발생한다() {
        // given
        signedIn();
        given(outbox.findByIdAndRecipientMember_Id(DELIVERY_ID, GUARDIAN_ID)).willReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> service.recordReceived(DELIVERY_ID, null))
                .isInstanceOf(BusinessException.class);
    }

    private void signedIn() {
        Member guardian = mock(Member.class);
        given(guardian.getId()).willReturn(GUARDIAN_ID);
        given(memberUtil.getCurrentMember()).willReturn(guardian);
    }

    private FcmOutbox row(NotificationType type, String decisionId) {
        return FcmOutbox.builder().id(DELIVERY_ID).notificationType(type).decisionId(decisionId).build();
    }
}
