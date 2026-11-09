# LLD-0127: 복약 인증 푸시는 인증 마감에 버린다

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #959 |
| 관련 ADR | ADR-0028(FCM 영속 발송) |
| 선행 LLD | [LLD-0036](LLD-0036-fcm-durable-delivery.md)(outbox 만료), [LLD-0068](LLD-0068-medication-notification-alignment.md)(복약 알림 M01~M04), [LLD-0080](LLD-0080-medication-on-time-push-switch.md)(정시 푸시) |
| 작성자 | Claude |
| 작성일 | 2026-11-09 |

## 1. 목적 / 배경

폰이 꺼져 있던 사이 알람 +20분 푸시(M03 「약 복용 인증 시간이 10분 남았어요」)가 폰을 켠 뒤 약 1시간 늦게 도착했다. 인증 시간(알람 ±30분)이 이미 끝나 내용이 틀린 알림이었다.

`FcmOutboxService`는 모든 푸시에 `expiresAt = now + ttl(emergency)`를 주고, 복약 푸시는 위급이 아니라 일반 TTL 24시간을 받는다. 이 값이 Android `ttl`과 iOS `apns-expiration`으로 나가므로 FCM은 꺼진 폰에 최대 24시간 보관했다가 켜질 때 전달한다.

## 2. 범위

### In scope
- 변경 모듈: widyu-api
- `FcmSendDto`에 푸시 만료 상한 `pushExpiresAt`(null 허용) 추가
- `FcmOutboxService.enqueue`가 outbox `expiresAt`을 `min(now + ttl, pushExpiresAt)`으로 계산
- 시니어 복약 푸시 M01(정시, 종류 없는 레거시 메시지), M02(+10분), M03(+20분)에 `알람 시각 + 30분`을 상한으로 전달

### Out of scope
- 보호자 +30분 알림(M04). 인증 마감 뒤에 보내는 알림이라 이 상한을 줄 수 없다. 버릴 시각은 이슈 #959의 확인 사항으로 남긴다
- 알림센터 행의 보관 기간. M01은 LLD-0080대로 센터 행이 생기고, 만료는 푸시(outbox)에만 적용한다
- 건강일정 1시간 전 알림, 걷기 목표 독려 등 다른 시간 제한 푸시. 알림 정책 문서에서 따로 정한다

## 3. 인터페이스 / API

외부 API 변경 없음. 앱이 받는 FCM 메시지 내용도 같고, 만료(`ttl`, `apns-expiration`)만 짧아진다.

## 4. 데이터 모델

테이블 변경 없음. `FcmSendDto`(widyu-api `fcm/dto`)에 `LocalDateTime pushExpiresAt` 추가. outbox 행으로 복원(`from`)할 때는 필요 없다. outbox `expires_at`에 이미 반영돼 있기 때문이다.

## 5. 처리 흐름

1. `MedicineScheduleNotificationListener`가 매분 알람 시각 `dueAt`(날짜 + 알람 시각)을 기준으로 M01(`minute`), M02(`minute - 10분`), M03(`minute - 20분`) 대상을 찾는다(기존과 같음).
2. 각 푸시의 `FcmSendDto.pushExpiresAt`에 `dueAt + MedicationStatus.ALLOWED_WINDOW_MINUTES(30분)`을 넣는다. 자정을 넘는 알람도 `LocalDateTime` 덧셈이라 날짜가 맞게 넘어간다.
3. `FcmOutboxService.enqueue`는 `now + ttl(emergency)`와 `pushExpiresAt` 중 이른 값을 outbox `expiresAt`으로 저장한다. `pushExpiresAt`이 null이면 지금과 같다.
4. 이후는 기존 경로다. 만료가 지난 행은 `claim`, `preflight`, 전송 직전 확인에서 `EXPIRED`로 끝나고, 재시도 지연도 만료를 넘지 않는다. 전송할 때 남은 시간이 Android `ttl`, iOS `apns-expiration`으로 나간다(LLD-0036).

결과적으로 시니어 복약 푸시의 FCM 보관 시간은 M01 약 30분, M02 약 20분, M03 약 10분이 된다.

## 6. 예외 / 에러 처리

새 ErrorCode 없음. 스케줄러가 늦게 돌아 `pushExpiresAt`이 이미 지났으면 outbox 행은 저장되지만 첫 claim에서 `EXPIRED`가 되어 보내지 않는다. 이미 끝난 인증 시간에 대한 푸시이므로 의도한 동작이다.

## 7. 인수조건 (Acceptance Criteria)

- [x] `pushExpiresAt`이 `now + ttl`보다 이르면 outbox `expiresAt`은 `pushExpiresAt`이다.
- [x] `pushExpiresAt`이 `now + ttl`보다 늦거나 null이면 outbox `expiresAt`은 `now + ttl`이다(기존 동작).
- [x] M01, M02, M03 푸시의 `pushExpiresAt`은 그 알람의 `dueAt + 30분`이다.
- [x] M04 보호자 알림은 `pushExpiresAt`이 null이다.
- [x] `FcmSendDto`의 `with*` 복사 메서드가 `pushExpiresAt`을 유지한다.
- [x] `bash scripts/harness/verify.sh`가 통과한다.

## 8. 영향 범위 / 마이그레이션

- DDL 없음. 배포 즉시 새로 쌓이는 복약 푸시부터 적용된다. 이미 쌓인 outbox 행은 기존 만료를 유지한다.
- `FcmSendDto`의 레코드 생성자가 바뀌어 직접 생성자를 쓰는 곳(같은 레코드의 `with*`, 보조 생성자)을 함께 고친다.

## 9. 미결정 사항 (Open Questions)

- 없음. M04를 몇 시간 뒤 버릴지는 이 LLD 범위 밖이다(#959 확인 사항).

## 10. 참고

- `FcmOutboxService.enqueue`, `FcmOutbox.claim`/`failed`, `FcmOutboxTransactions.preflight`, `FcmHttpTransport`(ttl, apns-expiration)
- 노션 「[이슈] 폰을 늦게 켜면 이미 지난 복약 푸시가 뒤늦게 오는 문제 고치기」
