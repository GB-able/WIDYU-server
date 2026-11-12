package com.widyu.sensor;

import com.widyu.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 서버가 받고 거절한 센서 묶음 1건(LLD-0128). 빠진 묶음 번호가 앱이 안 보낸 것인지 서버가 거절한 것인지 가르는 근거다.
 *
 * <p>원문은 저장하지 않는다. 식별자는 거절된 본문에서 얕게 읽어 수신 검증과 같은 형식일 때만 남기고 아니면 null이다.
 * 같은 묶음이 재전송마다 거절되면 시도마다 한 행이라 유일 키를 두지 않는다.
 * 회원 조회 실패 거절도 남기려고 {@code member_id}에 FK를 두지 않는다.
 */
@Entity
@Getter
@Table(
    name = "sensor_batch_rejection",
    indexes = @Index(name = "idx_sensor_batch_rejection_member_time", columnList = "member_id, received_at_ms")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SensorBatchRejection extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sensor_batch_rejection_id")
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "device_id", length = 64)
    private String deviceId;

    @Column(name = "stream", length = 20)
    private String stream;

    @Column(name = "batch_id", length = 26)
    private String batchId;

    @Column(name = "session_id", length = 64)
    private String sessionId;

    @Column(name = "seq")
    private Long seq;

    /** {@code ErrorCode.getCode()} 값(예: {@code SENSOR_4003}). */
    @Column(name = "error_code", nullable = false, length = 32)
    private String errorCode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "ingest_channel", nullable = false, length = 8)
    private SensorIngestChannel ingestChannel;

    /** 서버 수신 시각. {@code sensor_batch.server_received_at_ms}와 같은 시점이다. */
    @Column(name = "received_at_ms", nullable = false)
    private Long receivedAtMs;

    @Builder
    private SensorBatchRejection(
            Long memberId, String deviceId, String stream, String batchId, String sessionId, Long seq,
            String errorCode, SensorIngestChannel ingestChannel, Long receivedAtMs
    ) {
        this.memberId = memberId;
        this.deviceId = deviceId;
        this.stream = stream;
        this.batchId = batchId;
        this.sessionId = sessionId;
        this.seq = seq;
        this.errorCode = errorCode;
        this.ingestChannel = ingestChannel;
        this.receivedAtMs = receivedAtMs;
    }
}
