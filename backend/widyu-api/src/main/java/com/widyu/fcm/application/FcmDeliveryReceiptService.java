package com.widyu.fcm.application;

import com.widyu.decision.repository.DecisionRecordRepository;
import com.widyu.fcm.FcmOutbox;
import com.widyu.fcm.NotificationPriority;
import com.widyu.fcm.repository.FcmOutboxRepository;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.util.MemberUtil;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 긴급 알림이 폰에 실제로 닿은 시각을 받는다(LLD-0181).
 *
 * <p>FCM 200은 「구글이 받았다」일 뿐이라 앱이 받는 즉시 보내는 신호로 도착을 따로 잰다. 기준 시각은 이 서버가
 * 신호를 받은 때다. 기기 시계는 틀릴 수 있어 함께 받은 값은 참고로만 남긴다.
 */
@Service
@RequiredArgsConstructor
public class FcmDeliveryReceiptService {

    private final MemberUtil memberUtil;
    private final FcmOutboxRepository outbox;
    private final DecisionRecordRepository decisions;

    /**
     * 본인에게 간 긴급 알림이 아니면 존재하지 않는 것으로 답한다. 이미 받은 신호는 그대로 두고 성공으로 답한다.
     * FCM 응답을 기록하는 트랜잭션보다 신호가 먼저 올 수 있어 전송 상태는 보지 않는다.
     */
    @Transactional
    public void recordReceived(Long deliveryId, Long deviceReportedAtMs) {
        Long memberId = memberUtil.getCurrentMember().getId();
        FcmOutbox row = outbox.findByIdAndRecipientMember_Id(deliveryId, memberId)
                .filter(found -> found.getNotificationType() != null
                        && found.getNotificationType().priority() == NotificationPriority.CRITICAL)
                .orElseThrow(() -> new BusinessException(ErrorCode.FCM_NOTIFICATION_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now();
        if (outbox.markDeviceReceivedIfFirst(row.getId(), now, deviceReportedAtMs) == 0) {
            return;
        }
        if (row.getDecisionId() == null) {
            return;
        }
        decisions.markDeviceReceivedIfFirst(
                row.getDecisionId(), now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
    }
}
