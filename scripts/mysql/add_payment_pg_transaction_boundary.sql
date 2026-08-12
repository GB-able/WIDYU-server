-- 운영 DB에서 애플리케이션 배포 전에 1회 실행합니다.
-- ddl-auto: update는 기존 MySQL ENUM에 새 값을 추가하지 않습니다.

ALTER TABLE payment
    MODIFY COLUMN status ENUM('READY', 'IN_PROGRESS', 'WAITING_FOR_DEPOSIT', 'DONE', 'PARTIAL_CANCELED', 'CANCELED', 'ABORTED', 'EXPIRED') NOT NULL;

ALTER TABLE point_history
    ADD COLUMN operation_key VARCHAR(100) NULL,
    ADD CONSTRAINT uk_point_history_operation_key UNIQUE (operation_key);

ALTER TABLE payment_order
    MODIFY COLUMN status ENUM('CREATED', 'APPROVING', 'PAID', 'CANCELED', 'EXPIRED') NOT NULL,
    ADD COLUMN approval_payment_key VARCHAR(200) NULL,
    ADD COLUMN approval_pg_idempotency_key VARCHAR(36) NULL,
    ADD COLUMN approval_requested_at DATETIME NULL,
    ADD COLUMN approval_retry_count INT NOT NULL DEFAULT 0,
    ADD COLUMN approval_next_retry_at DATETIME NULL,
    ADD COLUMN approval_last_error_code VARCHAR(100) NULL,
    ADD COLUMN approval_recovery_stopped_at DATETIME NULL,
    ADD INDEX idx_payment_order_approval_recovery (status, approval_next_retry_at);

ALTER TABLE payment_cancel
    ADD COLUMN status ENUM('PENDING', 'COMPLETED') NOT NULL DEFAULT 'COMPLETED',
    ADD COLUMN pg_idempotency_key VARCHAR(36) NULL,
    MODIFY COLUMN canceled_at DATETIME NULL,
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0,
    ADD COLUMN next_retry_at DATETIME NULL,
    ADD COLUMN last_error_code VARCHAR(100) NULL,
    ADD COLUMN recovery_stopped_at DATETIME NULL,
    ADD INDEX idx_payment_cancel_recovery (status, next_retry_at);

UPDATE payment_cancel
SET pg_idempotency_key = UUID()
WHERE pg_idempotency_key IS NULL;

ALTER TABLE payment_cancel
    MODIFY COLUMN pg_idempotency_key VARCHAR(36) NOT NULL,
    ADD CONSTRAINT uk_payment_cancel_pg_idempotency_key UNIQUE (pg_idempotency_key);

-- 동일 멱등 키 재요청 비교용: 원본 요청의 취소 금액(전액 취소 = NULL) 보존
ALTER TABLE payment_cancel
    ADD COLUMN requested_cancel_amount INT NULL;

-- 기존 부분 취소(멱등 키 보유) 행은 요청 금액이 명시된 것으로 백필
UPDATE payment_cancel
SET requested_cancel_amount = cancel_amount
WHERE idempotency_key IS NOT NULL;
