# LLD-0129: 실험 모드의 기기 상태 바뀜 기록(device_event)

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #990 |
| 관련 ADR | ADR-0030(센서 배치 v2 원문 보존) |
| 선행 LLD | [LLD-0049](LLD-0049-device-heartbeat.md)(기기 하트비트), [LLD-0045](LLD-0045-collection-run.md)(회차 귀속 `resolveRun`), [LLD-0108](LLD-0108-admin-sensor-window-export.md)(기간 내보내기) |
| 작성자 | Claude |
| 작성일 | 2026-11-09 |

## 1. 목적 / 배경

실험 모드 시험에서 워치 충전, 착용 바뀜, 폰 연결 끊김 같은 상태 변화를 사람이 손으로 적고 있어 서버 자료와 시각이 어긋난다. 노션 요청 「실험 모드에서 받을 수 있는 자료를 전부 기록하게 하기」(11/4)에서 진욱 님이 기록 목록을 정했다(11/5 답글 1~4번, 1~3번 먼저).

1번(폰 상태 5분 기록)은 기기 하트비트(`device_heartbeat`, LLD-0049)에 칸이 모두 있다. 2~3번의 **상태가 바뀐 시각**은 60초 주기 스냅샷인 하트비트나 IMU 결측 구간(`imu_dropped`, LLD-0124)으로 표현할 수 없다. 바뀐 시각 한 점과 종류별 값을 담을 기록을 새로 만든다.

## 2. 범위

### In scope
- 변경 모듈: widyu-api, widyu-domain, 운영 스크립트·문서(DDL, ERD, 연구 철회 절차)
- 새 테이블 `device_event`와 묶음 수신 API `POST /api/v1/device/events`(이벤트 배열, 항목별 결과)
- 앱 생성 `event_id`로 멱등, 항목 원문과 sha256 보존, 열린 회차 귀속(`resolveRun` 재사용)
- 관리자 기간 내보내기(LLD-0108) zip에 `device_events.csv` 추가
- 진욱 님 답글 2~3번의 종류 값 확정, 「확인만」 항목과 4번 항목은 종류 값만 예약

### Out of scope
- 1번 폰 상태: 기존 하트비트(REST `POST /api/v1/device/heartbeats`, LLD-0049)로 받으며 칸이 이미 있다. 2026-11-09 확인 기준 앱이 아직 보내지 않는다(개발 서버 `device_heartbeat` 0행, 요청 0건). 실험 모드에서 바뀌면 바로, 아니면 5분마다 보내는 것은 앱 일이다
- 실험 모드 여부 판단: 서버는 모드와 무관하게 받는다. 실험 모드일 때만 기록하는 것은 앱이 정한다
- 회차 내보내기(run zip, LLD-0050) 포함: 연구팀이 어느 내보내기를 쓰는지 확인한 뒤 정한다
- 종류별 `attributes` 키 검증: 서버는 JSON 객체인지만 본다(4절 권장 키는 계약 문서일 뿐)
- WebSocket 수신 경로: 전송은 기존 송수신 주기에 얹어도 된다는 최신 답글에 따라 REST 묶음 하나만 둔다
- 본문 `run_id` 지정: 하트비트와 달리 받지 않는다. 회차는 서버가 기기·시각으로 찾는다

## 3. 인터페이스 / API

### `POST /api/v1/device/events`

인증된 회원(액세스 토큰)이 보낸다. 회원은 본문으로 받지 않는다. 본문은 **이벤트 객체의 JSON 배열**이다.

```json
[
  { "event_id": "0b6f2c1e-6a4d-4a51-9a47-5b0b8f7c1d20", "device_id": "gw-3f2a", "source": "watch",
    "kind": "WEAR_CHANGED", "occurred_at_ms": 1762650000123, "session_id": "s-20261109-01",
    "attributes": { "on_body": false, "sensor_raw": 0 } },
  { "event_id": "7d1e9a40-2c55-4f7e-8b61-0f3c2a9e4b11", "device_id": "ph-9c1", "source": "phone",
    "kind": "NETWORK_CHANGED", "occurred_at_ms": 1762650001500,
    "attributes": { "type": "none" } }
]
```

