package com.widyu.device;

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
 * 실험 모드에서 기기 상태가 바뀐 시각 한 점(LLD-0129 4절). 충전, 착용, 폰 연결, 네트워크 같은 바뀜을 담는다.
 * 60초 스냅샷인 {@link DeviceHeartbeat}로는 바뀐 시각을 표현할 수 없어 따로 둔다.
 *
 * <p>멱등 키는 앱이 만든 {@code (device_id, event_id)}다. {@code kind}는 모르는 값도 받으므로 VARCHAR다.
 */
@Entity
@Getter
@Table(
    name = "device_event",
    uniqueConstraints = @UniqueConstraint(name = "uk_device_event", columnNames = {"device_id", "event_id"}),
    indexes = @Index(name = "idx_device_event_member_time", columnList = "member_id, occurred_at_ms")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeviceEvent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "device_event_id")
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    @Column(name = "source", nullable = false, length = 10)
    private String source;

    @Column(name = "kind", nullable = false, length = 64)
    private String kind;

    /** 상태가 바뀐 시각. 그 기기의 벽시계 epoch ms다. */
    @Column(name = "occurred_at_ms", nullable = false)
    private Long occurredAtMs;

    @Column(name = "session_id", length = 64)
    private String sessionId;

    /** 종류별 값(JSON 객체 문자열). 원문은 {@link #payload}에 그대로 있다. */
    @Lob
    @Column(name = "attributes", columnDefinition = "TEXT")
    private String attributes;

    @Column(name = "run_id", length = 64)
    private String runId;

    @Column(name = "server_received_at_ms", nullable = false)
    private Long serverReceivedAtMs;

    /** 배열에서 잘라 낸 이 항목의 원문 그대로. 재직렬화하지 않는다(ADR-0030 v2). */
    @Lob
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "payload_sha256", nullable = false, length = 64)
    private String payloadSha256;

    @Builder
    private DeviceEvent(
            Long memberId, String eventId, String deviceId, String source, String kind, Long occurredAtMs,
            String sessionId, String attributes, String runId, Long serverReceivedAtMs,
            String payload, String payloadSha256
    ) {
        this.memberId = memberId;
        this.eventId = eventId;
        this.deviceId = deviceId;
        this.source = source;
        this.kind = kind;
        this.occurredAtMs = occurredAtMs;
        this.sessionId = sessionId;
        this.attributes = attributes;
        this.runId = runId;
        this.serverReceivedAtMs = serverReceivedAtMs;
        this.payload = payload;
        this.payloadSha256 = payloadSha256;
    }
}
