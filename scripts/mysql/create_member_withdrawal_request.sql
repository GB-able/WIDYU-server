-- #1017: 어르신 탈퇴 요청 (LLD-0137 4.2절). 적용 시점: 운영 구축 때 적용.
-- 서버는 요청과 운영자의 처리 완료·취소만 기록한다. status는 REQUESTED / PROCESSED / CANCELLED.
CREATE TABLE member_withdrawal_request (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    requested_at DATETIME(6) NOT NULL,
    processed_at DATETIME(6) NULL,
    processed_by_admin_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    INDEX idx_member_withdrawal_request_member (member_id, status),
    INDEX idx_member_withdrawal_request_status (status, id)
);
