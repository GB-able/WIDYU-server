# LLD-0171: 보호자가 끈 알림은 알림센터에도 남기지 않는다

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #1126 |
| 관련 ADR | ADR-0046 (ADR-0037 결정 3 대체) |
| 작성자 | Claude / feature-1126 |
| 작성일 | 2026-11-19 |

## 1. 목적 / 배경

보호자가 알림 스위치를 꺼도 지금은 푸시만 빠지고 알림센터 행은 남는다(ADR-0037 결정 3, LLD-0065 §5.5). 위듀케어(보호자) 알림 정책서 v18 2절은 「스위치를 끄면 휴대폰 푸시와 알림센터 어디에도 오지 않는다」로 정했다. 꺼 둔 알림이 목록과 안 읽은 수에 쌓이지 않게, 접수할 때 수신자 설정을 본다.

## 2. 범위

### In scope

- 변경 모듈: widyu-api
- `FcmEligibility.settingAllows(recipientId, recipientType, type, category)`: 발송 판정이 쓰던 설정 그룹 규칙을 공개 메서드로 꺼낸다. `eligible`도 이 메서드를 쓴다.
- `FcmOutboxService.enqueue`: 수신자 ACTIVE·가족 확인 뒤, 센터 행을 만들기 전에 `settingAllows`가 false면 아무것도 만들지 않고 끝낸다.

### Out of scope

- 보호자 스위치를 정책서 2절의 14개로 나누는 작업.
- 이미 쌓인 알림센터 행 정리. 이번 변경은 새 접수부터 적용한다.
- 목록·안 읽은 수 조회 쿼리 변경. 행이 생기지 않으므로 그대로 둔다.

## 3. 인터페이스 / API

공개 API 변경 없음. 보호자가 끈 그룹의 알림이 `GET /api/v1/notifications`, `GET /api/v1/fcm` 목록과 안 읽은 수에 새로 생기지 않는다.

## 4. 데이터 모델

변경 없음.

## 5. 처리 흐름

1. `enqueue`: 수신자 없음·비활성이면 끝(기존). 관련 회원이 다른 가족이면 끝(기존).
2. `settingAllows`로 수신자 설정을 본다. 그룹은 발송 판정과 같다.
   - 수신자가 어르신이면 판정하지 않는다(켜짐 고정, LLD-0095 §11).
   - 타입이 있으면 `DATA_ONLY`는 판정하지 않고, 아니면 `type.settingGroup()`.
   - 타입이 없으면 `PushSettingGroup.fromLegacy(category)`. null(위치 조회 통보, 본인확인 등)이면 판정하지 않는다.
   - 그룹이 `NONE`이면 판정하지 않는다. 그 밖에는 `NotificationSettingService.isNotificationEnabled`(보호자 전원 필수 `SAFETY`, 방장 필수 `SAFE_ZONE`은 항상 true).
3. false면 센터 행과 outbox 행을 만들지 않고 끝낸다. true면 기존 흐름(센터 행 → 토큰별 outbox)이다.
4. 접수 뒤 설정이 꺼지면 claim·preflight가 지금처럼 푸시만 취소하고 센터 행은 남는다. 다시 켜도 꺼져 있던 동안의 알림은 생기지 않는다.

트랜잭션은 호출자 것을 그대로 쓴다. 설정 행은 잠그지 않는다(접수 시점 값 기준, ADR-0046 결과).

## 6. 예외 / 에러 처리

새 에러 없음. 판정에서 조회 예외가 나면 지금처럼 호출자 트랜잭션으로 전파된다.

## 7. 인수조건

- [x] AC1: 복약 확인을 끈 보호자에게 M04를 넣으면 센터 행과 outbox 행이 없고, 켠 보호자는 센터 1행과 토큰별 outbox가 생긴다(`FcmOutboxIntegrationTest`).
- [x] AC2: 방장이 아닌 보호자가 안심구역을 끄면 Z01 센터 행과 outbox 행이 없다(`FcmOutboxIntegrationTest`).
- [x] AC3: 안전 알림 끔 기록이 남은 보호자에게 넣은 2차 재알림은 센터 행과 발송 가능한 outbox가 생긴다(필수 그룹 예외, `FcmOutboxIntegrationTest`).
- [x] AC4: 접수 뒤 일반 알림을 끄면 preflight가 푸시만 취소하고 센터 행은 남는다(`FcmOutboxIntegrationTest`).
- [x] AC5: 일반 알림을 끈 보호자에게는 건강일정 임박(H01-C) 센터 행과 outbox가 없고, 켠 보호자는 한 행만 남는다(`FcmSchedulerOutboxIntegrationTest`).
- [x] AC6: 접수 판정은 끈 그룹만 거부하고 위치 조회 통보 같은 설정 그룹 없는 레거시, `DATA_ONLY`, 어르신 수신은 허용한다(`FcmEligibilityTest`).

## 8. 영향 범위 / 마이그레이션

- 위듀케어 앱: 끈 그룹의 알림이 목록과 안 읽은 수에 새로 생기지 않는다. 화면 변경은 필요 없다.
- 접수마다 보호자·설정 그룹이 있는 알림에 한해 설정 행 조회가 한 번, `SAFE_ZONE`은 멤버십 조회가 한 번 더 생긴다.
- 배포 전 쌓인 OFF 보호자의 센터 행은 그대로 남는다.

## 9. 미결정 사항 (Open Questions)

없음.

## 10. 참고

- ADR-0046, ADR-0037, [LLD-0062](LLD-0062-notification-center-row-per-event.md), [LLD-0065](LLD-0065-notification-setting-groups.md), [LLD-0095](LLD-0095-senior-notification-settings.md), [LLD-0170](LLD-0170-safety-push-locked-for-all-guardians.md)
- 위듀케어(보호자) 알림·알람·팝업 정책과 문구 v18(Confluence 142082050) 2절
