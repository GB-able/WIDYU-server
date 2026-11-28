# LLD-0191: 보호자 카드의 체류 시간을 안심구역 상태가 바뀐 시각부터 센다

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #1178 |
| 관련 ADR | ADR-0044. 상태 저장은 [LLD-0141](LLD-0141-location-freshness-and-stay-state-db.md), 알림 묶음은 [LLD-0190](LLD-0190-safe-zone-return-after-window.md) 그대로 |
| 작성자 | Claude |
| 작성일 | 2026-11-28 |

## 1. 목적 / 배경

보호자 앱 위치 탭 카드의 「N시간 N분동안 휴식 중이에요 / 활동 중이에요」는 서버 응답 `stayDurationMinutes`(지금 - `stayStartTime`)를 그대로 보여 준다. 보호자는 이 값을 「집에 온 지」, 「외출한 지」로 읽는다.

지금 `stayStartTime`은 `senior_stay_state.stay_started_at`이고, 위치가 체류 지점에서 30m 넘게 벗어날 때마다 지금 시각으로 바뀐다. 그래서 외출 중 걸어 다니면 늘 0분이고, 집에서 GPS가 30m 넘게 튀어도 0으로 돌아간다. 개발 서버 2026-11-27 21:23: 회원 37은 18:02에 안심구역을 나갔는데 「0시간 0분 활동 중」, 회원 28은 21:12 GPS 튐 뒤 「11분 휴식 중」이었다.

## 2. 범위

### In scope
- 변경 모듈: widyu-domain(`SeniorStayState`), widyu-api(`LocationJudgeService`, `LocationQueryService`, `StayInfo`, Swagger 설명, Flyway `V8`)
- `senior_stay_state.zone_changed_at` 추가와 기존 행 채우기
- 실시간 방송, 발신 세션 ACK, REST 마지막 위치의 `stayStartTime`, `stayDurationMinutes` 기준 변경

### Out of scope
- 30m 체류 판정과 `stay_started_at`의 뜻. 같은 자리 판단과 체류 좌표는 그대로다
- 안심구역 이탈, 복귀 알림 규칙(`applySafeZoneTransition`, LLD-0190)
- 경계 튐 확정 지연(이력, hysteresis). 집 경계에서 한 번 밖으로 튀면 구역 상태가 바뀐 것으로 보고 새로 센다. 튐 자체를 거르는 일은 #1179에서 한다
- 앱 코드. 앱은 기존 필드를 그대로 보여 준다

## 3. 인터페이스 / API

경로와 필드는 그대로이고 두 필드의 뜻만 바뀐다. 대상: REST `GET /api/v1/location/realtime/senior/{seniorId}`, 방송 `/topic/location/senior/{id}`, ACK `/user/queue/location/ack`(모두 `LocationUpdateResponse`).

| 필드 | 이전 | 이후 |
| --- | --- | --- |
| `stayStartTime` | 마지막으로 30m 넘게 움직인 시각(`stay_started_at`) | 안심구역 상태가 마지막으로 바뀐 시각(`zone_changed_at`). 안심구역이 하나도 없는 시니어는 이전과 같다 |
| `stayDurationMinutes` | 지금 - 위 시각(분) | 같음 |

체류 상태가 없으면 둘 다 null인 규칙(LLD-0141)은 그대로다.

## 4. 데이터 모델

`senior_stay_state`(widyu-domain `SeniorStayState`)에 한 칸을 더한다.

| 컬럼 | 타입 | 뜻 |
| --- | --- | --- |
| `zone_changed_at` | DATETIME(6) NOT NULL | 안심구역 상태(어느 안심구역 안, 또는 밖)가 마지막으로 바뀐 서버 시각 |

`db/migration/V8__senior_stay_state_zone_changed_at.sql`: NULL 허용으로 추가 → 기존 행을 `stay_started_at`으로 채움 → NOT NULL. 기본값은 두지 않는다.

`StayInfo`(widyu-api DTO)에 `zoneChangedAt`을 더한다. 예전 Redis 체류 값을 읽을 때(`LegacyStayStateImporter`)는 이 칸이 없어 null이고, 가져오기는 쓰지 않는다.

## 5. 처리 흐름

위치 한 건의 판정 트랜잭션(LLD-0141 5.1)은 그대로이고, 상태를 바꾸는 순간의 규칙만 더한다.

1. 같은 자리(30m 안) 위치: 지금처럼 행을 읽기만 하고 쓰지 않는다. 응답은 행의 `zone_changed_at`을 쓴다
2. 행이 없어 처음 만들 때(`SeniorStayState.start`): `zone_changed_at = now`
3. 30m 넘게 움직여 옮길 때(`moveTo`): 아래 중 하나면 `zone_changed_at = now`, 아니면 유지
   - 안심구역 종류(`location_type`: `HOME`, `OTHER`, 밖이면 null)가 바뀌었다
   - 안심구역 이름(`location_name`)이 바뀌었다. 다른 안심구역으로 바로 옮긴 경우다(`OTHER` 병원 → `OTHER` 경로당)
   - 그 시니어가 등록한 안심구역이 하나도 없다(`decideStay`가 이미 읽는 안심구역 목록이 비었다). 구역 상태가 바뀌지 않아 시간이 끝없이 늘어나는 것을 막으려고 예전처럼 30m 이동마다 새로 센다
