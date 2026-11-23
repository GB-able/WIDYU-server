package com.widyu.fcm.event.member.listener;

import com.widyu.auth.event.SeniorWithdrawnEvent;
import com.widyu.fcm.NotificationType;
import com.widyu.fcm.application.FcmOutboxService;
import com.widyu.fcm.dto.FcmSendDto;
import com.widyu.fcm.dto.NotificationCopy;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 어르신이 탈퇴하면 가족 보호자 모두에게 안부 알림이 멈췄다고 알린다(D01, LLD-0186).
 * 탈퇴 트랜잭션 안에서 접수해 탈퇴가 롤백되면 알림도 남지 않는다.
 */
@Component
@RequiredArgsConstructor
public class SeniorWithdrawnNotificationListener {

    private final FcmOutboxService outboxService;

    @EventListener
    public void onSeniorWithdrawn(SeniorWithdrawnEvent event) {
        NotificationType type = NotificationType.SENIOR_WITHDRAWN;
        NotificationCopy copy = NotificationCopy.of(type, "D01",
                Map.of("시니어 이름", Objects.requireNonNullElse(event.seniorName(), "")));
        // 가족 연결을 이미 끊었으므로 같은 가족 확인(relatedMemberId)을 싣지 않는다.
        FcmSendDto message = FcmSendDto.of(type, copy, null, null, null, null, null);
        for (Long guardianId : event.guardianIds()) {
            outboxService.enqueue(guardianId, message);
        }
    }
}
