# LLD-0170: 심박 안전 알림은 보호자 누구도 끌 수 없다

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #1125 |
| 관련 ADR | ADR-0037 결정 5 (방장 필수 안전 그룹을 보호자 전원 필수로 넓힘) |
| 작성자 | Claude / feature-1125 |
| 작성일 | 2026-11-19 |

## 1. 목적 / 배경

보호자 설정의 `SAFETY`(심박 위급 S04·S06, 워치 도움 요청 K01)는 지금 방장만 끌 수 없다(LLD-0065). 방장이 아닌 보호자가 끄면 3분 미대응 뒤 활성 보호자 전원에게 가는 2차 재알림(S06)과 DB 장애 대비 저하 경로 알림을 받지 못한다. 위듀케어(보호자) 알림 정책서 v18 10절은 이 항목을 미정으로 두고 「안전 기능이라 끄지 못하게」를 추천한다. 추천대로 모든 보호자에게 잠근다.

## 2. 범위

### In scope

- 변경 모듈: widyu-domain, widyu-api
- `PushSettingGroup`에 보호자 전원 필수 속성(`mandatoryForGuardians`)을 두고 `SAFETY`만 켠다. `SAFE_ZONE`은 지금처럼 방장만 필수다.
- 설정 조회·변경과 발송 판정(`NotificationSettingService`), DB 장애 대비 긴급 수신자 조회(`FamilyMembershipRepository.findEmergencyRecipients`)가 저장된 `SAFETY` 끔 행을 보지 않는다.
- `FCM_4090` 메시지를 역할과 무관한 문장으로 바꾼다. Swagger 설명을 맞춘다.

### Out of scope

- 「본인은 괜찮다고 하셨어요」(S08, `SAFETY_SENIOR_OK_NOTICE_HEART`)는 `GENERAL` 그룹이라 지금처럼 끌 수 있다.
- 보호자 스위치를 정책서 2절의 14개로 나누는 작업, 스위치를 끄면 알림센터에도 남기지 않는 작업(#1126)은 따로 한다.
- 저장된 `SAFETY` 끔 행 삭제. 판정에서 무시하므로 남겨도 동작에 영향이 없다.

## 3. 인터페이스 / API

경로·필드는 그대로다(LLD-0065 §3).

- `GET /api/v1/fcm/settings`: 보호자 누구에게나 `SAFETY`가 `enabled=true`, `mandatoryProductPush=true`, `canEditProductPush=false`다. 방장이 아닌 보호자의 `SAFE_ZONE`은 지금처럼 수정 가능이다.
- `PATCH /api/v1/fcm/settings`: 보호자가 `SAFETY`를 끄면 `FCM_4090`/409, 행과 revision은 그대로다. 켜기는 저장 없이 잠긴 켜짐을 돌려준다.

## 4. 데이터 모델

DDL 없음. `member_notification_setting`에 남은 `SAFETY` 행은 읽지 않는다.

## 5. 처리 흐름

1. `NotificationSettingService.locked`: 시니어이거나 그룹이 보호자 전원 필수면 잠금, 아니면 방장이고 방장 필수 그룹일 때 잠금.
2. `NotificationSettingService.isNotificationEnabled`: 그룹이 null·`NONE`·보호자 전원 필수면 true. `FcmEligibility`의 claim·preflight가 이 판정을 쓰므로 S04·S06·K01 푸시가 끔 행과 무관하게 나간다.
3. `findEmergencyRecipients`: 설정 테이블 조인과 끔 조건을 지운다. ACTIVE 시니어·보호자와 활성 토큰만 본다.

## 6. 예외 / 에러 처리

| 조건 | 결과 |
| --- | --- |
| 보호자의 `SAFETY` 끄기, 방장의 `SAFE_ZONE` 끄기, 시니어의 네 그룹 끄기 | `FCM_4090` / 409 「끌 수 없는 알림입니다.」 |

## 7. 인수조건

- [x] AC1: 방장이 아닌 보호자의 GET에서 `SAFETY`는 끔 행이 있어도 잠긴 켜짐, `SAFE_ZONE`은 수정 가능이다.
- [x] AC2: 방장이 아닌 보호자의 `SAFETY` 끄기는 `FCM_4090`이고 revision이 그대로다.
- [x] AC3: `isNotificationEnabled(SAFETY)`는 끔 행과 방장 여부에 관계없이 true다. 끔 기록이 남은 보호자에게 넣은 S06 outbox는 claim된다.
- [x] AC4: DB 장애 대비 긴급 수신자 스냅샷에 끔 기록이 남은 보호자도 들어간다.
- [x] AC5: `SAFE_ZONE`의 방장 필수, S08의 `GENERAL` 그룹은 바뀌지 않는다.

## 8. 영향 범위 / 마이그레이션

- 3분 미대응 2차 재알림(S06)이 예외 없이 활성 보호자 전원에게 간다. 정책서 2절의 「심박 안전 알림을 켠 보호자 모두」와 결과가 같다.
- 위듀케어 앱은 `mandatoryProductPush`로 스위치를 잠그므로 화면 변경 없이 잠긴 스위치로 보인다. 앱이 `FCM_4090` 메시지를 그대로 보여 준다면 문장만 바뀐다.
- 배포 순서 제약 없음. 저하 경로 스냅샷은 다음 주기 갱신(LLD-0086)부터 끔 기록 보호자를 포함한다.

## 9. 미결정 사항 (Open Questions)

없음.

## 10. 참고

- [LLD-0065](LLD-0065-notification-setting-groups.md), [LLD-0086](LLD-0086-degraded-emergency-alert-path.md), [LLD-0095](LLD-0095-senior-notification-settings.md), ADR-0037
- 위듀케어(보호자) 알림·알람·팝업 정책과 문구 v18(Confluence 142082050) 2절, 10절
