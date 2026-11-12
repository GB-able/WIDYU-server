package com.widyu.retention.application;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 밤 보관 기간 경과 건수를 센다(LLD-0136 5.1). 1단계라 삭제 경로가 없다.
 *
 * <p>단일 인스턴스 전제다(ADR-0023). 두 번 돌아도 기록이 두 벌 남을 뿐이다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "retention.purge.enabled", havingValue = "true")
public class RetentionPurgeScheduler {

    private final RetentionPurgeService retentionPurgeService;

    @Scheduled(cron = "${retention.purge.cron}", zone = "Asia/Seoul")
    public void run() {
        retentionPurgeService.countExpired(LocalDateTime.now());
    }
}
