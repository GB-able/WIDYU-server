# LLD-0117: 알림 목록 탭을 전체·안 읽음·메시지·목표·앨범·위치·기타로 정리하기

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #946 |
| 관련 ADR | [ADR-0037](../adr/ADR-0037-notification-center-model.md) |
| 작성자 | Claude |
| 작성일 | 2026-11-01 |

## 1. 목적 / 배경

앱은 알림 목록에 v1 API(`GET /api/v1/fcm`, `GET /api/v1/fcm/categories`)를 쓴다. 이 API는 저장 카테고리(`FcmCategory`) 12개를 탭으로 그대로 내려준다. 그래서 다음 문제가 있다.

- `방문 일정`, `만보계`, `복약 알림`, `목표`가 각각 따로 보인다.
- 심박 위급 알림, 도움 요청은 저장 카테고리가 `HEART_MESSAGE`라 `가족 메시지` 탭에 들어간다.
- 목록과 안 읽은 수가 만료 시각(`expires_at`)을 보지 않는다. 만료 행 삭제 배치가 없어 보존 기간(90/180일)이 지난 알림도 보인다.

v2 알림 센터(`/api/v1/notifications`)에는 이미 탭 분류 `NotificationCenterFilter`(전체·안 읽음·위치·앨범·목표·메시지)와 만료 조건이 들어간 조회 쿼리가 있다. 이를 고쳐 v1도 쓰게 한다.

요청한 탭은 아래 순서다. 위치는 보호자에게만 보인다.

전체 / 안 읽음 / 메시지 / 목표 / 앨범 / 위치 / 기타

## 2. 범위

### In scope
- 변경 모듈: widyu-api, widyu-domain(`NotificationType`의 탭 값만)
- `NotificationCenterFilter`에 `ETC`(기타)를 추가하고 순서를 위와 같이 바꾼다. 탭 이름(한글)을 enum이 가진다.
- 어느 탭에도 속하지 않던 알림을 `ETC`로 옮긴다(4절 표).
- v1 목록·카테고리 API가 `NotificationCenterFilter`와 v2 조회 쿼리(만료 조건 포함)를 쓴다.
- v2 안 읽은 수(`unreadCounts`)에도 `ETC`가 추가된다(같은 enum을 쓰므로).

### Out of scope
- 저장 카테고리(`FcmCategory`) 값 변경. ADR-0037 결정 6에 따라 값을 늘리지 않는다.
- v2 API 삭제, 만료 행 삭제 배치.
- 목록 항목의 `category` 필드. 저장 카테고리 값(앱 아이콘용)을 그대로 내려준다.
- 앱 화면.

## 3. 인터페이스 / API

### `GET /api/v1/fcm/categories`

응답 형식(`{label, name, count}` 배열)은 그대로다. 항목이 탭 단위로 바뀐다.

| label | name | count |
| --- | --- | --- |
| `ALL` | 전체 | 만료 안 된 안 읽은 알림 전체 수 (기존과 같이 `UNREAD`와 같은 값) |
| `UNREAD` | 안 읽음 | 만료 안 된 안 읽은 알림 전체 수 |
| `MESSAGE` | 메시지 | 탭에 속한 안 읽은 수 |
| `GOAL` | 목표 | 〃 |
| `ALBUM` | 앨범 | 〃 |
| `LOCATION` | 위치 | 〃 (보호자에게만 내려준다) |
| `ETC` | 기타 | 〃 |

### `GET /api/v1/fcm?category=&cursor=`

- `category`는 위 `label` 값을 받는다. 없으면 `ALL`.
- 모르는 값, 시니어의 `LOCATION`은 400 `INVALID_NOTIFICATION_CENTER_FILTER`. 기존 v1은 모르는 값을 `ALL`로 처리했고, 옛 저장 카테고리 값(`TARGET`, `HEART_MESSAGE` 등)도 이제 400이다. 운영 미배포라 앱은 카테고리 API의 `label`만 보내면 된다.
- 응답 형식(`FcmNotificationResponses`), 페이지 크기 10, 커서(`id` 내림차순)는 그대로다.

## 4. 데이터 모델

테이블 변경 없음. 탭 판정은 v2와 같다: 행에 `type`이 있으면 `NotificationType`의 탭 값(`centerFilter`), 없으면(옛 행) 저장 카테고리로 정한다.

