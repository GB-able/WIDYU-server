package com.widyu.location.realtime.application;

import com.widyu.fcm.event.safezone.dto.SafeZoneEnterEvent;
import com.widyu.fcm.event.safezone.dto.SafeZoneExitEvent;
import com.widyu.location.stay.SafeZoneTransition;
import com.widyu.location.stay.SeniorStayState;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * 안전구역 이탈, 복귀 알림을 체류 상태 행의 30분 묶음으로 정한다(LLD-0141, ADR-0039).
 *
 * <p>상태 변경과 알림 이벤트 발행은 위치 처리 DB 트랜잭션 안에서 일어난다. 이벤트를 받은 리스너가 같은 트랜잭션에
 * 알림 Outbox를 넣으므로, 상태와 알림이 함께 커밋되거나 함께 롤백된다. 예전 Redis 표식(SETNX, Lua)과 롤백 보정은 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SafeZoneAlertService {

    /** 이탈 알림 뒤 같은 회원의 이탈, 복귀 알림을 묶는 시간. 예전 Redis 표식 TTL(1800초)과 같다. */
    static final Duration ALERT_WINDOW = Duration.ofMinutes(30);

    private final ApplicationEventPublisher eventPublisher;

    public void handleSafeZoneTransition(SeniorStayState state, String previousLocationType,
                                         String currentLocationType, LocalDateTime now) {
        SafeZoneTransition transition =
                state.applySafeZoneTransition(previousLocationType, currentLocationType, now, ALERT_WINDOW);
        if (transition == SafeZoneTransition.EXIT_ALERT) {
            eventPublisher.publishEvent(new SafeZoneExitEvent(state.getMemberId()));
            log.info("안심구역 이탈 소식 이벤트 발행: memberId={}", state.getMemberId());
            return;
        }
        if (transition == SafeZoneTransition.RETURN_ALERT) {
            eventPublisher.publishEvent(new SafeZoneEnterEvent(state.getMemberId()));
            return;
        }
        if (previousLocationType != null && currentLocationType == null) {
            log.debug("안심구역 이탈 알림 묶음 유지: memberId={}", state.getMemberId());
        }
    }
}
