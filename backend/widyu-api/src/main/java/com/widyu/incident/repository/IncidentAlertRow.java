package com.widyu.incident.repository;

import com.widyu.incident.IncidentKind;
import com.widyu.incident.IncidentOutcome;
import com.widyu.incident.IncidentResponseValue;
import com.widyu.incident.IncidentState;
import com.widyu.incident.ResponseVia;

/**
 * 알림 기록 내보내기의 한 줄(LLD-0131 3.2). 사건에 그 사건을 연 판정 기록과 판정 묶음을 left join한 값이다.
 * 회차 없는 알림은 판정 기록이 없어 {@code hrBpm}부터 묶음·FCM 도달 값이 null이다.
 */
public record IncidentAlertRow(
        Long openedAtMs, String incidentRef, Long memberId, IncidentKind kind, String runId,
        Integer detectionCount, String decisionId, String level,
        Integer hrBpm, Long hrMeasuredAtMs, String hrAccuracy, String reason,
        Boolean onBody, String wearState,
        Long respondByMs, IncidentResponseValue response, Long respondedAtMs, ResponseVia responseVia,
        IncidentState state, Long initialAlertSentAtMs, Boolean alertDelivered, Long alertAtMs,
        IncidentOutcome outcome) {
}
