package com.widyu.retention.application;

import java.time.LocalDateTime;
import java.time.Period;

/**
 * 처리방침 보관 기간을 집행할 테이블과 대상 조건(LLD-0136 4.1).
 *
 * <p>건수를 세는 1단계와 실제로 지우는 2단계가 이 조건을 함께 쓴다. 둘이 갈라지면 운영자가
 * 확인한 건수와 실제 삭제가 달라진다. 테이블과 칸 이름은 이 상수에서만 SQL에 들어간다.
 *
 * <p>기준 시각은 서버가 행을 만든 시각이고 PK와 함께 늘어난다(ADR-0043 결정 3).
 * 정해지지 않은 항목은 세지 않는 쪽으로 둔다(LLD-0136 9절).
 */
public enum RetentionTarget {

    /** 끝나지 않은 발송(PENDING, CLAIMED)은 세지 않는다. */
    FCM_OUTBOX("fcm_outbox", "id", "created_at", Period.ofDays(30),
            "state IN ('SENT', 'CANCELLED', 'EXHAUSTED', 'EXPIRED')", null),
    /**
     * 종류별 기간은 생성 때 채운 expires_at이 정한다. 90일은 가장 짧은 기간으로 PK 범위만 좁힌다.
     * expires_at이 빈 옛 행은 세지 않는다(LLD-0136 9절 3번).
     */
    FCM_NOTIFICATION("fcm_notification", "id", "created_at", Period.ofDays(90),
            "expires_at < :now", null),
    HEART_RATE_EVENT("heart_rate_event", "heart_rate_event_id", "created_at", Period.ofDays(30),
            null, "member_id"),
    SENSOR_BATCH("sensor_batch", "sensor_batch_id", "created_at", Period.ofDays(30),
            null, "member_id"),
    SENSOR_DROPPED_RANGE("sensor_dropped_range", "sensor_dropped_range_id", "created_at", Period.ofDays(30),
            null, "member_id"),
    /** 위급 알림에 붙은 좌표의 식별 방법이 정해질 때까지 reason='incident' 행은 세지 않는다(LLD-0136 9절 1번). */
    LOCATION_FIX("location_fix", "location_fix_id", "created_at", Period.ofDays(30),
            "reason <> 'incident'", "member_id"),
    DEVICE_HEARTBEAT("device_heartbeat", "device_heartbeat_id", "created_at", Period.ofDays(90),
            null, "member_id"),
    HEART_RATE_EMERGENCY("heart_rate_emergency", "heart_rate_emergency_id", "created_at", Period.ofDays(180),
            null, "member_id"),
    DECISION_RECORD("decision_record", "decision_record_id", "created_at", Period.ofDays(180),
            null, "member_id"),
    /** 딸린 표 6개는 2단계에서 사건과 함께 지운다(LLD-0136 5.4). */
    INCIDENT("incident", "incident_id", "created_at", Period.ofDays(180),
            null, "member_id"),
    LOCATION_ACCESS_LOG("location_access_log", "location_access_log_id", "accessed_at", Period.ofYears(1),
            null, null);

    /** 연구 자료 수동 삭제를 마치지 않은 실증 참가자(ADR-0043 결정 4). */
    private static final String PARTICIPANTS =
            "(SELECT member_id FROM study_participation WHERE deletion_processed_at IS NULL)";

    private final String table;
    private final String pkColumn;
    private final String timeColumn;
    private final Period retention;
    private final String extraCondition;
    /** null이면 참가자도 대상이다(연구 자료가 아닌 표). */
    private final String participantColumn;

    RetentionTarget(String table, String pkColumn, String timeColumn, Period retention,
                    String extraCondition, String participantColumn) {
        this.table = table;
        this.pkColumn = pkColumn;
        this.timeColumn = timeColumn;
        this.retention = retention;
        this.extraCondition = extraCondition;
        this.participantColumn = participantColumn;
    }

    public String table() {
        return table;
    }

    public String pkColumn() {
        return pkColumn;
    }

    public String timeColumn() {
        return timeColumn;
    }

    public LocalDateTime cutoff(LocalDateTime now) {
        return now.minus(retention);
    }

    public boolean exemptsParticipants() {
        return participantColumn != null;
    }

    /** 지울 대상. 파라미터 :bound(경계 PK, 미포함), :cutoff, :now. */
    public String targetCondition() {
        return expiredCondition() + participantFilter(" NOT IN ");
    }

    /** 기간이 지났지만 실증 참가자라 뺀 행. exemptsParticipants()일 때만 쓴다. */
    public String exemptCondition() {
        return expiredCondition() + participantFilter(" IN ");
    }

    private String expiredCondition() {
        String condition = pkColumn + " < :bound AND " + timeColumn + " < :cutoff";
        if (extraCondition == null) {
            return condition;
        }
        return condition + " AND (" + extraCondition + ")";
    }

    private String participantFilter(String operator) {
        if (participantColumn == null) {
            return "";
        }
        return " AND " + participantColumn + operator + PARTICIPANTS;
    }
}
