# LLD-0164: 무음 동기화와 표시 알림의 FCM 전송 설정 분리, 관리자 테스트 응답에 실제 전송 본문 반환

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #968 |
| 관련 ADR | - |
| 작성자 | Claude / feature-968 |
| 작성일 | 2026-11-16 |

## 1. 목적 / 배경

`FcmHttpTransport`는 데이터 전용(`DATA_ONLY`) 신호도 우선순위가 `CRITICAL`이면 표시 알림과 같은 설정을 붙였다. 걸음 수 동기화(`WALK_DATA_SYNC`)가 Android 안전 채널(`channel_id`)과 HIGH, iOS `apns-priority 10`, `apns-push-type alert`, `sound default`로 나가 무음 백그라운드 갱신 규격(5, background, `content-available 1`)과 달랐다. 전달 긴급도와 표시 여부를 같은 값으로 정했기 때문이다.

표시 알림의 채널 ID도 앱과 어긋났다. 서버는 `widyu_safety`, `widyu_general`을 보내지만 위듀 앱(develop, main 모두)은 `widyu_safety_channel`, `widyu_default_channel`, `widyu_quiet_channel`을 등록하고 `widyu_safety`는 지운다. 등록되지 않은 채널로 온 알림은 Android가 FCM 기본 채널로 보여 의도한 소리와 중요도가 적용되지 않는다.

관리자 테스트(`POST /api/v1/admin/fcm/test`)는 "2/3개 성공" 문자열만 돌려줘 실제로 나간 JSON과 실패 원인을 볼 수 없었다(2026-11-05 개발 서버 확인).

이번 변경은 프론트가 공유한 전송 규격(2026-11-05)에 맞춘 수정 요청이다. 범위 안의 미결정 정책은 없다.

## 2. 범위

### In scope
- 변경 모듈: widyu-api / widyu-domain / admin
- `DATA_ONLY`(`WALK_DATA_SYNC`, `MEDICATION_SCHEDULE_SYNC`): Android는 `priority HIGH`만 넣고 `notification`(채널)을 생략한다. APNs는 `apns-priority 5`, `apns-push-type background`, `aps.content-available 1`만 넣고 `interruption-level`, `sound`를 넣지 않는다. 최상위 `notification`은 기존처럼 없다.
- 표시 알림의 Android 채널과 iOS 우선순위, 소리를 아래 표로 바꾼다(§3).
- `CRITICAL`은 iOS Critical Alerts 소리 객체 `{"critical":1,"name":"safety_alert.caf","volume":1.0}`를 보낸다. `FcmMessageDto.Aps.sound`가 문자열과 객체를 모두 담는다.
- `MEDICATION_REMINDER_10`, `_20`(M02, M03) 우선순위를 `TIME_SENSITIVE`에서 `INTERACTION`으로 바꾼다.
- 관리자 테스트 응답을 `AdminFcmTestResponse`로 바꾼다. 토큰별 결과(FCM 수락, 실패와 사유, 미시도)와 FCM에 실제로 보낸 요청 본문에서 `message.token`만 뺀 JSON을 담는다. 관리자 화면과 Swagger를 맞춘다.

### Out of scope
- 업무 수신자, 적용일, 알림센터 저장 정책(`DeliveryMode`), 재시도와 기한(TTL, `pushExpiresAt`), 발송 직전 자격 검사. 바꾸지 않는다.
- 알림 설정 판정(`FcmEligibility`). `DATA_ONLY`는 이미 설정 그룹 검사를 건너뛴다. 테스트로 그 동작을 다시 확인만 한다.
- `WALK_DATA_SYNC`의 레지스트리 우선순위(`CRITICAL`). 전송 설정은 `DATA_ONLY`가 먼저 정하므로 우선순위 값은 전송에 쓰이지 않는다. 레지스트리 정리는 별도로 한다.
- 위치 조회 알림(L01). 규격 표에 타입, 우선순위, 딥링크가 비어 있어 넣지 않는다.
- Apple Critical Alerts entitlement 확보, 사용자 critical 허용, 앱 음원과 채널 등록. 앱과 Apple 쪽 작업이며 실제 단말에서만 확인할 수 있다.
- 레지스트리 타입이 없는 레거시 메시지(마음 메시지, 응원, 정시 복약 등)의 플랫폼 설정. 지금처럼 TTL만 넣는다.

