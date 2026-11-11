# LLD-0128: 센서 묶음의 수신 경로와 서버 거절 기록

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #987 |
| 관련 ADR | ADR-0030(센서 배치 v2 원문 보존), ADR-0041(WebSocket 끊김 대응, 후속 4: 경로 태그 카운터) |
| 선행 LLD | [LLD-0041](LLD-0041-sensor-batch-ingest.md)(센서 배치 수신), [LLD-0047](LLD-0047-heart-rate-batch-v2.md)(심박 배치), [LLD-0091](LLD-0091-websocket-slow-path-isolation.md)(WS 실행기), [LLD-0108](LLD-0108-admin-sensor-window-export.md)(기간 내보내기), [LLD-0122](LLD-0122-websocket-text-buffer-sensor-size.md)(WS 본문 크기) |
| 작성자 | Claude |
| 작성일 | 2026-11-09 |

## 1. 목적 / 배경

11/1 시험에서 센서 묶음이 30초 단위로 몰려 들어왔고(가장 늦은 것 33.6초), 묶음 번호와 세션 일부(약 4%)가 빠졌다. 폰은 묶음을 먼저 WebSocket으로 보내고, 소켓이 끊겼거나 10초 안에 ACK가 없으면 30초마다 REST(`POST /api/v1/sensor/batches`)로 다시 보낸다.

서버는 두 가지를 남기지 않는다.

- 묶음이 어느 길로 들어왔는지. 지금은 로그의 스레드 이름(`websocketSensor-*`와 서블릿 스레드)으로만 짐작한다. 늦은 묶음이 REST로 왔다면 폰-서버 연결 문제이고, WebSocket으로 제때 왔다면 워치-폰 구간을 봐야 한다.
- 받은 묶음을 거절했는지. 빠진 묶음 번호가 앱이 안 보낸 것인지 서버가 받고 거절한 것인지 가를 수 없다.

이 LLD는 앞으로 들어오는 묶음부터 두 사실을 남긴다. 11/1 당시 자료는 이 변경으로 복원되지 않는다.

## 2. 범위

### In scope
- 변경 모듈: widyu-api, widyu-domain, 운영 DDL
- `sensor_batch.ingest_channel`: 그 묶음이 **처음 저장된 경로**(`WS` 또는 `REST`). 심박(`hr`)과 IMU(`imu_watch`, `imu_phone`) 모두
- 수신 시도 카운터 `sensor.batch.received{channel, stream, result}`
- 거절 기록 테이블 `sensor_batch_rejection`: 서비스 안에서 거절한 묶음의 식별자, 오류 코드, 경로, 수신 시각. 원문은 저장하지 않는다
- 기간 내보내기(LLD-0108): `sensor_batches.csv`에 `ingest_channel` 열, 새 파일 `rejections.csv`

### Out of scope
- **기존 행 백필.** 과거 묶음의 경로는 추측해 채우지 않는다. 기존 행의 `ingest_channel`은 NULL이다
- **서비스 밖 거절의 DB 기록.** WebSocket 본문 크기 초과(`SensorBatchWebSocketController`가 서비스 호출 전에 `REJECTED` ACK를 보낸다)와 센서 실행기 포화(`WebSocketWorkDispatcher`가 `TEMPORARILY_BUSY`를 보낸다)는 회원, 세션, 그리고 파싱되면 `batch_id`·`seq`까지 식별할 수 있다. 식별이 불가능해서가 아니라 **이번 단계에서는 DB 기록에서 제외**한다. 카운터와 로그로만 남긴다(5.3)
- **일시 실패의 거절 처리.** S3 업로드 실패와 DB 일시 실패는 거절이 아니다. 카운터에 `FAILED`로 세고 기존 오류 로그만 남긴다. 앱이 재전송해야 하므로 `REJECTED`로 바꾸지 않는다
- 회차 내보내기(LLD-0050)에 경로와 거절 기록을 넣는 일
- 거절 기록 자동 삭제. `sensor_batch`, `sensor_dropped_range`와 같이 자동 삭제가 없다

