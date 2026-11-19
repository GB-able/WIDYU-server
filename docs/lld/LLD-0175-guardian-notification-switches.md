# LLD-0175: 보호자 알림 스위치를 정책서의 다섯 묶음 14개로 나눈다

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #1137 |
| 관련 ADR | ADR-0037, ADR-0046 |
| 관련 LLD | LLD-0065(4분류, 이 문서가 보호자 부분을 대체), LLD-0095(시니어 켜짐 고정), LLD-0170(심박 안전 잠금), LLD-0171(끈 알림 센터 미저장) |
| 작성자 | Claude / feature-1137 |
| 작성일 | 2026-11-19 |

## 1. 목적 / 배경

보호자 설정은 안전, 안심구역, 복약 확인, 일반 네 개다. 「일반」 하나를 끄면 새 게시물, 댓글, 좋아요, 목표 달성, 응원 메시지가 함께 꺼져 원하는 알림만 끌 수 없다. 위듀케어(보호자) 알림 정책서 v18 2절은 설정을 알림센터 칩과 같은 다섯 묶음(위치, 목표, 앨범, 메시지, 기타)으로 나누고 알림마다 스위치를 두도록 정했다.

## 2. 범위

### In scope

- 변경 모듈: widyu-domain(`PushSettingGroup`, `NotificationType`), widyu-api(설정 응답, Swagger, Flyway V4)
- 보호자 그룹 14개와 묶음·순서, 알림 종류별 그룹 재매핑
- 기존 보호자 「일반」 행 이행

### Out of scope

- 잠금 규칙 변경. 심박 안전(SAFETY)은 보호자 전원, 안심구역(SAFE_ZONE)은 방장만 끌 수 없다(LLD-0170).
- 시니어 설정. 네 그룹(ALBUM, GOAL, HOME, ETC) 켜짐 고정 그대로(LLD-0095).
- 운영 알림 발송 기능. 스위치만 둔다.
- 위듀케어 앱 설정 화면 개편(앱 몫).

## 3. 인터페이스 / API

`GET /api/v1/fcm/settings` 보호자 응답은 아래 14행을 이 순서로 돌려준다. 행마다 `section`(묶음)과 `order`(1부터)를 더한다. 시니어 행의 `section`은 null이다.

| order | group | 이름 | section | 알림 |
| --- | --- | --- | --- | --- |
| 1 | SAFETY 🔒 | 심박 안전 알림 | LOCATION | S04, S06, K01, K02 |
| 2 | SAFE_ZONE 🔒(방장) | 안심구역 | LOCATION | Z01, Z02 |
| 3 | SENIOR_OK_NOTICE | 「본인은 괜찮다고 하셨어요」 안내 | LOCATION | S08 |
| 4 | MEDICATION_CHECK | 약 인증 확인 | GOAL | M04 |
| 5 | HEALTH_SCHEDULE | 건강일정 임박 | GOAL | H01-C |
| 6 | GOAL_ACHIEVED | 목표 달성 | GOAL | G01-C, G02-C |
| 7 | ALBUM_NEW_POST | 새 게시물 | ALBUM | A02-C |
| 8 | ALBUM_COMMENT | 댓글·답글 | ALBUM | A03, A04 |
| 9 | ALBUM_LIKE | 좋아요 | ALBUM | A05 |
| 10 | ALBUM_SENIOR_VIEWED | 어르신 게시물 확인 | ALBUM | A06, A07 |
| 11 | ALBUM_UPLOAD_COMPLETE | 업로드 완료 | ALBUM | A01 |
| 12 | CHEER_MESSAGE | 응원 메시지 | MESSAGE | X02(하트 메시지 X01 포함) |
| 13 | LEADER_CHANGED | 가족 방장 변경 | ETC | R01, R02 |
| 14 | SERVICE_NOTICE | 운영 알림 | ETC | (발송 기능 없음) |

`PATCH /api/v1/fcm/settings`는 위 group 값을 받는다. 예전 `GENERAL`은 400(`FCM_4001`)이다.

정책서 표에 없는 보호자 수신 알림은 가까운 스위치에 둔다.

- K02(워치 도움 요청 취소): SAFETY. 같은 보호자가 받은 K01과 짝이라, 취소만 끌 수 있으면 요청을 받고 취소를 못 받는 상태가 생긴다.
- X01(하트 메시지): CHEER_MESSAGE. 정책서가 기능 삭제로 정했고 API만 남아 있다.

시니어만 받는 알림(M02·M03·M05·M08·M09, H02~H04, W01·W02, A08)은 시니어 그룹(GOAL, ALBUM)에 둔다. 시니어 설정은 켜짐 고정이라 판정에 쓰이지 않는다. 어떤 알림 종류도 `GENERAL`을 쓰지 않는다.

## 4. 데이터 모델

컬럼 변경 없음. `member_notification_setting.category`는 `varchar(32)`라 새 값에 ALTER가 필요 없다.