## 3. 인터페이스 / API

### FCM HTTP v1 `message.android`, `message.apns`

| 구분 | Android `priority` | Android `channel_id` | `apns-priority` | `apns-push-type` | `aps` |
| --- | --- | --- | --- | --- | --- |
| `DATA_ONLY` | `HIGH` | 없음 | `5` | `background` | `content-available: 1` |
| `CRITICAL` | `HIGH` | `widyu_safety_channel` | `10` | `alert` | `interruption-level: time-sensitive`, `sound: {critical:1, name:"safety_alert.caf", volume:1.0}` |
| `TIME_SENSITIVE` | `HIGH` | `widyu_default_channel` | `10` | `alert` | `interruption-level: time-sensitive`, `sound: "default"` |
| `INTERACTION` | `NORMAL` | `widyu_default_channel` | `10` | `alert` | `interruption-level: active`, `sound: "default"` |
| `PASSIVE` | `NORMAL` | `widyu_quiet_channel` | `5` | `alert` | `interruption-level: passive` |

변경 전 대비: 채널 ID 3종, `INTERACTION`의 `apns-priority` 5→10, `TIME_SENSITIVE`와 `INTERACTION`의 `sound default`, `CRITICAL`의 Critical sound 객체, `PASSIVE`의 quiet 채널, `DATA_ONLY` 전체.

`CRITICAL`의 `interruption-level`은 `time-sensitive`로 둔다. Critical Alerts 권한이 없는 기기에서는 critical 표시가 무시돼도 time-sensitive 알림으로 남게 하기 위해서다. 권한이 있는 기기에서 critical로 울리는지는 단말에서 확인한다.

`data` 키(`notificationType`, `eventId`, `revision`, `deepLink`, `notificationId` 등)는 바뀌지 않는다.

무음 동기화 예시(걸음 수):

```json
{
  "validateOnly": false,
  "message": {
    "token": "<기기 토큰>",
    "data": {"notificationType": "WALK_DATA_SYNC", "eventId": "WALK_DATA_SYNC:2:2026-10-28", "deepLink": ""},
    "android": {"ttl": "300s", "priority": "HIGH"},
    "apns": {"headers": {"apns-expiration": "...", "apns-priority": "5", "apns-push-type": "background"},
             "payload": {"aps": {"content-available": 1}}}
  }
}
```

### 관리자 테스트

```http
POST /api/v1/admin/fcm/test   (local, dev 프로필 전용)
```

요청은 바뀌지 않는다(`memberId`, `title`, `content`, `category`, `notificationType`, `copyCode`, `values`). 응답 `data`가 문자열에서 아래 객체로 바뀐다.

```json
{
  "code": "200",
  "message": "FCM 테스트 발송 완료",
  "data": {
    "memberId": 28,
    "notificationType": "WALK_DATA_SYNC",
    "deliveryMode": "DATA_ONLY",
    "tokenCount": 3,
    "sent": 1,
    "failed": 1,
    "notAttempted": 1,
    "notes": ["관리자 테스트는 알림 설정과 알림센터 저장을 거치지 않고 ...", "SENT는 FCM이 요청을 받았다는 뜻이며 ...", "표시 알림 없는 무음 동기화 신호라 ..."],
    "attempts": [
      {"tokenId": 101, "outcome": "SENT", "detail": null, "payload": {"validateOnly": false, "message": {"data": {}, "android": {}, "apns": {}}}},
      {"tokenId": 102, "outcome": "FAILED", "detail": "HTTP_404 NOT_FOUND UNREGISTERED", "payload": {}},
      {"tokenId": 103, "outcome": "NOT_ATTEMPTED", "detail": "TIME_BUDGET_EXHAUSTED", "payload": null}
    ]
  }
}
```

