-- #973: 워치 IMU 큐가 넘쳐 버린 구간 테이블 (LLD-0124).
-- 운영은 ddl-auto: validate라 배포 전에 적용한다.
-- 앱이 실제로 버린 IMU 배치 구간을 직접 알려 주는 결측 근거다(정책 B 1.2.4, 1.2.6).
-- 워치가 구간마다 Data Layer 항목 하나를 남기고 폰이 재전송해도 같은 값이 오므로 멱등 키는 (member_id, session_id, from_seq)다.
CREATE TABLE sensor_dropped_range (
    sensor_dropped_range_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    -- 버린 IMU 배치의 session_id. 같은 세션의 sensor_batch 행과 이어진다.
    session_id VARCHAR(64) NOT NULL,
    -- 버린 배치 seq의 처음과 끝(양 끝 포함).
    from_seq BIGINT NOT NULL,
    to_seq BIGINT NOT NULL,
    -- 결측 사유. 값 목록은 합의 대기(S2)라 앱이 보낸 값을 그대로 둔다.
    reason VARCHAR(64) NOT NULL,
    -- 워치가 버린 시각(워치 벽시계 epoch ms).
    ts_ms BIGINT NOT NULL,
    server_received_at_ms BIGINT NOT NULL,
    persisted_at_ms BIGINT NOT NULL,
    -- 앱이 보낸 본문 그대로. 재직렬화하지 않는다(ADR-0030 v2).
    payload TEXT NOT NULL,
    payload_sha256 CHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_sensor_dropped_range UNIQUE (member_id, session_id, from_seq),
    CONSTRAINT fk_sensor_dropped_range_member FOREIGN KEY (member_id) REFERENCES member (id),
    INDEX idx_sensor_dropped_range_member_time (member_id, ts_ms)
) DEFAULT CHARSET = utf8mb4;
