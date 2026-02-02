package com.widyu.heart;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@RedisHash(value = "heart_rate_result", timeToLive = 86400) // 24시간 TTL
public class HeartRateResult {

    @Id
    private Long memberId;

    private HeartRateStatus status;

    private Integer heartRate;

    private LocalDateTime measuredAt;

    public static HeartRateResult of(Long memberId, HeartRateStatus status, Integer heartRate, LocalDateTime measuredAt) {
        return HeartRateResult.builder()
                .memberId(memberId)
                .status(status)
                .heartRate(heartRate)
                .measuredAt(measuredAt)
                .build();
    }
}
