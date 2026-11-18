# LLD-0167: 응원, 하트 메시지 알림의 잠금화면 문구와 40자 미리보기

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #1103 |
| 관련 ADR | - |
| 작성자 | Claude / feature-1103 |
| 작성일 | 2026-11-18 |

## 1. 목적 / 배경

보호자와 어르신이 주고받는 응원 메시지(X02)와 하트 메시지(X01)는 원문이 FCM `notification.body`에 실려 잠금화면과 알림창에 그대로 떴다. 알림 정책서(위듀 v12, 위듀케어 v18) 공통 규칙 1은 잠금화면에 사용자 메시지 원문을 보이지 않게 한다.

두 경로는 알림 타입 없이 레거시로 보냈다. 그래서 알림센터 행이 `type=null`, `expires_at=null`로 원문을 만료 없이 담았고, 재시도 큐(`fcm_outbox.body`)에도 원문이 남았으며, FCM 채널과 우선순위(LLD-0164)가 붙지 않았다. 카탈로그(`NotificationCopy`)의 미리보기 자르기는 UTF-16 `substring(0, 40)`이라 이모지를 반으로 잘랐다.

## 2. 범위

### In scope
- 변경 모듈: widyu-api
- 응원, 하트 메시지 발송을 문구 카탈로그(X02-OS, X02-INAPP, X01-OS, X01-INAPP)와 알림 타입(`CHEER_MESSAGE_RECEIVED`, `HEART_MESSAGE_RECEIVED`)으로 조립한다(`FcmSendDto.ofMessage`).
- 메시지 정리와 미리보기 규칙(§5.2)을 `NotificationCopy`에 둔다. `{메시지 미리보기}` 변수는 이 규칙으로 채운다.
- 응원 메시지 입력을 50글자(그래핌)로 제한한다(§6).
- 이미 쌓인 레거시 응원, 하트 행을 정리하는 일회성 SQL(`scripts/mysql/trim_legacy_message_notifications.sql`).

### Out of scope
- 하트 메시지 API(`POST /api/v1/heart-rate/message`) 삭제: 위듀케어 앱이 아직 부른다. 앱 정리 뒤 별도 PR.
- 일정명 15자 자르기(H01): 서버에 아직 자르는 코드가 없다. 문구 정렬 PR에서 같은 그래핌 함수를 쓴다.
- 알림센터 목록 API 필드 추가, DB 스키마 변경.
- 원문 보관: 정책서에 원문을 보여 줄 화면이 없어 서버 어디에도 원문을 남기지 않는다(2026-11-18 결정).

## 3. 인터페이스 / API

요청 형식은 그대로다.

```http
POST /api/v1/fcm/send            {"receiverId": 123, "content": "…"}   응원(X02)
POST /api/v1/heart-rate/message  {"receiverId": 123, "message": "…"}   하트(X01)
```

FCM 전송 본문(응원, 변경 뒤):

| 위치 | 키 | 값 |
| --- | --- | --- |
| `notification` | `title` | 「{보낸 사람 이름} 님이 응원메시지를 보냈어요!」 |
| `notification` | `body` | 「앱에서 응원메시지를 확인해주세요.」 |
| `notification` | `image` | 보낸 사람 프로필 이미지(기존) |
| `data` | `notificationType` | `CHEER_MESSAGE_RECEIVED`(기존) |
| `data` | `deepLink` | `/notification`(기존) |
| `data` | `notificationId` | 센터 행 id(기존) |
| `data` | `eventId` | UUID(추가) |
| `data` | `actorDisplayName` | 보낸 사람 이름(추가) |
| `data` | `inAppTitle` | X02-INAPP 제목(잠금화면 제목과 같음, 추가) |
| `data` | `inAppBody` | 메시지 미리보기(추가) |

하트는 같은 구조에 X01 문구(「{보낸 사람 이름} 님이 메시지를 보냈어요!」 / 「앱에서 메시지를 확인해주세요.」)와 `HEART_MESSAGE_RECEIVED`를 쓴다.

`inAppTitle`, `inAppBody`는 H01(일정 임박)이 이미 「잠금화면과 다른 앱 안 문구」 뜻으로 쓰는 키다(LLD-0109). 새 키를 만들지 않아 앱은 알림 종류와 상관없이 같은 키를 읽는다.