- `outcome`: `SENT`(FCM 200), `FAILED`, `NOT_ATTEMPTED`(관리자 시간 예산 소진).
- `detail`: 거부면 기존 `Result.detail` 코드(`HTTP_<상태> <FCM 상태> <FcmError/ApnsError 코드>`, `PREFLIGHT`, `EXPIRED_BEFORE_SEND`). 429, 5xx, 시간 초과처럼 코드가 없는 일시 오류는 `RETRYABLE`, 코드 없는 거부는 `REJECTED`.
- `payload`: 그 토큰으로 FCM에 보낸 요청 본문에서 `message.token`만 뺀 JSON. FCM 응답을 받지 못한 경우(시간 초과, 발송 직전 탈락, 미시도)는 `null`.
- `tokenCount 0`이면 활성 토큰이 없어 보내지 않은 것이다. `notes` 첫 줄로도 알린다.
- 알림센터 전용(`CENTER_ONLY`) 종류는 실제 업무에서 푸시가 나가지 않는다는 안내를 `notes`에 붙인다.
- 기기 토큰 값, Firebase 액세스 토큰, 회원 이름은 응답에 넣지 않는다. `tokenId`는 토큰 행 ID다.

## 4. 데이터 모델

DB 변경 없음.

- `FcmMessageDto.Aps.sound`: `String`에서 `Object`(문자열 또는 `CriticalSound`)로. `CriticalSound(int critical, String name, double volume)` 추가.
- `FcmTransport.Result`: `JsonNode sentBody` 구성요소와 `withSentBody` 추가.
- `com.widyu.fcm.application.FcmTestAttempt`: 관리자 테스트의 토큰별 결과.
- `com.widyu.admin.dto.response.AdminFcmTestResponse`: 관리자 테스트 응답.
- `NotificationType.MEDICATION_REMINDER_10`, `_20`: 우선순위 `INTERACTION`.

## 5. 처리 흐름

1. `FcmHttpTransport.execute`가 `FcmMessageDto`를 만든다. 플랫폼 설정은 `deliveryMode`가 `DATA_ONLY`이면 무음 규격으로, 아니면 우선순위 표(§3)로 정한다.
2. 본문을 `ObjectNode`로 바꿔 그대로 직렬화해 보낸다. 보낸 뒤 그 트리에서 `message.token`을 지운다.
3. FCM 응답(200, 429/5xx, 4xx)을 받으면 결과에 2의 트리를 붙인다. outbox 경로는 이 값을 쓰지 않는다.
4. `FcmService.sendTestMessageToUser`는 활성 토큰마다 시간 예산을 확인한다. 남은 예산이 HTTP 한 번의 시간제한보다 적으면 그 토큰은 `NOT_ATTEMPTED`로 남기고 넘어간다. 보낸 토큰은 결과를 `FcmTestAttempt`로 바꾼다.
5. `AdminFcmService`는 회원 존재를 확인하고(없으면 404) 발송한 뒤 `AdminFcmTestResponse.of`로 건수, 안내, 토큰별 결과를 만든다. 감사 로그 문구는 기존처럼 `성공/전체`다.

트랜잭션: 관리자 테스트는 기존처럼 `NOT_SUPPORTED`로 DB 트랜잭션 밖에서 HTTP를 호출한다.

## 6. 예외 / 에러 처리

새 오류 코드 없음. 없는 회원은 기존 `MEMBER_4041`(404), 종류와 맞지 않는 문구 코드는 기존 400이다. 토큰이 없을 때는 예외 대신 `tokenCount 0` 응답이다.

## 7. 인수조건 (Acceptance Criteria)

