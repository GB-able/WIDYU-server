package com.widyu.authtest.api;

import com.widyu.global.properties.NaverProperties;
import java.net.URI;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/test/naver")
public class NaverLoginController {
    private final NaverProperties naverProperties;

    @GetMapping("/login")
    public ResponseEntity<Void> redirectToNaver() {
        String url = "https://nid.naver.com/oauth2.0/authorize?response_type=code"
                + "&client_id=" + naverProperties.clientId()
                + "&redirect_uri=" + "http://localhost:8080/api/v1/auth/test/naver/callback"
                + "&state=RANDOM_STATE";

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(url))
                .build();
    }

    @GetMapping("/callback")
    public ResponseEntity<?> naverCallback(@RequestParam String code,
                                           @RequestParam String state) {
        log.info("네이버 callback: code={}, state={}", code, state);

        String tokenUrl = "https://nid.naver.com/oauth2.0/token"
                + "?grant_type=authorization_code"
                + "&client_id=" + naverProperties.clientId()
                + "&client_secret=" + naverProperties.clientSecret()
                + "&code=" + code
                + "&state=" + state;

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Map> tokenResponse = restTemplate.getForEntity(tokenUrl, Map.class);

        if (tokenResponse.getStatusCode() == HttpStatus.OK) {
            Map body = tokenResponse.getBody();
            log.info("네이버 토큰 발급 성공: {}", body);

            return ResponseEntity.ok(Map.of(
                    "access_token", body.get("access_token"),
                    "refresh_token", body.get("refresh_token")
            ));
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("네이버 토큰 발급 실패");
    }
}

