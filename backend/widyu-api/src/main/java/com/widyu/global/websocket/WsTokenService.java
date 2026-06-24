package com.widyu.global.websocket;

import com.widyu.global.util.MemberUtil;
import com.widyu.member.Member;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WsTokenService {

    private static final String KEY_PREFIX = "ws-token:";
    private static final Duration TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate redisTemplate;
    private final MemberUtil memberUtil;

    public String issueToken() {
        Member member = memberUtil.getCurrentMember();
        String tokenId = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(KEY_PREFIX + tokenId, String.valueOf(member.getId()), TTL);
        return tokenId;
    }

    public Long validateAndConsume(String tokenId) {
        String memberId = redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + tokenId);
        if (memberId == null) {
            return null;
        }
        return Long.parseLong(memberId);
    }
}