`PushSettingGroup.GENERAL`은 값만 남긴다. 어느 화면에도 보이지 않고 어떤 알림도 가리키지 않는다. V4가 행을 지우지만, 이행 전 데이터가 남아도 enum 변환이 실패하지 않게 둔다.

Flyway `V4__split_guardian_general_notification_setting.sql`:

1. `GENERAL=false`인 보호자에게 SENIOR_OK_NOTICE ~ SERVICE_NOTICE 11개 스위치를 OFF로 넣는다. 이미 같은 스위치 행이 있으면 건드리지 않는다.
2. 남은 `GENERAL` 행(켜짐 행, 시니어 행 포함)을 지운다. 켜짐은 기본값과 같아 동작이 바뀌지 않는다.

새 운영 알림(SERVICE_NOTICE)도 OFF로 옮긴다. 「일반」을 끈 사람의 뜻을 더 보수적으로 따른다.

## 5. 처리 흐름

판정은 바뀌지 않는다. `FcmEligibility.settingAllows`가 `NotificationType.settingGroup()`을 읽고, 접수(LLD-0171)와 발송(preflight)이 같은 규칙을 쓴다. 그룹만 잘게 나뉜다.

타입 없는 옛 outbox 폴백(`PushSettingGroup.fromLegacy`)은 카테고리별로 가장 가까운 스위치로 옮긴다: ALBUM→ALBUM_NEW_POST, TARGET·WALK→GOAL_ACHIEVED, HEALTH_SCHEDULE→HEALTH_SCHEDULE, HEART_MESSAGE→CHEER_MESSAGE, ETC→SERVICE_NOTICE. 지금 코드에서 타입 없이 보내는 보호자 알림은 없고, 배포 직전에 쌓인 행만 해당한다.

## 6. 예외 / 에러 처리

- 보호자가 `GENERAL`이나 시니어 그룹을 PATCH: 400 `FCM_4001`(기존 `visibleFor` 검사).
- SAFETY OFF, 방장의 SAFE_ZONE OFF: 409 `FCM_4090`(기존).

## 7. 인수조건

- AC1. 보호자 설정 조회가 위 표의 14행을 순서, section, order 그대로 돌려준다.
- AC2. 보호자가 새 게시물만 끄면 새 게시물은 센터와 푸시에서 빠지고 좋아요는 남는다.
- AC3. 이행 뒤 「일반」 OFF 보호자는 11개 스위치가 모두 꺼져 있고 `GENERAL` 행은 없다. 이미 있던 스위치 행은 바뀌지 않는다. 두 번 실행해도 결과가 같다.
- AC4. 어떤 `NotificationType`도 `GENERAL`을 쓰지 않는다.
- AC5. 시니어 설정 조회와 PATCH 동작은 바뀌지 않는다.

## 8. 영향 범위 / 마이그레이션

- 위듀케어 앱: 설정 화면 개편이 필요하다. 응답은 기존 필드를 그대로 두고 `section`, `order`만 더했다.
- 현재 앱(WIDYU-care develop, main)은 설정 화면에서 `ETC/ALBUM/GOAL/HOME`을 찾고 PATCH에 `policyRevision`을 보내지 않는다. 보호자에게 보이지 않는 그룹이라 모두 꺼짐으로 보이고 PATCH는 400이다. 이 PR 전에도 같은 상태라, 서버를 먼저 배포해도 앱이 새로 깨지지 않는다. `group`은 문자열로 읽어 모르는 값에서 죽지 않는다.
- Flyway V4는 개발 서버 기동 때 한 번 적용된다. 테스트 프로필은 Flyway가 꺼져 있어 H2 MySQL 모드 테스트로 SQL을 검증한다.

### 선택과 기각한 대안

- 선택: 그룹을 스위치 하나씩 enum에 두고, 응답에 `section`·`order`만 더한다. 기존 판정·잠금·센터 미저장 경로를 그대로 쓴다.
- 기각: 옛 `GENERAL` PATCH를 11개 스위치 일괄 변경으로 받아 주기. 지금 `GENERAL`을 보내는 앱이 없어(위 8절) 지킬 호환이 없다.
- 기각: 응답을 묶음별 중첩 구조로 바꾸기. 기존 평평한 목록을 읽는 코드를 깨고, 앱은 `section`으로 묶을 수 있다.
- 기각: 이행을 수동 SQL 스크립트로 두기. 스키마와 데이터 이행은 Flyway 버전 파일로만 한다(LLD-0154). 코드 배포와 같은 순간에 적용돼야 GENERAL 행이 남지 않는다.

## 9. 미결정 사항 (Open Questions)

없음.

## 10. 참고

- 위듀케어(보호자) 알림 정책서 v18(Confluence 142082050) 2절
- `backend/widyu-domain/src/main/java/com/widyu/fcm/PushSettingGroup.java`
