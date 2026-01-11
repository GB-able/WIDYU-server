package com.widyu.global.websocket;

import static com.widyu.global.constant.SecurityConstant.TOKEN_PREFIX;

import com.widyu.auth.dto.AccessTokenDto;
import com.widyu.global.security.JwtTokenProvider;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    @Override // websocket jwt 토큰 검사
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) {

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        // Query parameter로도 받을 수 있도록
        if (authHeader == null) {
            String query = request.getURI().getQuery();
            if (query != null && query.contains("token=")) {
                authHeader = TOKEN_PREFIX + extractTokenFromQuery(query);
            }
        }

        if (authHeader != null && authHeader.startsWith(TOKEN_PREFIX)) {
            String token = authHeader.replace(TOKEN_PREFIX, "");
            AccessTokenDto accessTokenDto = jwtTokenProvider.retrieveAccessToken(token);

            if (accessTokenDto != null) {
                attributes.put("memberId", accessTokenDto.memberId());
                attributes.put("memberRole", accessTokenDto.memberRole());
                log.info("WebSocket handshake 성공 - memberId: {}", accessTokenDto.memberId());
                return true;
            }
        }

        log.warn("WebSocket handshake 실패 - 유효하지 않은 토큰");
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
        // 필요시 후처리
    }

    private String extractTokenFromQuery(String query) {
        for (String param : query.split("&")) {
            if (param.startsWith("token=")) {
                return param.substring(6);
            }
        }
        return null;
    }
}
