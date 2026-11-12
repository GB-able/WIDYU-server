-- #1016: 손목 움직임 5초 구간 활동 상태 테이블 (LLD-0138, ADR-0042 결정 3).
-- 운영은 ddl-auto: validate라 애플리케이션 배포 전에 적용한다. 운영 서버가 아직 없어 운영 구축 때 적용한다.
--
-- 워치 IMU 묶음(1초)이 한 구간에 나눠 들어오므로 상태만 두지 않고 정수 합계를 쌓는다.
-- 더하는 순서와 무관해 늦게 온 묶음, 역순 도착에도 결과가 같다. 멱등 키는 (member_id, window_start_ms)다.
-- 회원당 하루 최대 17,280행. 자동 삭제는 하지 않는다(보존 기간은 #650과 함께 정한다).
CREATE TABLE activity_window (
    activity_window_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    -- 구간 시작·끝(epoch ms, 기기 측정 시각 기준). 시작은 구간 길이의 배수다.
    window_start_ms BIGINT NOT NULL,
    window_end_ms BIGINT NOT NULL,
    sample_count INT NOT NULL,
    -- d = round(가속도 벡터 크기 mg) - 1000의 합과 제곱합. 표준편차를 정수로 다시 구한다.
    magnitude_sum_mg BIGINT NOT NULL,
    magnitude_sq_sum_mg BIGINT NOT NULL,
    -- REST / LOW / ACTIVE. 샘플이 모자라 정하지 않은 구간은 NULL. 값이 늘어도 ALTER가 필요 없게 VARCHAR다.
    state VARCHAR(16) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    -- 멱등 키이자 심박 시각 조회의 범위 인덱스다.
    CONSTRAINT uk_activity_window_member_start UNIQUE (member_id, window_start_ms),
    CONSTRAINT fk_activity_window_member FOREIGN KEY (member_id) REFERENCES member (id)
) DEFAULT CHARSET = utf8mb4;

-- 원 묶음과 재전송 계보에서 먼저 저장된 묶음만 활동 구간에 더하려고 재전송 묶음을 찾는다(LLD-0138 5.1).
CREATE INDEX idx_sensor_batch_original_batch_id ON sensor_batch (original_batch_id);
