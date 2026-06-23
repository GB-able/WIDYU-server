package com.widyu.auth;

import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;
import org.springframework.data.redis.core.index.Indexed;

@Getter
@EqualsAndHashCode(of = "id")
@RedisHash(value = "wsConnectionToken")
public class WsConnectionToken {

    private static final long TTL_SECONDS = 30;

    @Id
    private String id;

    @Indexed
    private Long memberId;

    @TimeToLive
    private final long ttl;

    @Builder(access = AccessLevel.PRIVATE)
    private WsConnectionToken(String id, Long memberId, long ttl) {
        this.id = id;
        this.memberId = memberId;
        this.ttl = ttl;
    }

    public static WsConnectionToken create(Long memberId) {
        return WsConnectionToken.builder()
                .id(UUID.randomUUID().toString())
                .memberId(memberId)
                .ttl(TTL_SECONDS)
                .build();
    }
}
