package com.widyu.retention.application;

import com.widyu.retention.DataPurgeLog;
import com.widyu.retention.repository.DataPurgeLogRepository;
import com.widyu.retention.repository.RetentionPurgeRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 보관 기간이 지난 자료의 건수를 테이블별로 센다(LLD-0136 1단계). 아무것도 지우지 않는다.
 *
 * <p>트랜잭션을 열지 않는다. 건수 조회는 잠금 없는 읽기이고 기록 한 행은 저장마다 자기 트랜잭션이다.
 * 한 테이블이 실패해도 기록을 남기고 다음 테이블로 간다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetentionPurgeService {

    private final RetentionPurgeRepository retentionPurgeRepository;
    private final DataPurgeLogRepository dataPurgeLogRepository;

    public void countExpired(LocalDateTime now) {
        String runId = UUID.randomUUID().toString();
        for (RetentionTarget target : RetentionTarget.values()) {
            countTarget(runId, target, now);
        }
    }

    private void countTarget(String runId, RetentionTarget target, LocalDateTime now) {
        LocalDateTime startedAt = LocalDateTime.now();
        LocalDateTime cutoff = target.cutoff(now);
        try {
            long bound = retentionPurgeRepository.findBoundary(target, cutoff);
            long targetRows = retentionPurgeRepository.countTargets(target, bound, cutoff, now);
            Long exemptRows = null;
            if (target.exemptsParticipants()) {
                exemptRows = retentionPurgeRepository.countExempt(target, bound, cutoff, now);
            }
            dataPurgeLogRepository.save(DataPurgeLog.counted(
                    runId, target.table(), cutoff, targetRows, exemptRows, startedAt, LocalDateTime.now()));
            log.info("[RETENTION] 보관 기간 경과 건수 dryRun=true target={} cutoff={} targetRows={} exemptRows={}",
                    target.table(), cutoff, targetRows, exemptRows);
        } catch (RuntimeException e) {
            // 예외 메시지에는 SQL 값이 섞일 수 있어 클래스 이름만 남긴다(LLD-0136 6절).
            String errorType = e.getClass().getSimpleName();
            log.warn("[RETENTION] 보관 기간 경과 건수 조회 실패 target={} cause={}", target.table(), errorType);
            dataPurgeLogRepository.save(DataPurgeLog.failed(
                    runId, target.table(), cutoff, errorType, startedAt, LocalDateTime.now()));
        }
    }
}
