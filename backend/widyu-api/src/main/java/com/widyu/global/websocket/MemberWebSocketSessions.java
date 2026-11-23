package com.widyu.global.websocket;

import com.widyu.auth.event.MemberWithdrawnEvent;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

/**
 * 회원별로 열린 STOMP 연결. 탈퇴한 회원의 연결을 커밋 뒤 닫아 위치, 심박, 센서 전송을 바로 멈춘다(LLD-0186).
 *
 * <p>REST는 요청마다, 핸드셰이크는 연결 때 활성 회원을 확인하지만 이미 열린 연결의 SEND 프레임은 확인하지 않는다.
 * SEND마다 DB를 읽는 대신 탈퇴 때 연결을 닫는다. 다시 연결하려면 일회용 WS 토큰을 REST로 받아야 하는데
 * 비활성 회원이라 받지 못한다.
 */
// ponytail: 인스턴스 메모리라 단일 인스턴스 전제다(ADR-0023). 여러 대로 늘리면 탈퇴 이벤트를 모든 인스턴스에 알린다
@Slf4j
@Component
public class MemberWebSocketSessions {

    private final Map<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();
    /**
     * 연결을 닫은 탈퇴 회원. 탈퇴 커밋 전에 핸드셰이크를 통과한 연결이 닫기 뒤에 등록되면 여기서 보고 바로 닫는다.
     * 등록은 「넣고 확인」, 닫기는 「표시하고 꺼냄」 순서라 어느 쪽이 먼저든 한쪽이 그 연결을 닫는다.
     */
    // ponytail: 탈퇴 회원 ID가 재시작 전까지 쌓인다. 탈퇴는 드물어 메모리가 작다
    private final Set<Long> withdrawn = ConcurrentHashMap.newKeySet();

    public void register(WebSocketSession session) {
        Long memberId = memberId(session);
        if (memberId == null) {
            return;
        }
        sessions.computeIfAbsent(memberId, id -> ConcurrentHashMap.newKeySet()).add(session);
        if (withdrawn.contains(memberId)) {
            closeAll(memberId);
        }
    }

    public void unregister(WebSocketSession session) {
        Long memberId = memberId(session);
        if (memberId == null) {
            return;
        }
        sessions.computeIfPresent(memberId, (id, set) -> {
            set.remove(session);
            if (set.isEmpty()) {
                return null;
            }
            return set;
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void closeAfterWithdrawal(MemberWithdrawnEvent event) {
        withdrawn.add(event.memberId());
        closeAll(event.memberId());
    }

    private void closeAll(Long memberId) {
        Set<WebSocketSession> open = sessions.remove(memberId);
        if (open == null) {
            return;
        }
        for (WebSocketSession session : open) {
            try {
                session.close(CloseStatus.POLICY_VIOLATION.withReason("member withdrawn"));
            } catch (Exception e) {
                log.warn("탈퇴 회원 WebSocket 연결 닫기 실패: memberId={}, errorType={}",
                        memberId, e.getClass().getSimpleName());
            }
        }
        log.info("탈퇴 회원 WebSocket 연결 닫음: memberId={}, sessions={}", memberId, open.size());
    }

    private static Long memberId(WebSocketSession session) {
        if (session.getAttributes().get("memberId") instanceof Long memberId) {
            return memberId;
        }
        return null;
    }
}
