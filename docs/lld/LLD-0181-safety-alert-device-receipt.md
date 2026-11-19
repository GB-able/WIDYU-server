# LLD-0181: 긴급 알림이 폰에 닿은 시각을 앱 신호로 기록한다

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #1144 |
| 관련 ADR | ADR-0035 결정 3(알림 도달은 FCM 첫 성공), 이 LLD는 그 위에 폰 도착을 따로 더한다 |
| 작성자 | Claude / feature-1144 |
| 작성일 | 2026-11-20 |

## 1. 목적 / 배경

서버가 안전 알림의 「전달됨」으로 남기는 값(`decision_record.alert_delivered`, `alert_at_ms`)은 FCM이 요청을 받았다는 사실이다(ADR-0035 결정 3). 폰이 꺼져 있거나 절전 중이면 FCM 접수와 실제 도착 사이가 몇 분 벌어질 수 있지만 서버는 이를 모른다. 위듀케어 알림 정책서 v18 10절 「안전 알림 폰 도착 기록」과 Jira SCRUM-374는 방법, 아이폰 제약, 품을 정해 S04·S06 상태 칸에 적어 달라고 요청했다.

방법: 앱이 긴급 알림을 **받는 즉시** 서버에 도착 신호를 보낸다. 서버는 신호를 받은 시각을 도착 시각으로 남긴다. 신호가 없으면 「도착 미확인」이며 FCM 접수와 구분한다.

- 안드로이드: FCM 메시지 핸들러(백그라운드·포그라운드)에서 부른다. 긴급 알림은 Android `priority=HIGH`라 Doze 중에도 앱을 깨운다.
- 아이폰: 알림이 표시되기 전에 실행되는 Notification Service Extension에서 부른다. 앱이 꺼져 있어도 실행되며 약 30초 안에 네트워크 호출을 할 수 있다. 확장이 실행되려면 APNs `aps.mutable-content=1`이 필요하다. 확장은 앱과 다른 프로세스라 로그인 토큰을 App Group으로 공유해야 한다. 알림 권한이 꺼져 있거나 네트워크가 없으면 호출이 실패하고 도착 미확인으로 남는다.

## 2. 범위

### In scope

- 변경 모듈: widyu-api, widyu-domain
- 우선순위가 긴급(`CRITICAL`)이고 사람에게 보이는 알림(S01 본인확인, S04·S06 심박 위급, K01 워치 도움 요청)에 한해. 긴급으로 앱만 깨우는 데이터 신호(`WALK_DATA_SYNC`, `DATA_ONLY`)는 제외한다
  - FCM data에 전송 건 ID `deliveryId`(= `fcm_outbox.id`, 문자열)를 싣는다.
  - APNs `aps.mutable-content=1`을 붙인다. 기존 `interruption-level`, critical `sound`는 그대로다.
- 도착 신호 API `POST /api/v1/fcm/deliveries/{deliveryId}/received`.
- `fcm_outbox`에 도착 시각을, `decision_record`에 판정별 첫 폰 도착 시각을 더하고 회차 내보내기에 싣는다.

### Out of scope

- 앱 구현(안드로이드 핸들러, iOS 확장 타깃 추가). 앱 저장소 몫이며 규격은 3절과 PR 본문에 둔다.
- DB 장애 대체 경로(`DegradedEmergencyAlertService`)의 직접 전송. outbox 행이 없어 `deliveryId`가 없다. 도착 미확인으로 남는다.
- 관리자 알림 기록 내보내기(LLD-0131) 필드 추가. 필요해지면 같은 컬럼을 읽어 더한다.
- 긴급이 아닌 알림의 도착 기록.

## 3. 인터페이스 / API

`POST /api/v1/fcm/deliveries/{deliveryId}/received` (로그인 필요)

| 항목 | 값 |
| --- | --- |
| 경로 | `deliveryId`: FCM data `deliveryId` 값 |
| 본문(선택) | `{ "deviceReceivedAtMs": 1763548800123 }` 기기 시계 epoch ms, 생략 가능 |
| 200 | `FCM_2012` 「알림 도착을 기록했습니다」. 이미 기록된 건도 200 |
| 404 | `FCM_4041`. 본인에게 간 전송 건이 아니거나 긴급 알림이 아님 |

FCM 메시지 변화(긴급 알림만):

- `message.data.deliveryId`: 전송 건 ID 문자열. 같은 알림이라도 기기(토큰)마다, 1차·2차마다 다르다.
- `message.apns.payload.aps.mutable-content`: 1.

앱은 신호 호출이 실패해도 알림 표시를 막지 않는다. 재시도는 1회 정도로 충분하다(서버는 첫 값만 남긴다).

## 4. 데이터 모델

Flyway `V5__safety_alert_device_receipt.sql`.

| 테이블 | 컬럼 | 타입 | 뜻 |
| --- | --- | --- | --- |
| `fcm_outbox` | `device_received_at` | datetime(6) NULL | 서버가 도착 신호를 받은 시각. NULL이면 도착 미확인 |
| `fcm_outbox` | `device_reported_at_ms` | bigint NULL | 앱이 보낸 기기 시계 값. 참고용 |
| `decision_record` | `alert_device_received_at_ms` | bigint NULL | 이 판정의 보호자 알림 중 처음 폰에 닿은 서버 시각 |

`fcm_outbox`는 전송 완료 30일 뒤 지워진다(`RetentionTarget.FCM_OUTBOX`). 연구 기록으로 남는 값은 `decision_record` 쪽이다.

