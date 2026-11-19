# LLD-0180: 후속 질문 카드 발급 알림(F01)과 3시간 응답 기한

> Low-Level Design. 이 문서는 #1147 구현과 PR 본문의 오라클이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #1147 |
| 관련 LLD | [LLD-0073](LLD-0073-followup-card-and-answers.md) 2절 Out of scope(OS 푸시·알림센터 없음)와 AC2(12시간)를 바꾼다. [LLD-0075](LLD-0075-followup-answer-reward.md) 보상 |
| 선행 | [LLD-0179](LLD-0179-location-access-notice-type-and-copy.md)(#1143). 같은 `NotificationType` 끝과 타입 수 테스트를 고쳐 그 브랜치 위에 쌓는다 |
| 작성자 | Claude / feature-1147 |
| 작성일 | 2026-11-20 |

## 1. 목적 / 배경

심박 주의 상황에서 어르신이 「괜찮아요」로 끝내면(`OK_CLOSED`) 서버는 「무슨 일이 있었는지」 묻는 후속 질문 카드를 만든다(LLD-0073). 카드는 앱 안에서만 보이고 알림은 없으며, 12시간 동안 답할 수 있다.

위듀(어르신) 알림 정책서 v12 3절은 이를 바꿨다.

- 상황이 끝나면 카드가 생기고 **3시간** 동안 답할 수 있다.
- 휴대폰 푸시를 보내고 알림센터 「기타」에도 남긴다.
- 푸시 문구는 13절 추천: 「아까 몸은 괜찮으셨나요?」 / 「짧게 알려주시면 10포인트를 드려요.」
- 실증 기간에도 켜되 IRB 설명문 보완이 승인된 뒤에 켠다.

정책서가 LLD-0073보다 나중 결정이므로 이를 따른다. 기능은 지금처럼 `SENSOR_FOLLOWUP_ENABLED`(기본 false)로 꺼져 있고, 이 PR은 켜지 않는다.

## 2. 범위

### In scope
- widyu-domain: `NotificationType.FOLLOWUP_SURVEY_REQUESTED`(F01), `FollowupCard.ANSWER_WINDOW_MS`(3시간)
- widyu-api: `NotificationCopy` F01, F01-NOREWARD. `FollowupCardService.issueIfEnabled`가 카드 저장 뒤 알림 접수

### Out of scope
- 기능 켜기(운영 설정, IRB 승인 뒤)
- 앱의 카드 화면과 알림을 눌렀을 때의 이동. 위듀 앱에 카드 경로가 없어 딥링크를 비운다
- 「답하지 않을게요」(decline) API 제거. 정책서는 건너뛰기를 두지 않지만 앱이 부르지 않으면 되는 일이라 서버 계약은 그대로 둔다
- 이미 발급된 카드의 기한 변경. 새 기한은 새로 발급하는 카드부터다

## 3. 인터페이스 / API

API 변경 없음. GET 카드의 `expiresAtMs`가 발급 뒤 3시간이 된다.

| 코드 | 조건 | 제목 | 부제목 |
| --- | --- | --- | --- |
| F01 | 응답 보상 켜짐(`sensor.followup.reward-enabled`) | 아까 몸은 괜찮으셨나요? | 짧게 알려주시면 10포인트를 드려요. |
| F01-NOREWARD | 응답 보상 꺼짐 | 아까 몸은 괜찮으셨나요? | 짧게 알려주세요. |

보상이 꺼져 있으면 제출해도 포인트가 없으므로(LLD-0075), 포인트를 약속하는 문구를 보내지 않는다. 포인트 수는 `FollowupRewardService.REWARD_POINTS`(10)에서 읽는다.

## 4. 데이터 모델

테이블 변경 없음. `fcm_notification.type`, `fcm_outbox.notification_type`은 VARCHAR라 새 값에 ALTER가 필요 없다.

| 종류 | 칩 | 전달 | 우선순위 | 설정 그룹 | 보관 | 딥링크 |
| --- | --- | --- | --- | --- | --- | --- |
| `FOLLOWUP_SURVEY_REQUESTED` | 기타 | 푸시+센터 | 보통 | ETC(어르신은 켜짐 고정) | 90일 | 없음 |

## 5. 처리 흐름

1. `IncidentService.respond`가 OK 종료에서 `issueIfEnabled`를 부른다(변경 없음).
2. 기능이 꺼져 있거나 이미 카드가 있거나 사건 종류가 아니면 끝낸다(변경 없음).
3. 카드를 저장한다. 기한은 발급 시각 + 3시간이다.
4. 같은 트랜잭션에서 어르신에게 F01 알림을 접수한다.
   - eventId `F01:{카드 ID}`. 카드 UK(`incident_ref`)가 이미 중복을 막지만 재접수돼도 센터 행은 하나다.
   - `entityId`는 카드 ID다.
   - 푸시 만료(`pushExpiresAt`)는 카드 기한이다. 기한이 지나 열 수 없는 카드를 열라는 푸시가 늦게 닿지 않는다.
5. 커밋 뒤 outbox가 푸시를 보낸다. 트랜잭션이 롤백되면 카드와 알림이 함께 없어진다.

알림센터 행은 카드가 만료된 뒤에도 일반 보관(90일) 동안 남는다. 행에는 「아까 몸은 괜찮으셨나요?」만 있고 누르면 앱 기본 동작이라 오해의 소지가 작다. 행을 카드와 함께 지우려면 알림 종류별 센터 만료를 따로 두어야 하는데, 이번에는 그만큼의 필요가 없다고 보았다.

## 6. 예외 / 에러 처리

- 알림 접수가 실패하면 예외가 전파되어 카드 발급도 롤백된다. 기존 발급은 OK 응답과 같은 트랜잭션이므로 OK 응답 자체가 실패한다. outbox 접수는 DB 쓰기뿐이라 FCM 장애와는 무관하다.

## 7. 인수조건 (Acceptance Criteria)

- [ ] 기능 ON에서 카드를 발급하면 `FOLLOWUP_SURVEY_REQUESTED` 알림 한 건이 같은 트랜잭션에서 어르신에게 접수된다. eventId `F01:{카드 ID}`, 푸시 만료는 카드 기한이다.
- [ ] 보상 ON이면 부제목에 「10포인트」, OFF면 포인트 언급이 없다.
- [ ] 기능 OFF면 카드도 알림도 없다.
- [ ] `expires_at_ms - issued_at_ms = 10_800_000`이다.
- [ ] verify.sh가 통과한다.

## 8. 영향 범위 / 마이그레이션

- DB 마이그레이션 없음.
- 운영에서 켜는 방법: IRB 설명문 보완 승인 뒤 `SENSOR_FOLLOWUP_ENABLED=true`, 보상까지 주려면 `SENSOR_FOLLOWUP_REWARD_ENABLED=true`. 환경 파일을 바꾼 뒤 API 컨테이너를 다시 만든다.
- 어르신 앱은 새 `notificationType` 값을 받는다. 카드 화면은 앱이 앱 방문 때 `GET /api/v1/followups/current`로 가져온다(LLD-0073).

## 9. 미결정 사항 (Open Questions)

- 없음.

## 10. 참고

- 위듀 알림 정책서 v12 정책 3절, 문구 1절 「설문 요청」, 13절 「설문 푸시」.
