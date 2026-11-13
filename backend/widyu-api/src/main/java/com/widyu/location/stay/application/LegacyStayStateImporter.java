package com.widyu.location.stay.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.widyu.global.config.RedisConfig;
import com.widyu.location.realtime.dto.StayInfo;
import com.widyu.location.stay.SafeZoneAlertState;
import com.widyu.location.stay.SeniorStayState;
import com.widyu.location.stay.repository.SeniorStayStateRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 배포 때 한 번, 예전 Redis 체류 키({@code location:stay:{id}})와 알림 표식({@code safezone:alert:{id}})을 MySQL 체류 상태로
 * 옮긴다(LLD-0141 8절). DB에 행이 이미 있으면 건드리지 않고, Redis 키는 지우지 않는다(롤백 대비). 여러 번 돌아도 결과가 같다.
 *
 * <p>옮기지 않으면 배포 직후 첫 위치가 "이전 체류 없음"으로 판정돼 알림은 새로 생기지 않지만, 진행 중이던 30분 묶음을 잃어
 * 묶음 안 재이탈에 Z01이 다시 나가거나 복귀 Z02가 빠질 수 있다. Redis를 읽지 못하면 경고만 남기고 서버는 뜬다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "location.stay-state.import-legacy-redis", havingValue = "true", matchIfMissing = true)
public class LegacyStayStateImporter implements ApplicationRunner {

    static final String STAY_KEY_PREFIX = "location:stay:";
    static final String ALERT_KEY_PREFIX = "safezone:alert:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final SeniorStayStateRepository repository;
    private final PlatformTransactionManager transactionManager;
    private final ObjectMapper redisValueMapper = RedisConfig.redisObjectMapper();

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<String> keys = new ArrayList<>();
            try (Cursor<String> cursor = redisTemplate.scan(
                    ScanOptions.scanOptions().match(STAY_KEY_PREFIX + "*").count(500).build())) {
                cursor.forEachRemaining(keys::add);
            }
            Result result = importKeys(keys, LocalDateTime.now());
            log.info("예전 Redis 체류 상태 가져오기: keys={}, imported={}, existing={}, invalid={}",
                    keys.size(), result.imported(), result.existing(), result.invalid());
        } catch (RuntimeException e) {
            log.warn("예전 Redis 체류 상태 가져오기 실패, 없는 상태로 시작: cause={}", e.getClass().getSimpleName());
        }
    }

    Result importKeys(List<String> keys, LocalDateTime now) {
        int imported = 0;
        int existing = 0;
        int invalid = 0;
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        for (String key : keys) {
            SeniorStayState state = readLegacy(key, now);
            if (state == null) {
                invalid++;
                continue;
            }
            Boolean saved = transaction.execute(status -> {
                if (repository.existsById(state.getMemberId())) {
                    return false;
                }
                repository.save(state);
                return true;
            });
            if (Boolean.TRUE.equals(saved)) {
                imported++;
            } else {
                existing++;
            }
        }
        return new Result(imported, existing, invalid);
    }

    /** 좌표가 빠졌거나 읽을 수 없는 값은 옮기지 않는다(예전 판정도 이런 값은 지우고 체류 없음으로 봤다). */
    private SeniorStayState readLegacy(String key, LocalDateTime now) {
        try {
            Long memberId = Long.parseLong(key.substring(STAY_KEY_PREFIX.length()));
            Object raw = redisTemplate.opsForValue().get(key);
            if (raw == null) {
                return null;
            }
            StayInfo stay = redisValueMapper.convertValue(raw, StayInfo.class);
            if (stay.latitude() == null || stay.longitude() == null || stay.startTime() == null) {
                return null;
            }
            String alertKey = ALERT_KEY_PREFIX + memberId;
            SafeZoneAlertState alertState = alertState(redisTemplate.opsForValue().get(alertKey));
            LocalDateTime alertUntil = null;
            if (alertState != null) {
                Long ttlSeconds = redisTemplate.getExpire(alertKey, TimeUnit.SECONDS);
                if (ttlSeconds == null || ttlSeconds <= 0) {
                    alertState = null;
                } else {
                    alertUntil = now.plusSeconds(ttlSeconds);
                }
            }
            return SeniorStayState.imported(memberId, stay.latitude(), stay.longitude(), stay.startTime(),
                    stay.locationType(), stay.locationName(), alertState, alertUntil);
        } catch (RuntimeException e) {
            log.warn("예전 체류 키를 읽을 수 없어 건너뜀: key={}, cause={}", key, e.getClass().getSimpleName());
            return null;
        }
    }

    private static SafeZoneAlertState alertState(Object raw) {
        if (raw == null) {
            return null;
        }
        String value = String.valueOf(raw);
        if (SafeZoneAlertState.EXITED.name().equals(value)) {
            return SafeZoneAlertState.EXITED;
        }
        if (SafeZoneAlertState.RETURNED.name().equals(value)) {
            return SafeZoneAlertState.RETURNED;
        }
        return null;
    }

    record Result(int imported, int existing, int invalid) {
    }
}