## 5. 처리 흐름

1. 전송(`FcmHttpTransport.execute`): `FcmDeliveryReceiptService.tracksDeviceReceipt`(우선순위 `CRITICAL`이고 `DATA_ONLY`가 아님)이고 outbox 전송이면 data에 `deliveryId`를 넣고, APNs `aps`에 `mutable-content: 1`을 넣는다.
2. 신호(`FcmDeliveryReceiptService.recordReceived`):
   1. 현재 회원과 `deliveryId`로 outbox 행을 찾는다. 없거나 같은 판정(`tracksDeviceReceipt`)에 맞지 않으면 404.
   2. `device_received_at IS NULL`을 조건으로 한 UPDATE로 첫 값만 남긴다. 0행이면(이미 기록) 그대로 끝.
   3. 행에 `decision_id`가 있으면 `alert_device_received_at_ms IS NULL` 조건부 UPDATE로 판정의 첫 폰 도착을 남긴다. 판정과 무관한 본인확인(S01)은 outbox에만 남는다.
3. 전송 상태(SENT 여부)는 보지 않는다. FCM 200 직후 폰 도착 신호가 서버의 전송 완료 트랜잭션보다 먼저 올 수 있기 때문이다.
4. 회차 내보내기: `alert_delivered`(FCM 접수) 옆에 `alert_device_received_at_ms`를 싣는다. 신호가 없으면 null.

## 6. 예외 / 에러 처리

- 남의 전송 건과 없는 건은 같은 404로 답해 존재 여부를 드러내지 않는다.
- 동시 신호(재시도, 같은 기기 중복)는 조건부 UPDATE가 한 번만 성공시킨다.
- 기기 시계 값은 검증하지 않고 참고로만 저장한다. 기준 시각은 서버 시각이다.

## 7. 인수조건 (Acceptance Criteria)

- [x] AC1: 긴급 알림 전송 본문에 `data.deliveryId`와 `aps.mutable-content=1`이 있고, 일반 알림과 긴급 데이터 신호(`WALK_DATA_SYNC`)에는 둘 다 없다(`FcmHttpTransportTest`).
- [x] AC2: 본인 긴급 알림의 첫 신호는 판정 기록의 첫 폰 도착을 남기고, 이미 기록된 건은 판정을 다시 건드리지 않는다(`FcmDeliveryReceiptServiceTest`).
- [x] AC3: 긴급이 아니거나, 긴급 데이터 신호이거나, 본인 것이 아닌 전송 건은 404 예외다(`FcmDeliveryReceiptServiceTest`).
- [x] AC4: 판정의 첫 폰 도착 시각은 두 번 기록해도 첫 값만 남고 FCM 접수 기록과 섞이지 않는다(`DecisionRecordRepositoryTest`).
- [x] AC5: 회차 내보내기에 `alert_device_received_at_ms`가 나오고 신호가 없으면 null이다(`RunExportAssemblerTest`).
- [ ] AC6(앱 배포 후): 안드로이드·아이폰 실기기에서 앱이 백그라운드·종료 상태로 S04를 받으면, 알림을 누르기 전에 도착 시각이 남는다(9절 2번).

## 8. 영향 범위 / 마이그레이션

- Flyway V5. #1142의 V4보다 먼저 적용되면 V4가 순서 위반으로 막히므로 #1142를 먼저 머지한다(Flyway `outOfOrder` 미사용).
- 앱: 새 data 키와 APNs 필드가 더해질 뿐 기존 키는 그대로라 옛 앱은 영향이 없다. iOS 확장을 넣기 전에는 `mutable-content`가 있어도 표시가 그대로다.
- 원복: 앱이 신호를 보내지 않으면 컬럼이 비어 있을 뿐 동작 변화가 없다.

## 9. 미결정 사항 (Open Questions)

1. 도착 시각과 표시 시각의 차이: 확장·핸들러는 표시 직전에 실행되므로 「표시」와 거의 같다고 본다. 사용자가 보았는지는 읽음 처리로 따로 남는다.
2. 안드로이드 백그라운드 수신 시점: 긴급 알림은 notification+data 메시지다. 네이티브 `FirebaseMessagingService.onMessageReceived`는 앱이 백그라운드일 때 이 형식에서 호출되지 않는다. 두 앱(위듀, 위듀케어)은 Flutter `firebase_messaging`을 쓰며, 이 플러그인은 `com.google.android.c2dm.intent.RECEIVE` 브로드캐스트 리시버로 메시지를 받아 백그라운드에서도 `onBackgroundMessage` isolate를 띄운다는 전제로 설계했다. 실기기 확인(AC6)에서 탭 전에 호출되지 않으면, 안드로이드 긴급 알림만 data 전용으로 바꾸고 앱이 안전 채널 로컬 알림을 띄우는 방식으로 옮긴다(앱과 서버 동시 변경, 옛 앱 호환 검토 필요). Codex 검수가 이 위험을 BLOCKER로 지적했고, 앱 확인 전 서버 형식을 바꾸면 옛 앱의 긴급 알림 표시가 깨지므로 지금은 형식을 유지한다.

## 10. 참고

- ADR-0035 결정 3, LLD-0053 5.2(판정의 알림 도달), LLD-0164(우선순위별 전송 설정)
- 위듀케어 알림 정책서 v18 10절, Jira SCRUM-374
