-- #987: 서버가 받고 거절한 센서 묶음 기록 테이블 (LLD-0128).
-- 운영은 ddl-auto: validate라 배포 전에 적용한다.
-- 빠진 묶음 번호가 앱이 안 보낸 것인지 서버가 거절한 것인지 가르는 근거다. 원문은 저장하지 않는다.
-- 식별자는 거절된 본문에서 수신 검증과 같은 형식일 때만 남기고 아니면 NULL이다.
-- 재전송마다 거절되면 시도마다 한 행이라 유일 키가 없다. 회원 조회 실패 거절도 남기려고 member FK를 두지 않는다.
CREATE TABLE sensor_batch_rejection (
    sensor_batch_rejection_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    device_id VARCHAR(64) NULL,
    stream VARCHAR(20) NULL,
    batch_id VARCHAR(26) NULL,
    session_id VARCHAR(64) NULL,
    seq BIGINT NULL,
    -- ErrorCode.getCode() 값(예: SENSOR_4003).
    error_code VARCHAR(32) NOT NULL,
    -- WS 또는 REST.
    ingest_channel VARCHAR(8) NOT NULL,
    -- 서버 수신 시각(epoch ms). sensor_batch.server_received_at_ms와 같은 시점이다.
    received_at_ms BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    INDEX idx_sensor_batch_rejection_member_time (member_id, received_at_ms)
) DEFAULT CHARSET = utf8mb4;
