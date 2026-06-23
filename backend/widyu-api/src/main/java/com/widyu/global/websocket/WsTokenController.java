package com.widyu.global.websocket;

import com.widyu.global.response.ApiResponseTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ws")
public class WsTokenController {

    private final WsTokenService wsTokenService;

    @PostMapping("/token")
    public ApiResponseTemplate<String> issueWsToken() {
        return ApiResponseTemplate.ok()
                .code("WS_2001")
                .message("WebSocket 연결 토큰 발급 성공")
                .body(wsTokenService.issueToken());
    }
}
