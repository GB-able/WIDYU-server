# LLD-0017: 부분 취소 멱등성 보장

| 항목 | 값 |
| --- | --- |
| 상태 | Approved |
| 관련 ARCH | ARCH-030 |
| 관련 ADR | ADR-0012 |
| 작성일 | 2026-07-25 |

## 1. 목적

같은 부분 취소 요청의 재전송 또는 병렬 실행이 PG 환불, 취소 이력, 포인트 환수를 중복 처리하지 않도록 한다.

## 2. API 및 데이터 모델

`POST /api/v1/payment/{paymentKey}/cancel`의 부분 취소 본문에 `idempotencyKey`를 추가한다.

```json
{
  "cancelReason": "구매 실수",
  "cancelAmount": 3000,
  "idempotencyKey": "cancel-20260725-0001"
}
```

- 부분 취소(`cancelAmount < 남은 금액`)는 `idempotencyKey`가 필수다.
- 키는 최대 100자이며 서버 DB에만 저장한다. Toss PG 요청에는 전달하지 않는다.
- `payment_cancel.idempotency_key`를 추가하고 `(payment_id, idempotency_key)`에 고유 제약을 둔다. NULL은 전액 취소의 하위 호환성을 위해 허용한다.

## 3. 처리 흐름

1. `PaymentRepository.findByPaymentKeyForUpdate()`로 결제 행을 `PESSIMISTIC_WRITE` 잠금으로 조회한다.
2. 요청 키가 있고 같은 `PaymentCancel`이 있으면 금액·사유 일치 여부를 검증한다.
   - 일치: PG 호출·포인트 환수 없이 현재 `PaymentConfirmResponse`를 반환한다.
   - 불일치: `BAD_REQUEST`를 반환한다.
3. 새 부분 취소인데 키가 없으면 PG 호출 전에 `BAD_REQUEST`를 반환한다.
4. 취소 선점 트랜잭션에서 `PaymentCancel`(PENDING)을 저장하고 환수 포인트를 즉시 차감해 예약한다. 잔액이 부족하면 선점 전체가 롤백된다.
   - PG 호출 중 포인트가 다른 곳에 사용되어 취소 확정 시 환수가 실패하는 레이스를 막기 위해, 검증만 하지 않고 실제로 차감한다.
5. PG 취소가 성공하면 응답의 상태(`CANCELED`/`PARTIAL_CANCELED`)와 누적 취소 금액(`totalAmount - balanceAmount`)을 검증한 뒤 취소를 확정한다. 포인트는 이미 예약(차감)되어 있으므로 추가 차감하지 않는다.
6. 예약 포인트 해제(반환) 경로:
   - 최종 오류(`NOT_CANCELABLE_PAYMENT` 등)로 취소를 포기하는 `releaseCancellation` (PENDING 행 제거)
   - 멱등 키 보존 기간(15일) 만료로 복구를 포기하는 `stopCancellationRecovery` (`ABORTED` 전이 — 중단 직전 PG 조회에서 취소 미반영이 확인된 경우이며, 이후 새 취소 요청을 막지 않는다)
   - 단, PG 누적 취소 금액 불일치로 대사를 보류하는 경우(`holdCancellationForReconciliation`)는 환불이 이미 반영됐을 수 있어 예약을 유지한 채 수동 정합을 기다린다

### 예약·반환 멱등성 계약

| 전이 | 트랜잭션 | 포인트 연산 | 멱등 가드 |
| --- | --- | --- | --- |
| 선점(예약) | `PENDING` 저장과 같은 트랜잭션 | 차감 | `point_history.operation_key` UNIQUE `PAYMENT_CANCEL:{cancelId}` — 재실행 시 DB 제약이 이중 차감 차단, 실패 시 선점째 롤백 |
| `PENDING → COMPLETED` | 결과 반영 트랜잭션 | 없음 (이미 예약됨) | `pg_idempotency_key` 일치 + `isPending()` 검사 |
| 해제 (`releaseCancellation`) | 행 제거와 같은 트랜잭션 | 반환 | `PAYMENT_CANCEL_RELEASE:{cancelId}` UNIQUE + `isPending()` 검사 — 반환 중 종료 시 트랜잭션째 롤백 |
| `PENDING → ABORTED` | 상태 전이와 같은 트랜잭션 | 반환 | 위와 동일. `ABORTED`는 재요청 시 거부되고 새 멱등 키의 취소를 막지 않는다 |

## 4. 인수조건

- [x] 부분 취소에 멱등 키가 없으면 PG 호출 전에 거부한다.
- [x] 동일 키·동일 요청 재전송은 PG 호출과 포인트 환수를 한 번만 수행한다.
- [x] 동일 키·상이한 요청은 거부한다.
- [x] 취소 결제 조회는 비관적 쓰기 잠금을 사용한다.
- [x] `./gradlew :backend:widyu-api:test --tests 'com.widyu.pay.application.PaymentServiceTest' --tests 'com.widyu.pay.integration.PaymentIntegrationTest'`가 통과한다.

## 5. 미결정 사항

Toss의 취소 거래 키를 별도 대사 식별자로 보관할지 여부는 PG 대사·보정 작업 설계에서 결정한다.
