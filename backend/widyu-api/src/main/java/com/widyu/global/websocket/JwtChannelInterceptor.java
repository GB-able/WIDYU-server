package com.widyu.global.websocket;

import static com.widyu.global.constant.SecurityConstant.TOKEN_PREFIX;

import com.widyu.auth.dto.AccessTokenDto;
import com.widyu.global.error.BusinessException;
import com.widyu.global.security.JwtTokenProvider;
import com.widyu.global.security.PrincipalDetails;
import com.widyu.member.application.FamilyAccessService;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    private static final Pattern LOCATION_TOPIC = Pattern.compile("^/topic/location/(\\d+)$");
    private static final Pattern HEART_RATE_TOPIC = Pattern.compile("^/topic/heart-rate/(\\d+)$");

    private final JwtTokenProvider jwtTokenProvider;
    private final FamilyAccessService familyAccessService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            return handleConnect(message, accessor);
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            return handleSubscribe(message, accessor);
        }

        return message;
    }

    private Message<?> handleConnect(Message<?> message, StompHeaderAccessor accessor) {
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

        return message;
    }

    private Message<?> handleSubscribe(Message<?> message, StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            return message;
        }

        Long targetMemberId = extractProtectedMemberId(destination);
        if (targetMemberId == null) {
            return message;
        }

        Long subscriberId = resolveSubscriberId(accessor);
        if (subscriberId == null) {
            log.warn("WebSocket SUBSCRIBE 인가 실패 - 인증 정보 없음, destination: {}", destination);
            return null;
        }

        try {
            familyAccessService.verifyFamilyAccess(subscriberId, targetMemberId);
            log.info("WebSocket SUBSCRIBE 인가 성공 - subscriberId: {}, destination: {}", subscriberId, destination);
        } catch (BusinessException e) {
            log.warn("WebSocket SUBSCRIBE 인가 거부 - subscriberId: {}, destination: {}", subscriberId, destination);
            return null;
        }

        return message;
    }

    private Long extractProtectedMemberId(String destination) {
        Matcher locationMatcher = LOCATION_TOPIC.matcher(destination);
        if (locationMatcher.matches()) {
            return Long.parseLong(locationMatcher.group(1));
        }

        Matcher heartMatcher = HEART_RATE_TOPIC.matcher(destination);
        if (heartMatcher.matches()) {
            return Long.parseLong(heartMatcher.group(1));
        }

        return null;
    }

    private Long resolveSubscriberId(StompHeaderAccessor accessor) {
        java.security.Principal user = accessor.getUser();
        if (user instanceof UsernamePasswordAuthenticationToken auth
                && auth.getPrincipal() instanceof PrincipalDetails principal) {
            return principal.getMemberId();
        }

        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes != null && sessionAttributes.get("memberId") instanceof Long memberId) {
            return memberId;
        }

        return null;
    }
}
