package com.widyu.heart.scheduler;

import com.widyu.heart.repository.HeartRateEventRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class HeartRateCleanupScheduler {

    private static final int RETENTION_DAYS = 30;

    private final HeartRateEventRepository heartRateEventRepository;

    /**
     * 매일 새벽 3시 실행: 30일 이상 지난 심박수 이벤트 삭제
     * HeartRateEmergency(위급상황 기록)는 안전 기록이므로 삭제하지 않음
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void deleteOldHeartRateEvents() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
        int deleted = heartRateEventRepository.deleteByMeasuredAtBefore(cutoff);
        log.info("심박수 이벤트 정리 완료 - {}일 이전 데이터 {}건 삭제", RETENTION_DAYS, deleted);
    }
}