## 3. 인터페이스 / API

수신 API의 요청·응답은 바뀌지 않는다. 바뀌는 것은 서버 내부 호출, 카운터, 내보내기다.

### 3.1 서비스 호출

```java
// REST: SensorBatchController
sensorBatchService.ingest(memberId, payload, SensorIngestChannel.REST);
// WebSocket: SensorBatchWebSocketController.process (센서 실행기)
sensorBatchService.ingest(memberId, payload, serverReceivedAtMs, SensorIngestChannel.WS);
```

경로를 오버로드 종류로 암묵 구분하지 않고 컨트롤러가 명시해서 넘긴다.

### 3.2 카운터 `sensor.batch.received`

태그 키는 항상 `channel`, `stream`, `result` 세 개로 고정한다. 같은 이름의 미터를 다른 태그 키 집합으로 등록하면 Prometheus 등록기가 한쪽을 버리므로, 기존 미터를 재사용하지 않고 새 이름을 쓴다.

| 태그 | 값 |
| --- | --- |
| `channel` | `WS`, `REST` |
| `stream` | `hr`, `imu_watch`, `imu_phone`, `imu_dropped`, `unknown`(본문을 못 읽었거나 목록 밖 값) |
| `result` | `STORED`, `DUPLICATE`, `REJECTED`, `FAILED`(일시 실패) |

센서 실행기 포화는 이 카운터에 들어가지 않는다. 기존 `websocket.work.rejected{api="SensorBatchWebSocketController.ingestBatch"}`가 같은 건수를 센다. WebSocket 수신 시도 전체는 두 미터의 합이다.

### 3.3 기간 내보내기

`GET /api/v1/admin/members/{memberId}/sensor-exports` zip에서 바뀌는 파일:

| 파일 | 변경 |
| --- | --- |
| `sensor_batches.csv` | 마지막 `server_received_at_ms` 뒤에 `ingest_channel` 열 추가. 이 변경 전에 저장된 묶음은 빈 칸 |
| `rejections.csv` (신규) | `sensor_batch_rejection`에서 **서버 수신 시각**(`received_at_ms`)이 내보내는 구간 안인 행. 열: `phase, ts_kst, ts_ms, stream, device_id, batch_id, session_id, seq, error_code, ingest_channel`. `phase`·`ts_*`는 수신 시각 기준 |

`summary.csv`의 `rows:rejections.csv`에 행 수가 남는다. 거절 기록에는 측정 시각이 없어(거절된 본문을 신뢰할 수 없다) 수신 시각으로 고른다. 늦게 온 묶음이 구간 밖으로 빠지지 않게 `padMinutes`를 쓴다.

## 4. 데이터 모델

### 4.1 `SensorIngestChannel` (widyu-domain `com.widyu.sensor`, 신규 enum)

`WS`, `REST`. 컬럼 매핑은 `@Enumerated(EnumType.STRING)` + `@JdbcTypeCode(SqlTypes.VARCHAR)`(MySQL native ENUM을 만들지 않는다, `FcmOutbox.notificationType`과 같다).

### 4.2 `sensor_batch` 칸 추가

| 컬럼 | 타입 | 제약 |
| --- | --- | --- |
| `ingest_channel` | VARCHAR(8) | NULL 허용. 기존 행은 NULL |

중복 수신(`DUPLICATE`)은 기존 행을 돌려주므로 이 칸은 **수신 시도 전체가 아니라 최초 저장 경로**다. WebSocket으로 저장된 뒤 같은 묶음이 REST로 다시 와도 `WS`로 남는다. 재전송 경로의 비중은 3.2 카운터로 본다.

### 4.3 `sensor_batch_rejection` (엔티티 `com.widyu.sensor.SensorBatchRejection`, 신규)