## 4. 데이터 모델

스키마 변경은 없다. 저장 값만 바뀐다.

| 저장소 | 칸 | 이전 | 이후 |
| --- | --- | --- | --- |
| `fcm_notification` | `type` | null | `CHEER_MESSAGE_RECEIVED` / `HEART_MESSAGE_RECEIVED` |
| `fcm_notification` | `title` | 「A님이 B님에게 응원메시지를 보냈어요.」 | X02-INAPP / X01-INAPP 제목 |
| `fcm_notification` | `body` | 원문(최대 255자) | 미리보기 |
| `fcm_notification` | `expires_at`, `retention_policy_version` | null | 생성 + 90일(`ROUTINE_90D`), `v1` |
| `fcm_notification` | `actor_display_name` | null | 보낸 사람 이름 |
| `fcm_outbox` | `title`, `body` | 제목, 원문 | 잠금화면 고정 문구 |
| `fcm_outbox` | `data_payload` | deepLink, notificationType | §3 data(미리보기 포함, 원문 없음) |

## 5. 처리 흐름

### 5.1 발송

1. 컨트롤러가 `@Valid`로 필수값과 길이 상한(응원 `@Size(max = 255)`, 하트 `@Size(max = 50)`)을 확인한다.
2. 서비스가 가족 관계를 확인한다(기존. 응원은 다른 가족이나 없는 회원이면 403, 하트는 404/403).
3. `FcmSendDto.ofMessage(type, sender, message)`가 메시지를 정리(§5.2)하고 검사(§6)한 뒤 OS 문구와 앱 안 문구를 조립한다. `title`/`content`에 OS 문구, `centerTitle`/`centerBody`에 앱 안 문구, `actorDisplayName`에 보낸 사람 이름을 넣는다.
4. `FcmOutboxService.enqueue`(기존)가 센터 행을 `centerTitle`/`centerBody`로, outbox 행을 `title`/`content`로 저장한다. 타입이 있어 만료와 전송 설정이 붙는다. 같은 트랜잭션 커밋 뒤 비동기 전송(기존).

### 5.2 메시지 정리와 미리보기

1. NFC 정규화(`java.text.Normalizer`). 분해된 한글 자모가 음절 하나로 합쳐진다.
2. 공백이 아닌 제어 문자(`\p{Cc}`)를 지운다.
3. 유니코드 공백 연속(`\s`, `UNICODE_CHARACTER_CLASS`: 줄바꿈, 탭, U+2028, U+3000, NBSP 포함)을 공백 하나로 바꾸고 앞뒤 공백을 지운다. 목록 한 줄 미리보기라 줄바꿈을 살리지 않는다.
4. 형식 문자(`\p{Cf}`, 제로폭 결합자 등)는 이모지 조합에 필요해 지우지 않는다. 다만 형식 문자를 빼면 아무것도 없는 값은 빈 값으로 본다.
5. 글자 수는 그래핌(사용자가 보는 한 글자)으로 센다. `BreakIterator.getCharacterInstance()`는 JDK 20부터 확장 그래핌 클러스터를 따른다(JDK-8291660, 프로젝트 Java 21). 한글 음절, ZWJ 가족 이모지, 피부색 이모지, 국기가 각각 한 글자다.
6. 40글자 이하면 그대로 쓴다. 넘으면 앞 39글자(끝 공백 제거) + 「…」(U+2026)로 40글자를 넘지 않게 한다(2026-11-18 결정).

## 6. 예외 / 에러 처리

| 조건 | 응답 |
| --- | --- |
| 정리 뒤 빈 값(공백, U+3000, 제로폭 문자만 등. `@NotBlank`가 통과시킨다) | 400 `REQ_4001` 「메시지 내용을 입력해 주세요.」 |
| 정리 뒤 50글자(그래핌) 초과 | 400 `REQ_4001` 「메시지는 50자 이하로 입력해 주세요.」 |
| 응원 `content` UTF-16 255자 초과(기존 `@Size`) | 400 `REQ_4001` |