| 필드 | 타입 | 필수 | 제약 |
| --- | --- | --- | --- |
| `event_id` | string | O | `^[A-Za-z0-9._-]{1,64}$`. 앱이 만든 고유값(UUID 권장). 멱등 키 |
| `device_id` | string | O | `^[a-z0-9._-]{1,64}$`(하트비트와 같다). 상태가 바뀐 기기. 워치 이벤트면 워치 ID |
| `source` | string | O | `watch` 또는 `phone` |
| `kind` | string | O | 1~64자. 값 목록은 아래 표. **목록에 없는 값도 저장한다** |
| `occurred_at_ms` | long | O | `> 0`. 상태가 바뀐 시각(그 기기의 벽시계 epoch ms) |
| `session_id` | string | X | `^[a-z0-9._-]{1,64}$` |
| `attributes` | object | X | 종류별 값. JSON 객체만 받는다(배열·문자열이면 거절) |

모르는 필드는 거절하지 않는다(원문에 남는다). 한 항목의 원문은 4,096바이트 이하, 한 요청은 1~500개다.

#### 종류 값(`kind`)

대문자 스네이크 문자열이다. `source`가 기기를 가리키므로 종류 이름에 워치·폰을 붙이지 않는다. `attributes`의 키는 권장값이며 서버는 검증하지 않는다.

| 상태 | `kind` | 주 출처 | 권장 `attributes` |
| --- | --- | --- | --- |
| 확정 | `CHARGING_STARTED`, `CHARGING_STOPPED` | watch | `battery_pct` |
| 확정 | `WEAR_CHANGED` | watch | `on_body`(앱 판정값), `sensor_raw`(착용 센서 원래 값) |
| 확정 | `PHONE_DISCONNECTED`, `PHONE_RECONNECTED` | watch | 없음 |
| 확정 | `POWER_SAVE_CHANGED` | watch | `enabled` |
| 확정 | `BRIGHTNESS_CHANGED` | watch | `level` |
| 확정 | `SCREEN_ON`, `SCREEN_OFF` | watch | 없음 |
| 확정 | `SENT_TO_PHONE` | watch | `batch_count`, `bytes`. `occurred_at_ms`는 보낸 시각 |
| 확정 | `NETWORK_CHANGED` | phone | `type`: `wifi`·`mobile`·`none` |
| 확정 | `LOCATION_STOPPED`, `LOCATION_RESTARTED` | phone | `reason` |
| 예약(확인만) | `SCREEN_ON`, `SCREEN_OFF`(source=phone), `UNLOCKED` | phone | 없음 |
| 예약(확인만) | `DOZE_CHANGED` | phone | `idle` |
| 예약(4번) | `BATTERY_STATUS` | watch·phone | `temperature_c`, `voltage_mv` |
| 예약(4번) | `APP_STARTED`, `APP_EXITED` | watch·phone | `reason` |
| 예약(4번) | `GNSS_STATUS` | phone | `satellites` |

**모르는 종류를 거절하지 않는 이유**: 앱이 서버보다 먼저 새 종류(예약 항목, 시험 중 추가 항목)를 보낼 수 있다. 거절하면 앱은 그 기록을 지우거나 계속 재전송해야 하고, 어느 쪽이든 시험 자료가 사라지거나 쌓인다. 저장해 두면 종류 목록은 나중에 문서만 고치면 된다. 길이 상한(64자)만 둬 컬럼을 넘지 않게 한다.

#### 응답

항상 200이며 요청 순서 그대로 항목마다 결과를 돌려준다.

```json
{
  "code": "200",
  "message": "기기 이벤트를 받았습니다.",
  "data": [
    { "eventId": "0b6f2c1e-6a4d-4a51-9a47-5b0b8f7c1d20", "result": "STORED" },
    { "eventId": "7d1e9a40-2c55-4f7e-8b61-0f3c2a9e4b11", "result": "DUPLICATE" }
  ]
}
```

| `result` | 뜻 | 앱 처리 |
| --- | --- | --- |
| `STORED` | 새로 저장 | 보관분에서 지운다 |
| `DUPLICATE` | 같은 `(device_id, event_id)`가 이미 있음 | 보관분에서 지운다 |
| `REJECTED` | 형식 오류(필수 값 없음, 형식·길이 위반, 객체 아님, 4,096바이트 초과) | 다시 보내도 같은 결과다. 재전송하지 않는다 |

`REJECTED` 항목은 `event_id`를 읽지 못했으면 `eventId`가 null이다. 응답 순서로 대응시킨다.

#### 재전송 계약