| 컬럼 | 타입 | 제약 |
| --- | --- | --- |
| `sensor_batch_rejection_id` | BIGINT | PK |
| `member_id` | BIGINT | NOT NULL. FK 없음(회원 조회 실패 거절도 남긴다) |
| `device_id` | VARCHAR(64) | NULL |
| `stream` | VARCHAR(20) | NULL |
| `batch_id` | VARCHAR(26) | NULL |
| `session_id` | VARCHAR(64) | NULL |
| `seq` | BIGINT | NULL |
| `error_code` | VARCHAR(32) | NOT NULL. `ErrorCode.getCode()`(예: `SENSOR_4003`) |
| `ingest_channel` | VARCHAR(8) | NOT NULL. `WS`, `REST` |
| `received_at_ms` | BIGINT | NOT NULL. 서버 수신 시각(`sensor_batch.server_received_at_ms`와 같은 시점) |
| `created_at`, `updated_at` | DATETIME(6) | NOT NULL |

인덱스 `idx_sensor_batch_rejection_member_time (member_id, received_at_ms)`. 유일 키는 두지 않는다. 같은 묶음이 재전송마다 거절되면 시도마다 한 행이고, 그 횟수 자체가 관측 대상이다.

식별자는 거절된 본문에서 얕게 읽되 **수신 검증과 같은 형식일 때만** 저장하고 아니면 NULL이다. 로그 주입과 칸 길이 초과를 막는다.

| 칸 | 저장 조건 |
| --- | --- |
| `batch_id` | 문자열이고 `^[a-z0-9]{26}$` |
| `device_id`, `session_id` | 문자열이고 `^[a-z0-9._-]{1,64}$` |
| `stream` | 문자열이고 `^[a-z0-9_]{1,20}$`(목록 밖 값도 남긴다. 옛 앱이 보낸 이름을 찾는 근거다) |
| `seq` | 정수이고 long 범위 |

리포지토리 `SensorBatchRejectionRepository`(widyu-api), 기록 빈 `SensorBatchRejectionRecorder`(widyu-api, `REQUIRES_NEW`).

DDL: `scripts/mysql/create_sensor_batch_rejection.sql`, `scripts/mysql/add_sensor_batch_ingest_channel.sql`.

## 5. 처리 흐름

### 5.1 수신(`SensorBatchService.ingest`)

서비스에는 지금처럼 `@Transactional`이 없다.

1. 기존 처리(회원 확인 → 본문 크기 → `stream` 읽기 → `hr`/`imu_dropped`/IMU 분기)를 그대로 돈다. 처음 저장하는 `sensor_batch` 행에 넘겨받은 경로를 넣는다
2. 정상 응답이면 `result`(`STORED` 또는 `DUPLICATE`)로 카운터를 1 올린다
3. `BusinessException`이 나오면 일시 실패인지 본다. 일시 실패는 `FILE_UPLOAD_FAILED`(S3) 하나이고 WebSocket 컨트롤러의 `REJECTED` ACK 제외 조건과 같은 판정(`SensorBatchService.isTransient`)을 쓴다
   - 일시 실패: 카운터 `FAILED`, 예외를 그대로 던진다
   - 그 밖: 카운터 `REJECTED`, 거절 기록(5.2), 예외를 그대로 던진다
4. 그 밖의 런타임 예외(DB 일시 실패, 유일 키 경합 뒤 행이 확인되지 않은 경우 등): 카운터 `FAILED`, 예외를 그대로 던진다. 거절 기록을 남기지 않는다

서비스 안 거절에는 검증 실패(`SENSOR_4000`~`4003`), 시계 매핑 충돌(`SENSOR_4090`), 같은 `batch_id`에 다른 원문(`SENSOR_4003`), 회차 귀속 실패(`RUN_*`), 회원 조회 실패(`MEMBER_4041`)가 들어간다. REST 본문 크기 초과도 서비스 안에서 걸러지므로 기록된다(본문을 읽기 전이라 식별자는 NULL).