글자 수 기준은 그래핌이다. 앱(Flutter)의 `TextField.maxLength`가 그래핌 클러스터로 세므로 앱에서 50자로 보이는 입력을 서버가 같은 기준으로 받는다. `@Size`(UTF-16)로 50을 두면 이모지 한 개가 2~11자로 세어 앱에서 허용한 입력이 400이 된다. 기존 `@Size(max = 255)`는 저장 칸(VARCHAR 255) 보호용 상한으로 남긴다. 정리는 길이를 늘리지 않고 미리보기는 정리 결과보다 길지 않으므로 센터 행 `body`가 255자를 넘지 않는다.

하트 요청의 `@Size(max = 50)`(UTF-16)은 API 삭제 예정이라 바꾸지 않는다. 그래핌 50 검사도 함께 받는다.

## 7. 인수조건 (Acceptance Criteria)

- [x] AC1. 응원, 하트 발송 시 outbox 행 `title`, `body`(FCM `notification`)에 원문의 어떤 부분도 없고 정책 고정 문구만 있다.
- [x] AC2. FCM `data.inAppBody`와 센터 행 `body`가 §5.2 미리보기다. `data.inAppTitle`, `actorDisplayName`, `eventId`가 실리고 `notificationType`, `deepLink`는 그대로다.
- [x] AC3. 응원 센터 행 `type=CHEER_MESSAGE_RECEIVED`, `expires_at`이 생성 + 90일, `actor_display_name`이 보낸 사람이다. v2 목록 `type`이 `CHEER_MESSAGE_RECEIVED`다.
- [x] AC4. 미리보기: 39, 40글자는 그대로, 41글자는 앞 39글자 + 「…」. 이모지, ZWJ 가족 이모지, 국기를 반으로 자르지 않는다. 분해형 한글을 음절로 센다. 줄바꿈, 탭, U+2028, 연속 공백이 공백 하나가 된다.
- [x] AC5. 정리 뒤 빈 값, 51글자 이상은 400이고 알림을 접수하지 않는다. 이모지 50글자는 받는다.
- [x] AC6. 다른 가족에게 보내면 403(기존 회귀).
- [x] AC7. 정리 SQL이 레거시 응원, 하트 행만 고른다(`type IS NULL`, 제목 형식). 실행은 배포 뒤 별도.
- [x] AC8. Swagger에 바뀐 제목 형식과 400이 반영된다.
- [x] AC9. `bash scripts/harness/verify.sh`가 통과한다.

## 8. 영향 범위 / 마이그레이션

- 앱 계약: 잠금화면 제목, 부제목이 바뀐다. 탭 이동(`data.deepLink`, `notificationType`)은 그대로다. 앱이 켜져 있을 때 배너를 `notification.title/body`로 그리면 「앱에서 응원메시지를 확인해주세요.」가 뜬다. `data.inAppTitle/inAppBody`를 쓰도록 권한다(필수 아님). Android 채널이 `widyu_default_channel`로 지정된다(LLD-0164).
- 위듀케어 v2 목록: 새 응원, 하트 행의 `type`이 `LEGACY`에서 타입 이름으로 바뀐다.
- 응원 51~255자 입력은 이제 400이다.
- 기존 데이터: `scripts/mysql/trim_legacy_message_notifications.sql`. 배포 뒤 개발 DB에 한 번, 운영 구축 때 이전 데이터가 있으면 한 번 적용한다. MySQL은 그래핌을 모르므로 코드포인트(`CHAR_LENGTH`) 기준으로 자르고 끝에 남은 ZWJ, 변형 선택자를 지운다. ZWJ 이모지와 분해형 한글은 코드포인트가 여러 개라 앱 규칙보다 짧게 잘리며(40글자를 넘지는 않음), 피부색 수식자가 떨어질 수 있다. 레거시 행은 `event_id`가 없을 수 있어 `type`은 채우지 않는다(타입이 있는 행은 v2 응답 `eventId`로 `event_id`를 쓴다).

## 9. 미결정 사항 (Open Questions)

없음. 2026-11-18 결정(하트 포함, 원문 미저장, 50자 제한, 39자 + 「…」, 기존 행 정리 SQL)을 반영했다.

## 10. 참고

- 알림 정책서: Confluence SCRUM 142213121(위듀), 142082050(위듀케어)
- LLD-0065(알림센터), LLD-0109(FCM data 계약), LLD-0164(전송 설정), LLD-0136(보관 기간 정리)
- JDK-8291660: BreakIterator 확장 그래핌 클러스터 지원