- 「바뀌면 즉시 기록」과 「즉시 전송」은 다르다. 앱은 바뀐 시각에 한 줄을 기록하고, 전송은 기존 송수신 주기에 얹어도 된다
- 끊긴 동안 쌓인 기록은 앱이 보관했다가 재연결 뒤 보내고, **항목별 `STORED`·`DUPLICATE`를 받은 뒤에만 지운다**
- 요청 전체가 실패(네트워크, 5xx)하면 같은 묶음을 그대로 다시 보낸다. 이미 저장된 항목은 `DUPLICATE`가 된다
- 서버는 순서와 지연을 가정하지 않는다. 늦게 온 기록도 `occurred_at_ms` 그대로 저장한다

## 4. 데이터 모델

`device_event` (엔티티 `com.widyu.device.DeviceEvent`, widyu-domain, 신규):

| 컬럼 | 타입 | 제약 |
| --- | --- | --- |
| `device_event_id` | BIGINT | PK |
| `member_id` | BIGINT | NOT NULL, FK `member` |
| `event_id` | VARCHAR(64) | NOT NULL |
| `device_id` | VARCHAR(64) | NOT NULL |
| `source` | VARCHAR(10) | NOT NULL, `watch`·`phone` |
| `kind` | VARCHAR(64) | NOT NULL |
| `occurred_at_ms` | BIGINT | NOT NULL |
| `session_id` | VARCHAR(64) | NULL |
| `attributes` | TEXT | NULL. `attributes` 객체의 JSON 문자열 |
| `run_id` | VARCHAR(64) | NULL. 회차 귀속, 없으면 운영 외 자료 |
| `server_received_at_ms` | BIGINT | NOT NULL. 요청을 받은 시각 |
| `payload` | TEXT | NOT NULL. 이 항목의 원문 바이트 그대로 |
| `payload_sha256` | CHAR(64) | NOT NULL |
| `created_at`, `updated_at` | DATETIME(6) | NOT NULL |

UK `uk_device_event (device_id, event_id)`, 인덱스 `idx_device_event_member_time (member_id, occurred_at_ms)`. `source`·`kind`는 MySQL ENUM이 아니라 VARCHAR다(모르는 종류를 받고, enum 값 추가 때 ALTER가 필요 없다). DDL `scripts/mysql/create_device_event.sql`.

DTO: `DeviceEventRequest`(`device/dto/request`), `DeviceEventResponse`·`DeviceEventResult`(`device/dto/response`). 리포지토리 `DeviceEventRepository`(widyu-api).

## 5. 처리 흐름

`DeviceEventService.ingest(memberId, byte[] payload)`. 서비스에 `@Transactional`을 두지 않고 저장은 리포지토리 자체 트랜잭션에서 한다(DeviceHeartbeatService와 같다).

1. 회원이 없으면 `MEMBER_NOT_FOUND`
2. 본문을 스트리밍 파서로 읽는다. 최상위가 배열이 아니거나, JSON 문법이 깨졌거나, 0개이거나 500개를 넘으면 요청 전체를 `DEVICE_EVENT_BATCH_INVALID`로 거절한다
3. 배열 원소마다 원문 바이트 구간을 잘라 낸다(재직렬화하지 않는다). 객체가 아니거나 4,096바이트를 넘으면 `REJECTED`
4. 잘라 낸 원문을 `DeviceEventRequest`로 읽고 Bean Validation을 돌린다. 실패하면 `REJECTED`. 다른 항목은 계속 처리한다
5. 같은 `(device_id, event_id)`가 있으면 `DUPLICATE`
6. `CollectionRunService.resolveRun(memberId, device_id, occurred_at_ms)`로 열린 회차를 찾아 `run_id`를 정한다. 없으면 null
7. 저장한다. 유일 키 경합이면 다시 조회해 행이 있을 때만 `DUPLICATE`, 없으면 예외를 그대로 던진다(앞 항목은 이미 저장됐고, 앱이 묶음을 다시 보내면 `DUPLICATE`가 된다)
8. 요청마다 한 줄 로그: `memberId`, 기기 ID 목록, 건수, 결과별 건수. **`attributes`·원문 값은 어떤 로그 레벨에도 남기지 않는다**(배터리, 위치 수집 사유 등이 들어 있다)

### 기간 내보내기(LLD-0108)

`device_events.csv`: 한 회원의 `occurred_at_ms`가 내보내는 구간 안인 행을 시각순으로.