### 5.2 거절 기록

1. `SensorBatchService`가 본문 트리(읽었으면)에서 4.3 규칙으로 식별자를 꺼내 엔티티를 만든다
2. 별도 빈 `SensorBatchRejectionRecorder.record`가 `REQUIRES_NEW` 트랜잭션으로 저장한다
3. **기록 실패는 그 빈 호출 바깥(`SensorBatchService`)에서 `RuntimeException`으로 잡는다.** WARN(회원 ID, 원래 오류 코드, 기록 실패 예외 클래스 이름)만 남기고 원래 `BusinessException`을 던진다. 커밋 시점 예외는 프록시가 메서드 밖에서 던지므로 기록 빈 안에서 잡으면 놓친다(LLD-0056과 같은 이유)
4. 결과적으로 REST는 원래 오류 코드(예: 400 `SENSOR_4003`)를, WebSocket은 원래 `REJECTED` ACK나 `/user/queue/errors`를 그대로 받는다

### 5.3 서비스 밖 거절과 일시 실패

| 경우 | 위치 | DB | 카운터 | 로그 |
| --- | --- | --- | --- | --- |
| WS 본문 크기 초과 | `SensorBatchWebSocketController.ingestBatch` | 기록 제외(이번 단계) | `channel=WS, stream=unknown, result=REJECTED` | 기존 `센서 배치 거절: memberId, batchId, seq, stream, code` |
| 센서 실행기 포화 | `WebSocketWorkDispatcher.dispatch` | 기록 제외(이번 단계) | 기존 `websocket.work.rejected{api}` | 기존 WARN(api, memberId, sessionId) |
| S3 업로드 실패 | `SensorBatchService` | 없음(거절 아님) | `result=FAILED` | 기존 오류 로그 |
| DB 일시 실패 | `SensorBatchService` | 없음(거절 아님) | `result=FAILED` | 기존 오류 로그 |

본문 크기 초과와 실행기 포화 로그는 센서 실행기에 들어가기 전에 나오므로 스레드 이름이 `websocketSensor-*`가 아니다. 로그 분석에서 WS 경로는 스레드 이름이 아니라 로거(`SensorBatchWebSocketController`, `WebSocketWorkDispatcher`)와 메시지로 식별한다.

