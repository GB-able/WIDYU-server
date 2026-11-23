package com.widyu.global.websocket;

import com.widyu.auth.event.MemberWithdrawnEvent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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

    // 등록, 해제, 탈퇴 처리를 한 잠금으로 줄 세운다. 탈퇴는 드물고 등록은 연결마다 한 번이라 경합이 작다.
    private final Map<Long, Set<WebSocketSession>> sessions = new HashMap<>();
    /** 연결을 닫은 탈퇴 회원. 탈퇴 커밋 전에 핸드셰이크를 통과한 연결이 그 뒤에 등록되면 바로 닫는다. */
    // ponytail: 탈퇴 회원 ID가 재시작 전까지 쌓인다. 탈퇴는 드물어 메모리가 작다
    private final Set<Long> withdrawn = new HashSet<>();

    public void register(WebSocketSession session) {
        Long memberId = memberId(session);
        if (memberId == null) {
            return;
        }
        synchronized (this) {
            if (!withdrawn.contains(memberId)) {
                sessions.computeIfAbsent(memberId, id -> new HashSet<>()).add(session);
                return;
            }
        }
        close(memberId, session);
    }

    public synchronized void unregister(WebSocketSession session) {
        Long memberId = memberId(session);
        if (memberId == null) {
            return;
        }
        Set<WebSocketSession> open = sessions.get(memberId);
        if (open == null) {
            return;
        }
        open.remove(session);
        if (open.isEmpty()) {
            sessions.remove(memberId);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void closeAfterWithdrawal(MemberWithdrawnEvent event) {
        Long memberId = event.memberId();
        Set<WebSocketSession> open;
        synchronized (this) {
            withdrawn.add(memberId);
            open = sessions.remove(memberId);
        }
        if (open == null) {
            return;
        }
        // 닫기는 잠금 밖에서 한다. 닫힘 콜백이 같은 잠금으로 해제를 부른다.
        for (WebSocketSession session : open) {
            close(memberId, session);
        }
        log.info("탈퇴 회원 WebSocket 연결 닫음: memberId={}, sessions={}", memberId, open.size());
    }

    private static void close(Long memberId, WebSocketSession session) {
        try {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("member withdrawn"));
        } catch (Exception e) {
            log.warn("탈퇴 회원 WebSocket 연결 닫기 실패: memberId={}, errorType={}",
                    memberId, e.getClass().getSimpleName());
        }
    }

    private static Long memberId(WebSocketSession session) {
        if (session.getAttributes().get("memberId") instanceof Long memberId) {
            return memberId;
        }
        return null;
    }
}
