package com.widyu.location.realtime.controller;

import com.widyu.global.security.PrincipalDetails;
import com.widyu.location.realtime.application.RealtimeLocationService;
import com.widyu.location.realtime.dto.LocationUpdateRequest;
import com.widyu.location.realtime.dto.LocationUpdateResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class RealtimeLocationController {

    private final RealtimeLocationService realtimeLocationService;

    /**
     * 시니어가 위치 업데이트를 전송하는 엔드포인트
     * 클라이언트는 /app/location/update 로 메시지 전송
     */
    @MessageMapping("/location/update")
    @SendToUser("/queue/location/ack")
    public LocationUpdateResponse updateLocation(
            @Valid @Payload LocationUpdateRequest request,
            @AuthenticationPrincipal PrincipalDetails principal
    ) {
        log.info("위치 업데이트 수신 - memberId: {}, seniorId: {}, lat: {}, lng: {}",
                 principal.getUsername(), request.seniorId(),
                 request.latitude(), request.longitude());

        // 시니어 본인 확인 (선택적 보안 강화)
        Long authenticatedMemberId = Long.parseLong(principal.getUsername());

        // 위치 저장 및 브로드캐스트
        LocationUpdateResponse response = realtimeLocationService.updateAndBroadcast(
                request, authenticatedMemberId
        );

        // 시니어에게 ACK 전송 (선택사항)
        return response;
    }
}
