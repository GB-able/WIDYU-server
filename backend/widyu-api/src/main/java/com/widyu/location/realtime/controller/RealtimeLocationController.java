package com.widyu.location.realtime.controller;

import com.widyu.global.security.PrincipalDetails;
import com.widyu.location.realtime.application.LocationIngestService;
import com.widyu.location.realtime.dto.LocationAckResponse;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class RealtimeLocationController {

    private final LocationIngestService locationIngestService;

    /**
     * 시니어가 위치 업데이트를 전송하는 엔드포인트
     * 클라이언트는 /app/location/update 로 메시지 전송
     *
     * <p>본문을 원문 바이트로 받아 서비스가 구형과 v2를 가려 파싱, 검증한다. 위치 원본은 저장하지 않는다(LLD-0143).
     * ACK 결과는 실시간 처리 결과({@code APPLIED}, {@code SKIPPED}, {@code REJECTED})다.
     */
    @MessageMapping("/location/update")
    @SendToUser(destinations = "/queue/location/ack", broadcast = false)
    public LocationAckResponse updateLocation(
            Message<byte[]> message,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        Long authenticatedMemberId = resolveMemberId(headerAccessor.getUser());
        log.info("위치 업데이트 수신 - authenticatedMemberId: {}", authenticatedMemberId);

        return locationIngestService.receive(message.getPayload(), authenticatedMemberId);
    }

    /**
     * STOMP 요청의 회원은 CONNECT가 핸드셰이크 회원으로 등록한 Principal로만 식별한다(LLD-0088).
     */
    private Long resolveMemberId(Principal user) {
        if (user instanceof Authentication authentication
                && authentication.getPrincipal() instanceof PrincipalDetails principal) {
            return principal.getMemberId();
        }
        throw new IllegalStateException("인증 정보가 없습니다. WebSocket 연결 시 유효한 JWT 토큰이 필요합니다.");
    }
}
