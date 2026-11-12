-- #990: 실험 모드의 기기 상태 바뀜 기록 (LLD-0129 4절).
-- 운영은 ddl-auto: validate라 배포 전에 적용한다.
-- 충전, 착용, 폰 연결, 네트워크처럼 상태가 바뀐 시각 한 점을 담는다(하트비트는 60초 스냅샷).
-- 멱등 키는 앱이 만든 (device_id, event_id)다.
-- source·kind는 ENUM이 아니라 VARCHAR다. 앱이 먼저 보낸 모르는 종류도 저장한다.
CREATE TABLE device_event (
    device_event_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    device_id VARCHAR(64) NOT NULL,
    source VARCHAR(10) NOT NULL,
    kind VARCHAR(64) NOT NULL,
    -- 상태가 바뀐 시각. 그 기기의 벽시계 epoch ms.
    occurred_at_ms BIGINT NOT NULL,
    session_id VARCHAR(64) NULL,
    -- 종류별 값(JSON 객체 문자열).
    attributes TEXT NULL,
    run_id VARCHAR(64) NULL,
    server_received_at_ms BIGINT NOT NULL,
    -- 배열에서 잘라 낸 이 항목의 원문 그대로. 재직렬화하지 않는다(ADR-0030 v2).
    payload TEXT NOT NULL,
    payload_sha256 CHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_device_event UNIQUE (device_id, event_id),
    CONSTRAINT fk_device_event_member FOREIGN KEY (member_id) REFERENCES member (id),
    INDEX idx_device_event_member_time (member_id, occurred_at_ms)
) DEFAULT CHARSET = utf8mb4;
