-- #1015: 보관 기간 정리 작업 기록 (LLD-0136 4.3, ADR-0043 결정 9).
-- 개발 서버는 ddl-auto: update가 만든다. 운영 서버는 아직 없으므로 운영 구축 때 적용한다.
-- 운영은 ddl-auto: validate라 이 표가 없으면 기동이 실패한다.
-- 한 행 = 정리 작업 한 번, 테이블 하나의 결과다. 회원 ID, 좌표, 건강값을 담지 않는다.
-- 이 표는 자동 삭제하지 않는다. 하루 11행이다.
CREATE TABLE data_purge_log (
    data_purge_log_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    -- 한 번의 실행을 묶는 UUID.
    run_id VARCHAR(36) NOT NULL,
    -- 대상 테이블 이름.
    target VARCHAR(40) NOT NULL,
    -- 지우지 않고 세기만 했는지. 1단계는 항상 1이다.
    dry_run BIT NOT NULL,
    -- 이 시각보다 앞선 행이 대상이다.
    cutoff_at DATETIME(6) NOT NULL,
    -- 조건에 맞는 행 수(실증 참가자 제외 뒤). 실패하면 NULL.
    target_rows BIGINT NULL,
    -- 기간이 지났지만 실증 참가자라 뺀 행 수. 제외 규칙이 없는 테이블과 실패하면 NULL.
    exempt_rows BIGINT NULL,
    -- 테이블 처리 중 예외 클래스 이름.
    error_type VARCHAR(100) NULL,
    started_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NOT NULL,
    INDEX idx_data_purge_log_started (started_at)
) DEFAULT CHARSET = utf8mb4;
