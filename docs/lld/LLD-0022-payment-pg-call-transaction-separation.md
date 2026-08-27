# LLD-0022: PG 호출 트랜잭션 분리와 장애 복구

> Low-Level Design. 이 문서는 해당 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Approved |
| Issue | #461 |
| 관련 ADR | ADR-0016 |
| 작성일 | 2026-08-08 |

## 1. 목적 / 배경

PG HTTP 호출 때문에 JDBC 커넥션과 비관적 행 잠금이 장시간 유지되지 않도록 결제 승인·취소의 트랜잭션 경계를 분리한다. PG 성공 뒤 결과 반영 전에 서버가 종료되어도, 저장된 Toss 멱등 키로 같은 요청을 재실행해 내부 결제·포인트 상태를 복구한다.

## 2. 범위

### In scope

- 변경 모듈: `widyu-api`, `widyu-domain`
- 승인·취소의 선점, PG 호출, 결과 반영 트랜잭션 분리
- Toss `Idempotency-Key` 헤더 전달
- 승인·취소 처리중 상태 영속화
- 처리중 건 1분 주기 복구
- 동시 요청과 복구 작업이 같은 PG 요청을 재실행해도 내부 포인트를 한 번만 반영하도록 보장

### Out of scope

- API 경로와 성공 응답 형식 변경
- 웹훅 수신 엔드포인트
- 메시지 브로커·outbox 도입
- 15일을 초과한 처리중 건의 자동 보정

## 3. 인터페이스 / API

기존 API를 유지한다.

```http
POST /api/v1/payment
POST /api/v1/payment/{paymentKey}/cancel
```

`PaymentClient`는 다음 헤더와 조회 메서드를 지원한다.

```http
POST /v1/payments/confirm
Idempotency-Key: {approvalPgIdempotencyKey}

POST /v1/payments/{paymentKey}/cancel
Idempotency-Key: {paymentCancel.pgIdempotencyKey}

GET /v1/payments/{paymentKey}
```

재요청이 이미 처리중이면 저장한 요청과 동일한 `paymentKey` 또는 취소 요청일 때 같은 PG 멱등 키로 호출을 이어간다. 다른 요청은 `PAYMENT_PROCESSING`으로 거부한다.

## 4. 데이터 모델

### `PaymentOrder`

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| `status` | ENUM | NOT NULL | `CREATED`, `APPROVING`, `PAID`, `EXPIRED`, `CANCELED` |
| `approval_payment_key` | VARCHAR(200) | NULL | 선점한 Toss 결제 키 |
| `approval_pg_idempotency_key` | VARCHAR(36) | NULL | 서버 생성 UUID |
| `approval_requested_at` | DATETIME | NULL | 선점 시각 |
| `approval_retry_count`, `approval_next_retry_at` | INT, DATETIME | NOT NULL, NULL | 지수 백오프 복구 제어 |
| `approval_last_error_code` | VARCHAR(100) | NULL | Toss `code` 원문 |
| `approval_recovery_stopped_at` | DATETIME | NULL | 인증 유효시간 경과로 자동 POST를 중단한 시각 |

`APPROVING`에서의 요청 정보는 완료 전까지 변경하지 않는다. HTTP 상태만으로 선점을 해제하지 않으며 Toss 오류 코드를 보존한다.

### `PaymentCancel`

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| `status` | ENUM | NOT NULL | `PENDING`, `COMPLETED`, `ABORTED`(PG 미반영 확인 후 복구 포기) |
| `idempotency_key` | VARCHAR(100) | NULL, `(payment_id, idempotency_key)` UNIQUE | 클라이언트 멱등 키 (ADR-0012, 전액 취소 하위호환 NULL) |
| `requested_cancel_amount` | INT | NULL | 원본 요청의 취소 금액 — 전액 취소(금액 생략)는 NULL로 보존해 동일 키 재요청 비교에 사용 |
| `pg_idempotency_key` | VARCHAR(36) | NOT NULL, UNIQUE | 서버 생성 UUID |
| `canceled_at` | DATETIME | NULL | PG 취소 완료 시각 |
| `retry_count`, `next_retry_at`, `last_error_code`, `recovery_stopped_at` | INT, DATETIME, VARCHAR, DATETIME | - | 취소 복구 제어와 오류 코드 |

`PaymentCancel`은 PG 호출 전에 `PENDING`으로 저장하고, 환수 포인트를 같은 선점 트랜잭션에서 예약(차감)한다. `Payment` 누적 취소 금액은 `COMPLETED` 전환 트랜잭션에서만 변경한다. `ABORTED` 전환 트랜잭션에서 예약 포인트를 반환하며, 이후 새 취소 요청을 막지 않는다.

## 5. 처리 흐름

### 5-1. 결제 승인

