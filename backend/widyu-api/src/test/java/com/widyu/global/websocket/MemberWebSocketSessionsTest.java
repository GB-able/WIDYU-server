package com.widyu.global.websocket;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.widyu.auth.event.MemberWithdrawnEvent;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

@DisplayName("회원별 WebSocket 연결 단위 테스트")
class MemberWebSocketSessionsTest {

    @Test
    @DisplayName("회원이 탈퇴하면 그 회원의 열린 연결만 닫는다")
    void 회원이_탈퇴하면_그_회원의_연결만_닫는다() throws Exception {
        // given
        MemberWebSocketSessions sessions = new MemberWebSocketSessions();
        WebSocketSession withdrawn = session(7L);
        WebSocketSession other = session(8L);
        sessions.register(withdrawn);
        sessions.register(other);

        // when
        sessions.closeAfterWithdrawal(new MemberWithdrawnEvent(7L));

        // then
        verify(withdrawn).close(any(CloseStatus.class));
        verify(other, never()).close(any(CloseStatus.class));
    }

    @Test
    @DisplayName("이미 닫혀 등록을 지운 연결은 탈퇴 때 다시 닫지 않는다")
    void 등록을_지운_연결은_다시_닫지_않는다() throws Exception {
        // given
        MemberWebSocketSessions sessions = new MemberWebSocketSessions();
        WebSocketSession closed = session(7L);
        sessions.register(closed);
        sessions.unregister(closed);

        // when
        sessions.closeAfterWithdrawal(new MemberWithdrawnEvent(7L));

        // then
        verify(closed, never()).close(any(CloseStatus.class));
    }

    private static WebSocketSession session(Long memberId) {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("memberId", memberId);
        given(session.getAttributes()).willReturn(attributes);
        return session;
    }
}
