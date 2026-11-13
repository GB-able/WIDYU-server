package com.widyu.location;

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
@RedisHash(value = SeniorLocation.KEYSPACE, timeToLive = SeniorLocation.TTL_SECONDS)
public class SeniorLocation {

    /** Redis 키 앞부분. 값은 {@code senior_location:{seniorId}} 해시에 들어간다. */
    public static final String KEYSPACE = "senior_location";

    /** 최신 위치를 남겨 두는 시간(5분). */
    public static final long TTL_SECONDS = 300;

    @Id
    private Long seniorId;

    private Double latitude;

    private Double longitude;

    private LocalDateTime updatedAt;

    /** 앱이 이 위치를 잰 시각(epoch ms). 잰 시각을 보내지 않는 옛 형식이면 null이다. */
    private Long measuredAtMs;

    public static SeniorLocation of(Long seniorId, Double latitude, Double longitude) {
        return of(seniorId, latitude, longitude, null);
    }

    public static SeniorLocation of(Long seniorId, Double latitude, Double longitude, Long measuredAtMs) {
        return SeniorLocation.builder()
                .seniorId(seniorId)
                .latitude(latitude)
                .longitude(longitude)
                .updatedAt(LocalDateTime.now())
                .measuredAtMs(measuredAtMs)
                .build();
    }
}