| 열 | 값 |
| --- | --- |
| `phase`, `ts_kst`, `ts_ms` | `occurred_at_ms` 기준 |
| `device_id`, `source`, `kind` | 저장값 |
| `attributes` | JSON 문자열. 쉼표·따옴표가 있으므로 CSV 규칙대로 큰따옴표로 감싼다 |
| `session_id`, `server_received_at_ms` | 저장값 |

조회: `DeviceEventRepository.findByMemberIdAndOccurredAtMsBetweenOrderByOccurredAtMsAsc` — 인덱스 `(member_id, occurred_at_ms)`.

## 6. 예외 / 에러 처리

| 조건 | 결과 |
| --- | --- |
| 회원 없음 | 404 `MEMBER_4041` |
| 본문이 배열이 아님, JSON 문법 오류, 0개, 500개 초과 | 400 `DEVICE_4001` `DEVICE_EVENT_BATCH_INVALID`(신규) |
| 항목 형식 오류, 객체 아님, 4,096바이트 초과 | 그 항목만 `REJECTED` |
| 같은 `(device_id, event_id)` | 그 항목만 `DUPLICATE` |
| 저장 실패(유일 키 경합이 아님) | 예외 전파(500). 앞 항목 저장은 유지된다 |

## 7. 인수조건 (Acceptance Criteria)

- [x] 형식에 맞는 이벤트 배열을 보내면 항목마다 원문, 해시, 종류, 바뀐 시각, `attributes` JSON, 서버 수신 시각을 저장하고 요청 순서대로 `STORED`를 돌려준다
- [x] 같은 `(device_id, event_id)`를 다시 보내면 새 행 없이 `DUPLICATE`를 돌려준다
- [x] 유일 키 경합에서 행이 확인될 때만 `DUPLICATE`, 아니면 예외를 전달한다
- [x] 한 항목의 형식 오류(필수 값 없음, `source` 값 오류, 객체 아님, `attributes`가 객체 아님, 4,096바이트 초과)는 그 항목만 `REJECTED`이고 같은 묶음의 다른 항목은 저장된다
- [x] 목록에 없는 `kind`도 64자 이하면 저장한다
- [x] 본문이 배열이 아니거나 비었거나 500개를 넘으면 `DEVICE_EVENT_BATCH_INVALID`다
- [x] 열린 회차가 있으면 `run_id`를 붙이고 없으면 null로 저장한다
- [x] 기간 내보내기 zip에 `device_events.csv`가 들어가고 `attributes`가 CSV 규칙대로 감싸진다. 기록이 없어도 헤더만 있는 파일이 들어간다
- [x] 로그에 `attributes`·원문 값이 없다(memberId, 기기 ID, 건수, 결과만)
- [x] `./gradlew :backend:widyu-api:test`가 통과한다

## 8. 영향 범위 / 마이그레이션

- **운영 배포 전 DDL 필수:** 운영은 `ddl-auto: validate`라 `scripts/mysql/create_device_event.sql`을 먼저 적용해야 한다. 적용하지 않으면 애플리케이션이 시작하지 않는다
- 개발 서버(`ddl-auto: update`)는 테이블을 자동으로 만든다. ENUM 컬럼이 없어 ALTER는 필요 없다
- 새 API라 기존 앱 동작은 바뀌지 않는다. 앱(인협 님)이 3절 계약대로 보내기 시작해야 자료가 쌓인다
- 기간 내보내기 zip에 파일이 하나 늘어난다. 기존 파일의 열은 바뀌지 않는다
- 연구 철회 삭제 절차(`docs/operations/study-withdrawal-procedure.md`)에 `device_event`를 `device_heartbeat`와 같은 방식(`run_id` 기준 캡처, 삭제, 사후 0건 확인)으로 추가한다. 회차 밖(`run_id` null) 행은 하트비트와 같이 이 절차 대상이 아니다

## 9. 미결정 사항 (Open Questions)

- 없음. 회차 내보내기 포함 여부는 연구팀이 쓰는 내보내기를 확인한 뒤 정하며, 이번 범위 밖으로 둔다(2절)

## 10. 참고

- 노션 요청 「실험 모드에서 받을 수 있는 자료를 전부 기록하게 하기」 본문과 댓글(석진욱 11/5 답글 1~4번, 추가 요청 폰 화면·잠금 해제·Doze, 「기존 송수신 주기에 얹어도 됨」 답글)
- LLD-0049 하트비트의 원문 보존·유일 키 경합 처리, LLD-0124 결측 구간 기록