1. **선점 트랜잭션**: 주문 행을 `PESSIMISTIC_WRITE`로 조회하고 소유권·만료·기존 결제를 검증한다.
2. 주문의 생성 만료 시간과 별개로, 서버가 `paymentKey`를 받은 시각(`approval_requested_at`)부터 승인 인증 유효시간을 계산한다. `CREATED`면 `APPROVING`으로 전환하고, 요청 `paymentKey`, UUID 멱등 키, 현재 시각을 저장한 뒤 커밋한다.
3. 주문이 `APPROVING`이고 같은 `paymentKey`면 저장된 명령을 반환한다. 다른 키면 `PAYMENT_PROCESSING`을 반환한다.
4. **트랜잭션 밖**: 저장된 주문 ID·금액·paymentKey와 PG 멱등 키로 Toss 승인 API를 호출한다.
5. **결과 반영 트랜잭션**: 주문 행을 다시 잠그고 이미 생성된 `Payment`가 있으면 그것을 반환한다. 없으면 PG 응답을 검증한 뒤 `Payment`, 포인트 적립 이력, `PAID` 상태를 하나의 트랜잭션으로 반영한다.
6. 오류 응답은 Toss `code`를 저장하고 `APPROVING`을 유지한다. `PROVIDER_ERROR`, `IDEMPOTENT_REQUEST_PROCESSING`, 미확인 오류는 재조회·재시도 대상이고, 명백한 거절·검증 오류도 먼저 조회해 승인 사실이 없을 때만 선점을 해제한다.

### 5-2. 결제 취소

1. **선점 트랜잭션**: `Payment`를 `PESSIMISTIC_WRITE`로 조회하고 소유권, 잔여 금액, 포인트 잔액, 부분 취소 멱등 키를 검증한다.
2. 진행 중인 `PENDING PaymentCancel`이 없으면 취소 금액·환수 포인트·사유·요청자·클라이언트 멱등 키·PG UUID 키를 가진 `PENDING PaymentCancel`을 저장하고 커밋한다.
3. 이미 `PENDING`이면 같은 요청만 저장된 취소 명령을 사용한다. 다른 요청은 `PAYMENT_PROCESSING`을 반환한다.
4. **트랜잭션 밖**: 저장된 취소 명령과 PG 멱등 키로 Toss 취소 API를 호출한다.
5. **결과 반영 트랜잭션**: `Payment`와 `PaymentCancel`을 다시 잠근다. 취소가 이미 `COMPLETED`면 현재 결제 결과를 반환한다. 그렇지 않으면 PG 응답을 검증하고 취소 이력 완료, `Payment` 누적 취소 금액, 포인트 환수 이력, 전액 취소 주문 상태를 하나의 트랜잭션으로 반영한다.
6. 오류 응답은 Toss `code`를 저장하고 `PENDING`을 유지한다. HTTP 4xx/5xx만으로 취소 이력을 삭제하지 않는다.

### 5-3. 장애 복구

`PaymentRecoveryScheduler`는 1분마다 `next_retry_at <= now`이고 중단 시각이 없는 `APPROVING PaymentOrder`와 `PENDING PaymentCancel`을 조회한다. 선점 직후 첫 재시도는 2분 뒤이고, 이후에는 30초부터 최대 15분의 지수 백오프를 적용한다.

1. 먼저 `paymentKey`로 PG 상태를 조회한다. `DONE` 또는 취소 누적 금액이 반영됐으면 결과 반영 트랜잭션만 호출하고, `ABORTED`·`EXPIRED`는 선점을 해제한다. 조회 오류·404는 미처리로 단정하지 않는다.
2. 조회가 완료를 확인하지 못했을 때만 저장된 요청 본문과 PG 멱등 키로 동일 POST를 재실행한다. 기존 키만 사용한다.
3. 성공하면 각 결과 반영 트랜잭션을 호출한다.
4. 모든 오류는 Toss `code`를 저장하고 다음 재시도 시각을 갱신한다. 명백한 최종 오류는 다음 PG 조회에서 승인/취소 사실이 없을 때만 선점을 해제한다.
5. 승인 건은 인증 시점으로부터 10분이 지나면, 취소 건은 멱등 키 최초 요청으로부터 15일이 지나면 PG 조회 뒤 자동 POST 재시도만 중단한다. 상태는 `APPROVING`/`PENDING`으로 보존하고 `recovery_stopped_at`과 오류 로그로 운영 대사 대상으로 만든다.

동일 시각의 API 재요청·스케줄러 실행은 같은 PG 멱등 키를 사용하므로 Toss에는 하나의 논리 요청만 전달된다. 결과 반영은 비관적 잠금과 완료 상태 검사로 한 번만 수행한다.

`Payment.payment_order_id`, `PaymentCancel`의 요청 키, `PointHistory.operation_key`에는 UNIQUE 제약을 둔다. 포인트 이력의 operation key는 승인에는 `PAYMENT_APPROVAL:{orderId}`, 취소에는 `PAYMENT_CANCEL:{paymentCancelId}`를 사용한다.

