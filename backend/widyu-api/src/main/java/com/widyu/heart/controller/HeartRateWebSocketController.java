package com.widyu.heart.controller;

import com.widyu.global.security.PrincipalDetails;
import com.widyu.heart.application.HeartRateService;
import com.widyu.heart.dto.request.HeartRateSendRequest;
import com.widyu.heart.dto.response.HeartRateStatusResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class HeartRateWebSocketController {

    private final HeartRateService heartRateService;

    /**
     * 시니어가 심박수 데이터를 전송하는 WebSocket 엔드포인트
     * 클라이언트는 /app/heart-rate/send 로 메시지 전송
     * 분석 결과는 /queue/heart-rate/result 로 수신
     */
    @MessageMapping("/heart-rate/send")
    @SendToUser("/queue/heart-rate/result")
    public HeartRateStatusResponse sendHeartRates(
            @Valid @Payload HeartRateSendRequest request,
            @AuthenticationPrincipal PrincipalDetails principal
    ) {
        Long memberId = Long.parseLong(principal.getUsername());
        log.info("심박수 데이터 수신 (WebSocket) - memberId: {}, 데이터 수: {}", memberId, request.heartRates().size());

        return heartRateService.processHeartRates(memberId, request);
    }
}
