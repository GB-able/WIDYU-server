package com.widyu.sensor.repository;

import com.widyu.sensor.SensorBatch;
import java.util.Optional;
import java.util.Collection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SensorBatchRepository extends JpaRepository<SensorBatch, Long> {

    Optional<SensorBatch> findByBatchId(String batchId);

    /**
     * 같은 회원의 같은 계보(원 묶음과 그 재전송들)에 이 묶음보다 먼저 저장된 묶음이 있는지(LLD-0138 5.1).
     * 계보에서 저장 순서(PK)가 가장 앞선 묶음만 활동 구간에 더한다.
     */
    @Query("""
            SELECT COUNT(batch) > 0 FROM SensorBatch batch
             WHERE batch.member.id = :memberId
               AND (batch.batchId = :rootBatchId
                    OR (batch.isResend = true AND batch.originalBatchId = :rootBatchId))
               AND batch.id < (SELECT self.id FROM SensorBatch self WHERE self.batchId = :batchId)
            """)
    boolean existsEarlierInLineage(
            @Param("memberId") Long memberId,
            @Param("rootBatchId") String rootBatchId,
            @Param("batchId") String batchId);

    /** 내보내기: 회차·스트림의 배치를 잰 시각 순으로(LLD-0050 5.3). */
    java.util.List<SensorBatch> findByRunIdAndStreamOrderByMeasuredAtStartMsAscSeqAsc(
            String runId, String stream);

    java.util.List<SensorBatch> findByRunIdOrderByMeasuredAtStartMsAsc(String runId);

    /** 시간대 내보내기: 측정 구간이 내보내는 구간과 겹치는 한 회원의 모든 스트림 묶음(LLD-0108 4절). */
    @Query("""
            SELECT batch FROM SensorBatch batch
             WHERE batch.member.id = :memberId
               AND batch.measuredAtEndMs >= :startMs
               AND batch.measuredAtStartMs <= :endMs
             ORDER BY batch.measuredAtStartMs ASC, batch.seq ASC
            """)
    java.util.List<SensorBatch> findMemberWindowBatches(
            @Param("memberId") Long memberId,
            @Param("startMs") long startMs,
            @Param("endMs") long endMs);

    @Query("""
            SELECT batch FROM SensorBatch batch
             WHERE batch.member.id = :memberId
               AND batch.stream IN :streams
               AND batch.measuredAtEndMs >= :windowStartMs
               AND batch.measuredAtStartMs <= :windowEndMs
               AND batch.persistedAtMs <= :inputCutoffMs
             ORDER BY batch.measuredAtStartMs ASC, batch.seq ASC
            """)
    java.util.List<SensorBatch> findFallInputBatches(
            @Param("memberId") Long memberId,
            @Param("streams") Collection<String> streams,
            @Param("windowStartMs") long windowStartMs,
            @Param("windowEndMs") long windowEndMs,
            @Param("inputCutoffMs") long inputCutoffMs);
}