## 6. 예외 / 에러 처리

| 상황 | 에러 | 처리 |
| --- | --- | --- |
| 다른 승인 `paymentKey`가 처리중 | `PAYMENT_PROCESSING` | 기존 선점을 보존한다 |
| 다른 취소 요청이 처리중 | `PAYMENT_PROCESSING` | 기존 선점을 보존한다 |
| Toss 오류 응답 | Toss `code` 보존 | HTTP 코드가 아닌 오류 코드로 재조회·재시도·최종 해제를 판단한다 |
| Toss 네트워크 오류 | `UNKNOWN_PAYMENT_ERROR` | 선점 상태를 유지하고 지수 백오프로 재시도한다 |
| 10분 경과 처리중 승인 | `AUTHORIZATION_EXPIRED` | PG 조회 후 자동 POST만 중단하고 대사 대상으로 보존한다 |
| 15일 경과 처리중 취소 | `IDEMPOTENCY_KEY_EXPIRED` | PG 조회 후 자동 POST만 중단하고 대사 대상으로 보존한다 |

## 7. 인수조건

- [x] PG HTTP 호출 중 활성 DB 트랜잭션이 없다.
- [x] 승인 선점 직후 서버가 종료되어도 복구 작업이 같은 PG 멱등 키로 승인하고 포인트를 한 번 적립한다.
- [x] 취소 선점 직후 서버가 종료되어도 복구 작업이 같은 PG 멱등 키로 취소하고 포인트를 한 번 환수한다.
- [x] 승인·취소 결과 반영 직전 서버가 종료되어도 재실행 시 결제·포인트 상태가 한 번만 완료된다.
- [x] 같은 승인·취소 요청이 동시에 실행되어도 Toss POST 헤더의 멱등 키가 같다.
- [x] 다른 승인·취소 요청은 처리중 건을 덮어쓰지 않고 `PAYMENT_PROCESSING`을 반환한다.
- [x] HTTP 4xx/5xx만으로 선점 상태를 해제하지 않고 Toss 오류 코드를 보존한다.
- [x] 정상 in-flight 건은 `next_retry_at` 전에는 복구 대상이 아니다.
- [x] 10분 경과 승인과 15일 경과 취소는 자동 POST만 중단하고 대사 대상으로 보존한다.
- [x] `./gradlew :backend:widyu-api:test`와 `./gradlew :backend:widyu-domain:test`가 통과한다.

## 8. 영향 범위 / 마이그레이션

```sql
ALTER TABLE payment
    MODIFY COLUMN status ENUM('READY', 'IN_PROGRESS', 'WAITING_FOR_DEPOSIT', 'DONE', 'PARTIAL_CANCELED', 'CANCELED', 'ABORTED', 'EXPIRED') NOT NULL;

ALTER TABLE point_history
    ADD COLUMN operation_key VARCHAR(100) NULL,
    ADD CONSTRAINT uk_point_history_operation_key UNIQUE (operation_key);

ALTER TABLE payment_order
    MODIFY COLUMN status ENUM('CREATED', 'APPROVING', 'PAID', 'EXPIRED', 'CANCELED') NOT NULL,
    ADD COLUMN approval_payment_key VARCHAR(200) NULL,
    ADD COLUMN approval_pg_idempotency_key VARCHAR(36) NULL,
    ADD COLUMN approval_requested_at DATETIME NULL,
    ADD COLUMN approval_retry_count INT NOT NULL DEFAULT 0,
    ADD COLUMN approval_next_retry_at DATETIME NULL,
    ADD COLUMN approval_last_error_code VARCHAR(100) NULL,
    ADD COLUMN approval_recovery_stopped_at DATETIME NULL,
    ADD INDEX idx_payment_order_approval_recovery (status, approval_next_retry_at);

ALTER TABLE payment_cancel
    ADD COLUMN status ENUM('PENDING', 'COMPLETED', 'ABORTED') NOT NULL DEFAULT 'COMPLETED',
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

UPDATE payment_cancel
SET requested_cancel_amount = cancel_amount
WHERE idempotency_key IS NOT NULL;
```

- `PaymentOrder`, `PaymentCancel`, 상태 enum: `widyu-domain`
- `PaymentService` 분리, `PaymentRecoveryScheduler`, `PaymentClient`, 저장소, 오류 코드, 테스트: `widyu-api`
- ERD-0001: 결제 상태와 추가 컬럼 반영
- 운영 DB 실행 스크립트: `scripts/mysql/add_payment_pg_transaction_boundary.sql`

## 9. 미결정 사항

없음.

## 10. 참고

- [Toss Payments API](https://docs.tosspayments.com/en/api-guide): `Idempotency-Key`는 POST 요청에 적용되며 15일 동안 유효하다.
- [Toss Payments Core API](https://docs.tosspayments.com/reference): `paymentKey` 조회와 결제 승인·취소 API를 제공한다.
