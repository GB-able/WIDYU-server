-- 시니어 체류, 안전구역 판정 상태(LLD-0141, ADR-0044). 운영(prod)은 ddl-auto: validate라 배포 전에 실행한다.
-- 회원당 1행. 위치 처리 트랜잭션 안에서 알림 Outbox와 함께 커밋, 롤백된다. 탈퇴, 비활성 회원 정리 때 지운다.
-- 롤백(이전 버전으로 되돌릴 때): 테이블은 남겨도 이전 버전이 읽지 않는다. LLD-0141 8절 롤백 절차를 따른다.
CREATE TABLE IF NOT EXISTS senior_stay_state (
    member_id       BIGINT       NOT NULL,
    latitude        DOUBLE       NOT NULL,
    longitude       DOUBLE       NOT NULL,
    stay_started_at DATETIME(6)  NOT NULL,
    location_type   VARCHAR(16)  NULL,
    location_name   VARCHAR(255) NULL,
    alert_state     VARCHAR(16)  NULL,
    alert_until     DATETIME(6)  NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (member_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