- [x] AC1. `WALK_DATA_SYNC`, `MEDICATION_SCHEDULE_SYNC` 요청 본문의 `message.android`는 `{"priority":"HIGH"}`이고 `message.apns`는 `{"headers":{"apns-priority":"5","apns-push-type":"background"},"payload":{"aps":{"content-available":1}}}`이다. 최상위 `notification`이 없다.
- [x] AC2. `CRITICAL`, `TIME_SENSITIVE`, `INTERACTION`, `PASSIVE` 대표 타입의 `message.android`, `message.apns` JSON이 §3 표와 같다.
- [x] AC3. M02, M03은 Android `NORMAL`, `widyu_default_channel`, APNs `10`, `active`, `sound default`로 나간다.
- [x] AC4. 전송 결과의 `sentBody`는 스텁 서버가 받은 본문에서 `message.token`만 뺀 것과 같고, 기기 토큰과 액세스 토큰 문자열을 포함하지 않는다.
- [x] AC5. 관리자 테스트는 토큰별로 `SENT`, `FAILED`(사유 코드), `NOT_ATTEMPTED`(`TIME_BUDGET_EXHAUSTED`)를 구분하고, 토큰이 없으면 빈 결과와 안내를 돌려준다.
- [x] AC6. 알림센터 전용 종류와 데이터 전용 종류의 응답에 실제 업무 동작 안내가 붙는다.
- [x] AC7. 데이터 전용 동기화는 알림 설정과 관계없이 발송 자격을 유지한다(기존 `FcmEligibilityTest`).
- [x] AC8. Swagger(`AdminFcmDocs`)와 관리자 화면이 새 응답을 보여준다.
- [ ] AC9. `bash scripts/harness/verify.sh`가 통과한다.

## 8. 영향 범위 / 마이그레이션

- DB 변경 없음. 배포 전에 저장된 PENDING outbox 행도 보낼 때 새 규격으로 나간다(설정은 전송 시점에 타입으로 계산).
- 위듀 앱(develop, main)은 이미 `widyu_safety_channel`, `widyu_default_channel`, `widyu_quiet_channel`을 등록하고 앱 번들에 `safety_alert.caf`가 있다. 데이터 메시지는 `data.notificationType`만 읽으므로 키 변경은 없다.
- 위듀케어 앱(main, 2026-10-26)은 이 채널들을 등록하지 않는다. 변경 전 채널(`widyu_safety`, `widyu_general`)도 등록하지 않았으므로 Android 표시는 지금과 같이 FCM 기본 채널을 쓴다.
- 앱 사용 중이 아닌 iOS 단말에서 무음 동기화는 시스템이 백그라운드 실행을 미루거나 건너뛸 수 있다. 이전의 alert 형식처럼 바로 깨우지 않는다.
- iOS 일반 알림(`INTERACTION`)이 `apns-priority 10`과 기본 소리로 바뀐다. [LLD-0109](LLD-0109-fcm-data-contract-slim.md) §2, §3의 채널 ID, 일반 알림 `apns-priority 5`, M02·M03 `TIME_SENSITIVE`를 이 문서가 대체한다. [LLD-0060](LLD-0060-notification-type-registry.md)의 채널 이름도 이 문서를 따른다.
- 관리자 테스트 응답 `data`가 문자열에서 객체로 바뀐다. 저장소의 관리자 화면 외에 이 API를 쓰는 곳은 위듀 앱 develop, main에서 찾지 못했다.

## 9. 미결정 사항 (Open Questions)

없음. 아래는 정책이 아니라 단말에서만 확인할 수 있는 검증이다.

- iOS Critical Alerts entitlement 승인과 사용자 허용 뒤 `CRITICAL` 알림이 무음 모드에서도 `safety_alert.caf`로 울리는지.
- Android에서 세 채널이 앱이 정한 소리, 중요도로 표시되는지.
- iOS, Android에서 무음 동기화 수신 시 걸음 수, 복약 알람 동기화가 실행되는지.

## 10. 참고

- [LLD-0060](LLD-0060-notification-type-registry.md), [LLD-0109](LLD-0109-fcm-data-contract-slim.md)
- Firebase 메시지 유형과 Android 우선순위: https://firebase.google.com/docs/cloud-messaging/android-message-priority
- Apple 백그라운드 업데이트: https://developer.apple.com/documentation/usernotifications/pushing-background-updates-to-your-app
- Apple Critical Alerts entitlement: https://developer.apple.com/documentation/bundleresources/entitlements/com.apple.developer.usernotifications.critical-alerts
