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

    /**
     * 최신 위치 비교에 쓰는 순서 시각(epoch ms) = {@code min(ts_ms, 서버 수신 시각)}(LLD-0140). 화면에 보일 측정 시각이 아니다.
     * 잰 시각을 보내지 않는 옛 형식이면 null이다.
     */
    private Long measuredAtMs;

    /** 앱이 보낸 측정 시각(ts_ms) 그대로. 기기 시계가 앞서면 서버보다 늦을 수 있다. 이 필드가 생기기 전 값이면 null(LLD-0141). */
    private Long deviceMeasuredAtMs;

    /** 서버가 이 위치를 받은 시각(epoch ms). 이 필드가 생기기 전 값이면 null(LLD-0141). */
    private Long receivedAtMs;

    public static SeniorLocation of(Long seniorId, Double latitude, Double longitude) {
        return of(seniorId, latitude, longitude, null);
    }

    public static SeniorLocation of(Long seniorId, Double latitude, Double longitude, Long measuredAtMs) {
        return of(seniorId, latitude, longitude, measuredAtMs, measuredAtMs, null);
    }

    public static SeniorLocation of(Long seniorId, Double latitude, Double longitude, Long measuredAtMs,
                                    Long deviceMeasuredAtMs, Long receivedAtMs) {
        return SeniorLocation.builder()
                .seniorId(seniorId)
                .latitude(latitude)
                .longitude(longitude)
                .updatedAt(LocalDateTime.now())
                .measuredAtMs(measuredAtMs)
                .deviceMeasuredAtMs(deviceMeasuredAtMs)
                .receivedAtMs(receivedAtMs)
                .build();
    }

    /**
     * 보호자에게 보일 측정 시각. 앱이 보낸 시각을 쓰고, 그 필드가 없던 예전 값이면 순서 시각을 쓴다(미래가 아니면 둘은 같다).
     */
    public Long displayMeasuredAtMs() {
        if (deviceMeasuredAtMs != null) {
            return deviceMeasuredAtMs;
        }
        return measuredAtMs;
    }
}