| 탭 | `type`이 있는 행 | `type`이 없는 옛 행 (`fcm_category`) |
| --- | --- | --- |
| 메시지 | 하트·응원 메시지 | `HEART_MESSAGE` |
| 목표 | 목표 달성, 복약, 병원 일정, 걷기 | `TARGET`, `HEALTH_SCHEDULE`, `WALK`, `MEDICINE_SCHEDULE` |
| 앨범 | 앨범 전체 | `ALBUM` |
| 위치 | 심박 위급, 도움 요청·취소, 괜찮다는 응답 안내, 안전구역 나감·들어옴 | `SAFE_ZONE` |
| 기타 | **포인트 획득·사용, 가족 대표 변경** (지금은 탭 값 없음) | `ETC`, `INCIDENT_SELF_CHECK`, **`LOCATION_NOTICE`** |

굵게 쓴 항목이 이번에 옮기는 것이다.

- `LOCATION_NOTICE`(보호자가 내 위치를 봤다는 통보)는 시니어만 받는다. 시니어에게는 위치 탭이 없어 지금은 전체에서만 보이므로 기타로 옮긴다.
- 옛 행의 `ETC`는 지금 v2에서 메시지 탭에 묶여 있다. 기타 탭이 생겼으므로 기타로 옮긴다.
- 탭 값이 없는 푸시 전용·데이터 전용 type(`SAFETY_SELF_CHECK`, `MEDICATION_SCHEDULE_SYNC`, `WALK_DATA_SYNC`)은 센터에 저장되지 않으므로 그대로 둔다.

## 5. 처리 흐름

- `NotificationCenterFilter.tabs(memberType)`: enum 선언 순서대로, 보호자가 아니면 `LOCATION`을 뺀다.
- `countedFilters(memberType)`(v2 안 읽은 수): `tabs`에서 `ALL`을 뺀다.
- v1 목록: `parse(category, memberType)` → 탭이면 `findCenterPageByCategory`, 아니면 `findCenterPage(unreadOnly = UNREAD)`. 기준 시각은 서버 현재 시각.
- v1 카테고리: `tabs(memberType)` 각각 `ALL`·`UNREAD`는 `countCenterUnread`, 나머지는 `countCenterUnreadByCategory`.

## 6. 예외 / 에러 처리

| 코드 | HTTP | 조건 |
| --- | --- | --- |
| `INVALID_NOTIFICATION_CENTER_FILTER` (기존) | 400 | 모르는 탭, 시니어의 `LOCATION` |

## 7. 인수조건 (Acceptance Criteria)

- [ ] 보호자의 카테고리 응답 `label`이 `ALL, UNREAD, MESSAGE, GOAL, ALBUM, LOCATION, ETC` 순서이고 `name`이 전체·안 읽음·메시지·목표·앨범·위치·기타다.
- [ ] 시니어의 카테고리 응답은 `LOCATION`이 빠진 6개다.
- [ ] v1 목록을 `GOAL`로 조회하면 복약·병원 일정·걷기·목표 달성 알림이 함께 나온다.
- [ ] 심박 위급 알림(`HEART_RATE_EMERGENCY`, 저장 카테고리 `HEART_MESSAGE`)은 `LOCATION`에 나오고 `MESSAGE`에는 나오지 않는다.
- [ ] 포인트 알림, 옛 `ETC`·`LOCATION_NOTICE` 행이 `ETC`에 나온다.
- [ ] 만료된 알림은 v1 목록과 카테고리 수에서 빠진다.
- [ ] 모르는 탭, 시니어의 `LOCATION`은 400이다.
- [ ] `./gradlew compileJava`, `run-module-tests.sh` 통과.

## 8. 영향 범위 / 마이그레이션

- DB 변경 없음.
- 앱: v1 카테고리 `label` 값이 바뀌므로 탭 칩과 `category` 요청값을 카테고리 API 응답 기준으로 맞춘다.
- v2 응답의 `category`·`unreadCounts`에 `ETC`가 생기고, 옛 `ETC` 행의 `category`가 `MESSAGE`에서 `ETC`로 바뀐다.
- 기획서(UX-SPEC v0.4 §5.1)는 `기타` 제거, 포인트는 전체에만 노출로 정했으나 이번 요청으로 기타를 두고 포인트를 기타에 넣는다.

## 9. 미결정 사항 (Open Questions)

- 없음. 포인트를 기타에 넣는 것은 기타 탭을 두는 요청에 맞춘 기본값이며, 다른 탭을 원하면 `NotificationType` 탭 값만 바꾸면 된다.

## 10. 참고
- `NotificationCenterFilter`, `NotificationCenterService`(v2 조회·집계 선례)
- 이슈 #887(v1 경로·읽음), #890(v1 안 읽음 탭)
