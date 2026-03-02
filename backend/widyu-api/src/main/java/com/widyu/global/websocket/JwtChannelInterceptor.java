package com.widyu.global.websocket;

import static com.widyu.global.constant.SecurityConstant.TOKEN_PREFIX;

import com.widyu.auth.dto.AccessTokenDto;
import com.widyu.global.security.JwtTokenProvider;
import com.widyu.global.security.PrincipalDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    @Override // STOMP CONNECT 메시지 전송 jwt 토큰 검사
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader != null && authHeader.startsWith(TOKEN_PREFIX)) {
                String token = authHeader.replace(TOKEN_PREFIX, "");
                AccessTokenDto accessTokenDto = jwtTokenProvider.retrieveAccessToken(token);

                if (accessTokenDto != null && accessTokenDto.memberId() != null) {
                    PrincipalDetails principal = new PrincipalDetails(
                            accessTokenDto.memberId(),
                            accessTokenDto.memberRole()
                    );
                    Authentication auth = new UsernamePasswordAuthenticationToken(
                            principal, null, principal.getAuthorities()
                    );
                    accessor.setUser(auth);
                    log.info("WebSocket CONNECT 인증 성공 - memberId: {}", accessTokenDto.memberId());
                }
            }
        }

        return message;
    }
}
