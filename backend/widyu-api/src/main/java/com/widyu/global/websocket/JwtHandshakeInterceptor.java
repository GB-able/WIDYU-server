package com.widyu.global.websocket;

import static com.widyu.global.constant.SecurityConstant.TOKEN_PREFIX;

import com.widyu.auth.dto.AccessTokenDto;
import com.widyu.global.security.JwtTokenProvider;
import java.util.Map;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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
        if (authHeader == null || authHeader.isEmpty()) {
            String query = request.getURI().getQuery();
            if (query != null && query.contains("token=")) {
                String encodedToken = extractTokenFromQuery(query);
                if (encodedToken != null) {
                    try {
                        String decodedToken = URLDecoder.decode(encodedToken, StandardCharsets.UTF_8.name());
                        authHeader = TOKEN_PREFIX + decodedToken;
                    } catch (UnsupportedEncodingException e) {
                        log.error("Failed to decode token from query parameter", e);
                        // 토큰 디코딩 실패 시 연결을 거부합니다.
                        log.warn("WebSocket handshake 실패 - 토큰 디코딩 오류");
                        return false;
                    }
                }
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
