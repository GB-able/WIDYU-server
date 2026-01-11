package com.widyu.location;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.index.Indexed;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@RedisHash(value = "senior_location", timeToLive = 300) // 5분 TTL
public class SeniorLocation {

    @Id
    private Long seniorId;

    @Indexed
    private Double latitude;

    @Indexed
    private Double longitude;

    private LocalDateTime updatedAt;

    public static SeniorLocation of(Long seniorId, Double latitude, Double longitude) {
        return SeniorLocation.builder()
                .seniorId(seniorId)
                .latitude(latitude)
                .longitude(longitude)
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
