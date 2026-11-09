package com.widyu.sensor;

import com.widyu.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 워치 IMU 큐가 넘쳐 버린 배치 구간 1건(LLD-0124). 앱이 실제로 버린 구간을 직접 알려 주는 결측 근거다(정책 B 1.2.4, 1.2.6).
 *
 * <p>워치가 구간마다 Data Layer 항목 하나({@code /imu/dropped/{session_id}/{from_seq}})를 남기고 폰이 그대로 보낸다.
 * 다시 보내도 같은 값이 오므로 멱등 키는 {@code (member_id, session_id, from_seq)}다.
 */
@Entity
@Getter
@Table(
    name = "sensor_dropped_range",
    uniqueConstraints = @UniqueConstraint(
            name = "uk_sensor_dropped_range", columnNames = {"member_id", "session_id", "from_seq"}),
    indexes = @Index(name = "idx_sensor_dropped_range_member_time", columnList = "member_id, ts_ms")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SensorDroppedRange extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sensor_dropped_range_id")
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    /** 버린 IMU 배치의 {@code session_id}. 같은 세션의 {@code sensor_batch} 행과 이어진다. */
    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    /** 버린 배치 {@code seq}의 처음과 끝(양 끝 포함). */
    @Column(name = "from_seq", nullable = false)
    private Long fromSeq;

    @Column(name = "to_seq", nullable = false)
    private Long toSeq;

    /** 결측 사유. 값 목록은 합의 대기(S2)라 앱이 보낸 값을 그대로 둔다. */
    @Column(name = "reason", nullable = false, length = 64)
    private String reason;

    /** 워치가 버린 시각(워치 벽시계 epoch ms). */
    @Column(name = "ts_ms", nullable = false)
    private Long tsMs;

    @Column(name = "server_received_at_ms", nullable = false)
    private Long serverReceivedAtMs;

    @Column(name = "persisted_at_ms", nullable = false)
    private Long persistedAtMs;

    /** 앱이 보낸 본문 그대로. 재직렬화하지 않는다(ADR-0030 v2). */
    @Lob
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "payload_sha256", nullable = false, columnDefinition = "CHAR(64)")
    private String payloadSha256;

    @Builder
    private SensorDroppedRange(
            Long memberId, String sessionId, Long fromSeq, Long toSeq, String reason, Long tsMs,
            Long serverReceivedAtMs, Long persistedAtMs, String payload, String payloadSha256
    ) {
        this.memberId = memberId;
        this.sessionId = sessionId;
        this.fromSeq = fromSeq;
        this.toSeq = toSeq;
        this.reason = reason;
        this.tsMs = tsMs;
        this.serverReceivedAtMs = serverReceivedAtMs;
        this.persistedAtMs = persistedAtMs;
        this.payload = payload;
        this.payloadSha256 = payloadSha256;
    }
}
