package com.widyu.location.realtime.application;

import com.widyu.location.SeniorLocation;
import com.widyu.location.realtime.dto.LocationPoint;
import com.widyu.location.realtime.dto.LocationUpdateRequest;
import com.widyu.location.realtime.dto.LocationUpdateResponse;
import com.widyu.location.realtime.event.SeniorLocationUpdatedEvent;
import com.widyu.location.realtime.repository.SeniorLocationRepository;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * 판정이 커밋된 위치를 Redis 최신 위치, 이동 경로, 보호자 방송, 위치 이벤트로 내보낸다(LLD-0121, LLD-0142).
 * 트랜잭션을 열지 않는다. 방송 인가 조회는 아웃바운드 인터셉터의 읽기 트랜잭션이 따로 끝낸다(LLD-0140).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocationProjectionService {

    static final String TRAIL_KEY_PREFIX = "location:trail:";
    // 이동 경로 점 상한. 초당 1건으로 보내도 15분을 담는다. 위치마다 TTL을 다시 걸어 키가 만료되지 않으므로
    // 자르지 않으면 리스트가 끝없이 커진다(LLD-0098).
    static final long TRAIL_MAX_POINTS = 900;
    private static final long TRAIL_TTL_SECONDS = 900; // 15분

    private final SeniorLocationRepository seniorLocationRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final ApplicationEventPublisher eventPublisher;

    /** 지금 Redis 최신 위치의 순서 시각. 최신 위치가 없거나 시각이 없는 옛 형식이면 null. */
    public Long latestOrderAt(Long memberId) {
        return seniorLocationRepository.findById(memberId)
                .map(SeniorLocation::getMeasuredAtMs)
                .orElse(null);
    }

    /**
     * 단계마다 실패 정책이 다르다. 최신 위치 저장은 필수라 실패하면 던진다. 저장되지 않았으니 같은 측정 시각의 재전송이
     * 실시간 후보로 다시 평가돼 복구된다. 그 뒤 단계(경로, 방송, 이벤트)는 최신 위치가 이미 저장돼 같은 시각의 재전송이
     * 걸러지므로, 오류를 돌려줘도 복구되지 않는다. 그래서 실패를 남기고 다음 단계를 계속한다.
     */
    public void project(LocationUpdateRequest request, LocationUpdateResponse response,
                        Long orderAtMs, long receivedAtMs) {
        Long memberId = request.memberId();
        Double latitude = request.resolvedLatitude();
        Double longitude = request.resolvedLongitude();
        seniorLocationRepository.save(
                SeniorLocation.of(memberId, latitude, longitude, orderAtMs, request.tsMs(), receivedAtMs));
        appendTrailQuietly(memberId, latitude, longitude, orderAtMs);
        broadcastQuietly(memberId, response);
        publishLocationUpdatedQuietly(memberId, latitude, longitude);
    }

    static String trailKey(Long memberId) {
        return TRAIL_KEY_PREFIX + memberId;
    }

    private void appendTrailQuietly(Long memberId, Double latitude, Double longitude, Long orderAtMs) {
        String trailKey = trailKey(memberId);
        try {
            Long listSize = redisTemplate.opsForList()
                    .leftPush(trailKey, LocationPoint.of(latitude, longitude, orderAtMs));
            redisTemplate.opsForList().trim(trailKey, 0, TRAIL_MAX_POINTS - 1);
            redisTemplate.expire(trailKey, TRAIL_TTL_SECONDS, TimeUnit.SECONDS);
            log.info("이동 경로 저장: memberId={}, pointCount={}", memberId, listSize);
        } catch (RuntimeException e) {
            log.warn("이동 경로 저장 실패: memberId={}, cause={}", memberId, e.getClass().getSimpleName());
        }
    }

    private void broadcastQuietly(Long memberId, LocationUpdateResponse response) {
        String destination = String.format("/topic/location/senior/%d", memberId);
        try {
            messagingTemplate.convertAndSend(destination, response);
            log.info("시니어 방으로 위치 브로드캐스트 완료 - memberId: {}, destination: {}", memberId, destination);
        } catch (RuntimeException e) {
            log.warn("위치 브로드캐스트 실패: memberId={}, cause={}", memberId, e.getClass().getSimpleName());
        }
    }

    private void publishLocationUpdatedQuietly(Long memberId, Double latitude, Double longitude) {
        try {
            eventPublisher.publishEvent(new SeniorLocationUpdatedEvent(memberId, latitude, longitude));
        } catch (RuntimeException e) {
            log.warn("위치 갱신 이벤트 발행 실패: memberId={}, cause={}", memberId, e.getClass().getSimpleName());
        }
    }
}
