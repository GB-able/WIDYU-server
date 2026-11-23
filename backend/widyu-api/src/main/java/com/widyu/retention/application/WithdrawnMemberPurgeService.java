package com.widyu.retention.application;

import com.widyu.global.infrastructure.s3.deletion.S3ObjectDeletionTaskService;
import com.widyu.retention.DataPurgeLog;
import com.widyu.retention.repository.DataPurgeLogRepository;
import com.widyu.retention.repository.WithdrawnMemberPurgeRepository;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 탈퇴한 지 30일이 지난 회원의 개인 자료를 지운다(LLD-0187, ADR-0043 결정 7). 회원 행은 묘비로 남긴다.
 *
 * <p>회원 한 명이 트랜잭션 하나다. 한 회원이 실패해도 롤백은 그 회원뿐이고 다음 회원으로 간다.
 * 실증 참가자도 같게 지운다(2026-11-23 기획자 답변). 세기만 하는 경로는 없다(사용자 결정, 결정 9 대체).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawnMemberPurgeService {

    static final Period GRACE = Period.ofDays(30);
    // ponytail: 하룻밤 100명. 밀리면 다음 날 이어서 한다. 한 회원의 자료를 한 트랜잭션에 지우므로 센서 묶음이 아주 많으면 길어진다
    static final int MEMBERS_PER_RUN = 100;
    static final String MASK_TARGET = "withdrawn.album_comment_mask";
    static final String FAILURE_TARGET = "withdrawn.member";

    private final WithdrawnMemberPurgeRepository repository;
    private final DataPurgeLogRepository dataPurgeLogRepository;
    private final S3ObjectDeletionTaskService s3ObjectDeletionTaskService;
    private final PlatformTransactionManager transactionManager;

    public void purge(LocalDateTime now) {
        LocalDateTime startedAt = LocalDateTime.now();
        LocalDateTime cutoff = now.minus(GRACE);
        List<Long> memberIds = repository.findDueMemberIds(cutoff, MEMBERS_PER_RUN);
        if (memberIds.isEmpty()) {
            return;
        }

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        List<Counts> done = new ArrayList<>();
        int failed = 0;
        for (Long memberId : memberIds) {
            try {
                done.add(transaction.execute(status -> purgeMember(memberId, now)));
            } catch (RuntimeException e) {
                failed++;
                // 예외 메시지에는 SQL 값이 섞일 수 있어 클래스 이름만 남긴다(LLD-0136 6절).
                log.warn("[WITHDRAWAL_PURGE] 회원 자료 삭제 실패 memberId={} cause={}",
                        memberId, e.getClass().getSimpleName());
            }
        }
        record(UUID.randomUUID().toString(), cutoff, done, failed, startedAt);
        log.info("[WITHDRAWAL_PURGE] cutoff={} members={} failed={}", cutoff, done.size(), failed);
    }

    private Counts purgeMember(Long memberId, LocalDateTime now) {
        // 행을 지우기 전에 파일 목록을 읽는다. 작업은 이 트랜잭션과 함께 커밋되고 커밋 뒤 지워진다.
        List<String> urls = new ArrayList<>(repository.albumFileUrls(memberId));
        urls.addAll(repository.medicationProofImageUrls(memberId));
        s3ObjectDeletionTaskService.schedule(memberId, urls);
        List<String> keys = new ArrayList<>(repository.sensorObjectKeys(memberId));
        keys.addAll(repository.runExportObjectKeys(memberId));
        s3ObjectDeletionTaskService.scheduleKeys(memberId, keys);

        WithdrawnMemberPurgeStep[] steps = WithdrawnMemberPurgeStep.values();
        long[] rows = new long[steps.length];
        for (WithdrawnMemberPurgeStep step : steps) {
            rows[step.ordinal()] = repository.delete(step, memberId);
        }
        long masked = repository.maskComments(memberId);
        repository.markPurged(memberId, now);
        return new Counts(rows, masked);
    }

    private void record(String runId, LocalDateTime cutoff, List<Counts> done, int failed, LocalDateTime startedAt) {
        LocalDateTime finishedAt = LocalDateTime.now();
        for (WithdrawnMemberPurgeStep step : WithdrawnMemberPurgeStep.values()) {
            long rows = done.stream().mapToLong(counts -> counts.rows()[step.ordinal()]).sum();
            dataPurgeLogRepository.save(DataPurgeLog.deleted(
                    runId, step.logTarget(), cutoff, rows, null, startedAt, finishedAt));
        }
        long masked = done.stream().mapToLong(Counts::masked).sum();
        dataPurgeLogRepository.save(DataPurgeLog.deleted(
                runId, MASK_TARGET, cutoff, masked, null, startedAt, finishedAt));
        if (failed > 0) {
            dataPurgeLogRepository.save(DataPurgeLog.deleted(
                    runId, FAILURE_TARGET, cutoff, null, "FAILED_MEMBERS=" + failed, startedAt, finishedAt));
        }
    }

    private record Counts(long[] rows, long masked) {
    }
}
