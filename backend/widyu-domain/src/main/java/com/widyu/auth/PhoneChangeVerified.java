package com.widyu.auth;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

@Getter
@NoArgsConstructor
@EqualsAndHashCode(of = "phoneNumber")
@RedisHash(value = "phoneChangeVerified")
public class PhoneChangeVerified {

    @Id
    private String phoneNumber;

    @TimeToLive
    private long ttl;

    @Builder
    public PhoneChangeVerified(final String phoneNumber, final long ttl) {
        this.phoneNumber = phoneNumber;
        this.ttl = ttl;
    }
}
