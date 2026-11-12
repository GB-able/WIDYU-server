package com.widyu.incident.repository;

import com.widyu.incident.Incident;
import com.widyu.incident.IncidentKind;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 알림 기록 내보내기 전용 조회(LLD-0131). {@code IncidentRepository}와 나눈 것은 다른 작업이 그 파일을 고칠 때
 * 충돌을 피하기 위해서다.
 */
public interface IncidentAlertRecordRepository extends Repository<Incident, Long> {

    // ponytail: opened_at_ms만으로 거는 인덱스가 없어 범위 스캔이다. 사건은 위급 판정마다 하나라 작다.
    // 기간 조회가 느려지면 (opened_at_ms) 인덱스를 더한다.
    @Query("""
            SELECT new com.widyu.incident.repository.IncidentAlertRow(
                   i.openedAtMs, i.incidentRef, i.memberId, i.kind, i.runId,
                   i.detectionCount, i.decisionId, i.level,
                   d.hrBpm, d.hrMeasuredAtMs, d.hrAccuracy, d.reason,
                   b.onBody, b.wearState,
                   i.respondByMs, i.response, i.respondedAtMs, i.responseVia,
                   i.state, i.initialAlertSentAtMs, d.alertDelivered, d.alertAtMs,
                   i.outcome)
              FROM Incident i
              LEFT JOIN DecisionRecord d ON d.decisionId = i.decisionId
              LEFT JOIN SensorBatch b ON b.batchId = d.triggerBatchId
             WHERE i.openedAtMs >= :fromMs
               AND i.openedAtMs < :toMs
               AND i.kind IN :kinds
             ORDER BY i.openedAtMs, i.id
            """)
    List<IncidentAlertRow> findAlertRecords(
            @Param("fromMs") long fromMs, @Param("toMs") long toMs, @Param("kinds") List<IncidentKind> kinds);
}
