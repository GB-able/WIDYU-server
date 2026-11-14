package com.widyu.global.properties;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String accessTokenSecret,
        Long accessTokenExpirationTime,
        String refreshTokenSecret,
        Long refreshTokenExpirationTime,
        String temporaryTokenSecret,
        // 액세스, 리프레시와 달리 1000을 곱하지 않고 밀리초로 그대로 쓴다. 환경변수 단위는 확인 필요 (LLD-0148)
        Long temporaryTokenExpirationTime
) {

    // HS256은 256비트 이상의 키를 요구한다
    private static final int HS256_MIN_SECRET_BYTES = 32;

    // 비밀값이 짧거나 서로 같으면 기동하지 않는다. 같으면 서명 키로 토큰 종류를 가를 수 없다 (LLD-0148)
    public JwtProperties {
        requireStrongSecret("jwt.access-token-secret", accessTokenSecret);
        requireStrongSecret("jwt.refresh-token-secret", refreshTokenSecret);
        requireStrongSecret("jwt.temporary-token-secret", temporaryTokenSecret);
        if (accessTokenSecret.equals(refreshTokenSecret)
                || accessTokenSecret.equals(temporaryTokenSecret)
                || refreshTokenSecret.equals(temporaryTokenSecret)) {
            throw new IllegalStateException("JWT 비밀값 세 개(access, refresh, temporary)는 서로 달라야 합니다.");
        }
    }

    private static void requireStrongSecret(String name, String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < HS256_MIN_SECRET_BYTES) {
            throw new IllegalStateException(name + "는 " + HS256_MIN_SECRET_BYTES + "바이트 이상이어야 합니다.");
        }
    }

    public Long accessTokenExpirationMilliTime() {
        return accessTokenExpirationTime * 1000;
    }

    public Long refreshTokenExpirationMilliTime() {
        return refreshTokenExpirationTime * 1000;
    }
}
