package com.widyu.retention.application;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 밤 탈퇴 30일이 지난 회원의 개인 자료를 지운다(LLD-0187). 기본은 dry-run이다(ADR-0043 결정 9).
 *
 * <p>단일 인스턴스 전제다(ADR-0023). 두 번 돌아도 지운 회원은 삭제 완료 시각이 있어 다시 고르지 않는다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "retention.withdrawal-purge.enabled", havingValue = "true")
public class WithdrawnMemberPurgeScheduler {

    private final WithdrawnMemberPurgeService withdrawnMemberPurgeService;

    @Value("${retention.withdrawal-purge.dry-run}")
    private boolean dryRun;

    @Scheduled(cron = "${retention.withdrawal-purge.cron}", zone = "Asia/Seoul")
    public void run() {
        withdrawnMemberPurgeService.purge(LocalDateTime.now(), dryRun);
    }
}