로그에는 지금처럼 센서 값과 bpm을 남기지 않는다(정책 1.6.7, #639). 거절 기록에도 원문을 저장하지 않는다.

### 5.4 기간 내보내기

`SensorWindowExportService.export`가 `SensorBatchRejectionRepository.findByMemberIdAndReceivedAtMsBetweenOrderByReceivedAtMsAsc`로 행을 읽어 `rejections.csv`를 `dropped_ranges.csv` 다음에 쓴다. 다른 조회와 같이 자기 읽기 트랜잭션에서 끝난다.

## 6. 예외 / 에러 처리

새 오류 코드 없음. 수신 API의 응답은 바뀌지 않는다.

| 조건 | 결과 |
| --- | --- |
| 서비스 안 거절 | 원래 오류(REST 4xx, WS `REJECTED` ACK 또는 `/user/queue/errors`) + 거절 기록 한 행 |
| 거절 기록 저장 실패 | 원래 오류 그대로, WARN 한 줄 |
| S3·DB 일시 실패 | 원래 오류(REST 500, WS `/user/queue/errors`), 거절 기록 없음 |
| WS 본문 크기 초과, 실행기 포화 | 기존 동작 그대로, 거절 기록 없음 |

## 7. 인수조건 (Acceptance Criteria)

- [x] REST로 처음 저장된 IMU·심박 묶음의 `ingest_channel`은 `REST`, WebSocket으로 처음 저장된 묶음은 `WS`다
- [x] 이미 저장된 묶음이 다시 오면(`DUPLICATE`) 기존 행의 경로를 바꾸지 않는다
- [x] 서비스 안 거절(검증 실패, 시계 매핑 충돌)은 회원, 기기, 자료 종류, `batch_id`, `session_id`, `seq`, 오류 코드, 경로, 수신 시각을 담은 거절 기록 한 행을 남기고 원래 예외를 던진다
- [x] 본문을 읽지 못한 거절이나 형식이 틀린 식별자는 해당 칸을 NULL로 남긴다
- [x] 거절 기록 저장이 실패해도 원래 `BusinessException`(같은 오류 코드)이 그대로 나간다
- [x] S3 업로드 실패와 DB 일시 실패는 거절 기록을 남기지 않고 `result=FAILED`로 센다
- [x] 수신 시도마다 `sensor.batch.received{channel, stream, result}`가 1 오른다(`STORED`, `DUPLICATE`, `REJECTED`, `FAILED`)
- [x] WebSocket 본문 크기 초과는 서비스를 부르지 않고 `channel=WS, stream=unknown, result=REJECTED`로 센다
- [x] 기간 내보내기 `sensor_batches.csv`에 `ingest_channel` 열이 있고, 수신 시각이 구간 안인 거절 기록이 `rejections.csv`에 한 줄씩 들어간다. 없으면 머리글만 있다
- [x] 로그와 거절 기록에 센서 값·bpm·원문이 없다
- [x] `bash scripts/harness/verify.sh`가 통과한다

## 8. 영향 범위 / 마이그레이션

- **운영 DDL 선적용 필수.** 운영은 `ddl-auto: validate`라 배포 전에 아래 두 스크립트를 적용해야 한다. 적용하지 않으면 애플리케이션이 시작하지 않는다
  1. `scripts/mysql/add_sensor_batch_ingest_channel.sql` — `ALTER TABLE sensor_batch ADD COLUMN ingest_channel VARCHAR(8) NULL`
  2. `scripts/mysql/create_sensor_batch_rejection.sql` — 새 테이블
- 새 칸은 NULL 허용 VARCHAR라 기존 행·기존 버전 애플리케이션과 충돌하지 않는다. 먼저 적용하고 배포해도 된다
- MySQL native ENUM을 쓰지 않으므로 값 추가 때 `ALTER ... MODIFY`가 필요 없다
- 개발 서버(`ddl-auto: update`)는 칸과 테이블을 자동으로 만든다
- `SensorBatchService.ingest` 시그니처가 바뀐다(경로 인자 추가). 호출부는 두 컨트롤러뿐이다
- `SensorBatchService` 생성자에 `SensorBatchRejectionRecorder`, `MeterRegistry`가, `SensorBatchWebSocketController` 생성자에 `MeterRegistry`가 추가된다
- 거절 한 건마다 DB 쓰기 한 번이 늘어난다. 거절은 정상 수신보다 드물고, 기록 실패가 응답을 막지 않는다
- **연구 철회 수동 삭제 대상에 넣는다.** 거절 기록에는 원문이 없지만 회원·기기·묶음 식별자가 남는다. `run_id`가 없어 회원과 회차 기간의 수신 시각, 또는 회차 배치의 `batch_id`로 찾는다(`docs/operations/study-withdrawal-procedure.md`)

## 9. 미결정 사항 (Open Questions)

- 없음

## 10. 참고

- 노션 요청 「늦게 온 묶음과 빠진 묶음을 서버 기록으로 가려 주기」(11/1 12:53~13:48 진욱 님 워치 8), 이슈 1-1·2-1
- ADR-0041 후속 4(전송 경로와 유실 관측: 경로 태그 카운터 제안)
- 이전 사례: 같은 이름 미터에 다른 태그 키 집합을 쓰면 Prometheus 등록기가 한쪽을 버린다(PR #981 검수)