4. 예전 Redis 상태 가져오기(`SeniorStayState.imported`): 구역 상태가 바뀐 시각을 모르므로 `zone_changed_at = stay_started_at`
5. 응답 조립: 방송(`LocationJudgeService.judge`)과 REST(`LocationQueryService.getLastLocation`) 모두 `StayInfo.zoneChangedAt()`을 `stayStartTime`으로 넘긴다

다른 안심구역 판단에 구역 ID 대신 종류와 이름을 쓴 이유: 상태 행에 구역 ID가 없고, 이름이 다른 두 구역을 구분하는 데는 종류와 이름으로 충분하다. ID 칸을 더하면 스키마 변경과 가져오기 경로가 늘어난다. 한계는 9절에 적었다.

## 6. 예외 / 에러 처리

새 오류 없음. 상태 저장 실패는 LLD-0141 6절대로 트랜잭션 전체가 롤백된다.

## 7. 인수조건 (Acceptance Criteria)

- [x] AC1. 안심구역을 등록한 시니어가 밖에서 30m 넘게 여러 번 움직여도 `zone_changed_at`은 나간 시각 그대로이고 `stay_started_at`만 바뀐다(`SeniorStayStateZoneTest`)
- [x] AC2. 집 구역 안에서 30m 넘게 움직여도 집에 들어온 시각이 유지된다(`SeniorStayStateZoneTest`)
- [x] AC3. 밖 → 집, 다른 이름의 안심구역으로 옮기면 그 시각부터 다시 센다(`SeniorStayStateZoneTest`)
- [x] AC4. 안심구역이 하나도 없는 시니어는 30m 이동마다 새로 센다. 방송의 체류 시간이 0분이다(`SeniorStayStateZoneTest`, `RealtimeLocationServiceTest`)
- [x] AC5. 방송 응답의 `stayStartTime`은 나간 시각이고 `stayDurationMinutes`가 그 시각부터 늘어난다(`RealtimeLocationServiceTest`)
- [x] AC6. REST 마지막 위치의 `stayStartTime`은 집 안 이동 뒤에도 들어온 시각이다(`RealtimeLocationServiceTest`)
- [x] AC7. 실제 Spring 트랜잭션에서 나간 뒤 밖에서 움직여도 응답 체류 시작이 같고, 그 시각이 상태 행 `zone_changed_at`에 저장된다(`StayStateOutboxAtomicityTest`, H2)
- [x] AC8. 예전 Redis 상태를 가져오면 `zone_changed_at = stay_started_at`이다(`SeniorStayStateZoneTest`, `LegacyStayStateImporterTest`)
- [x] AC9. 빈 MySQL에 버전 파일을 모두 적용하면 엔티티 검증을 통과하고 `zone_changed_at`은 기본값 없는 NOT NULL이다(`FlywayMigrationMySqlTest`). V5까지 적용된 DB의 기존 행은 V8 뒤 `stay_started_at`과 같은 값이다(수동 확인, PR 본문)
- [x] AC10. 이탈, 복귀 알림과 같은 자리 무쓰기 규칙이 그대로다(`SafeZoneAlertServiceTest`, `StayStateOutboxAtomicityTest`, `RealtimeLocationServiceTest`)
- [x] `./gradlew :backend:widyu-api:test`가 통과한다

## 8. 영향 범위 / 마이그레이션

- **배포:** Flyway가 기동 때 V8을 적용한다(dev, prod `ddl-auto: validate`). 기존 행은 `stay_started_at`으로 채워 배포 직후 화면 값은 지금과 같고, 다음 구역 상태 변화부터 새 기준으로 센다. 밖에서 걷던 시니어는 다음 30m 이동 때도 배포 시점의 값이 유지되므로 「나간 지」보다 짧게 보일 수 있다(배포 전 마지막 이동 시각부터 센다)
- **버전 번호:** 열린 PR #1160(V6), #1162(V7)보다 이 PR이 먼저 머지되면 그쪽이 V9 이후로 번호를 올려야 한다. 낮은 번호가 나중에 오면 Flyway가 적용하지 않은 버전으로 보고 기동을 멈춘다(LLD-0154)
- **롤백:** 이전 버전은 `zone_changed_at`을 모르며, NOT NULL에 기본값이 없어 이전 버전이 새 행을 INSERT하면 실패한다. 이전 버전으로 되돌릴 때는 `ALTER TABLE senior_stay_state DROP COLUMN zone_changed_at`을 먼저 실행한다(Flyway 기록 V8 행도 지운다)
- **같은 클래스 병렬 작업:** #1179(경계 튐 확정 지연)가 `LocationJudgeService`, `SeniorStayState`를 함께 고친다. 나중에 머지되는 쪽이 충돌을 해소한다
- `backend/CLAUDE.md` location 절, ERD 갱신

## 9. 미결정 사항 (Open Questions)

- 없음. 아래는 알고 받아들인 한계다
  - 보호자가 안심구역 이름을 바꾸면 다음 30m 이동 때 구역이 바뀐 것으로 보고 새로 센다
  - 안심구역 75m 반경이 겹치는 곳에서는 목록의 첫 구역으로 정하므로(기존 규칙) 겹친 자리에서 움직이면 구역이 바뀐 것으로 보일 수 있다
  - 집 경계에서 GPS가 한 번 밖으로 튀면 구역 상태가 바뀌어 새로 센다. 튐을 거르는 확정 지연은 #1179 범위다

## 10. 참고

- 보호자 앱이 `stayDurationMinutes`를 그대로 보여 주는 것은 앱 코드로 확인했다(앱은 바꾸지 않는다)
