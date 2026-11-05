# LLD-0124: 워치 IMU 큐가 넘쳐 버린 구간(imu_dropped)을 저장

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #973 |
| 관련 ADR | ADR-0030(센서 배치 v2 원문 보존), ADR-0041(WebSocket 끊김 대응, Proposed) |
| 선행 LLD | [LLD-0041](LLD-0041-sensor-batch-ingest.md)(센서 배치 수신), [LLD-0047](LLD-0047-heart-rate-batch-v2.md)(같은 endpoint의 stream 분기), [LLD-0050](LLD-0050-run-export.md)(내보내기 결측 구간) |
| 작성자 | Claude |
| 작성일 | 2026-11-05 |

## 1. 목적 / 배경

워치는 IMU 큐가 넘치면 가장 오래된 배치를 버리고, 버린 `seq` 구간을 Data Layer 항목(`/imu/dropped/{session_id}/{from_seq}`)으로 남긴다. 폰은 이 항목을 `stream: "imu_dropped"` 본문으로 바꿔 센서 배치 WebSocket(`/app/sensor/batches/send`)으로 보내고, `send()`가 성공하면 지운다(앱 develop `17cd0a1`, `HeartRatePlugin.sensorItemToJson`, 워치 `ImuRemoteDataSourceImpl.recordDrop`).

서버는 `stream`을 `hr`, `imu_watch`, `imu_phone`만 받아 이 기록을 `SENSOR_BATCH_INVALID`로 거절했다. 앱은 이미 지웠으므로 유실 구간 기록이 전부 사라졌다.

데이터정책서 B 1.2.4와 1.2.6은 결측 구간마다 사유를 저장하라고 하고, 작업지시서는 앱이 보내는 사유를 그대로 저장하되 값 목록(합의 대기 S2)은 나중에 바꿀 수 있게 하라고 한다. 완료기준 C2-8은 결측 신고가 실제 공백과 맞는지 본다. 이 기록은 앱이 실제로 버린 구간을 직접 알려 주는 근거다.

## 2. 범위

### In scope
- 변경 모듈: widyu-api, widyu-domain
- 센서 배치 endpoint(WebSocket `/app/sensor/batches/send`, REST `POST /api/v1/sensor/batches`)가 `stream: "imu_dropped"`를 받는다
- 새 테이블 `sensor_dropped_range`에 구간, 사유, 시각, 원문을 저장한다
- 멱등 키 `(member_id, session_id, from_seq)`

### Out of scope
- 내보내기 `missing_intervals`와 `quality.json`에 반영(LLD-0050). 지금은 저장만 하고 내보내기는 별도로 한다
- 회차 귀속(`run_id`). 기록에 `device_id`가 없고, 같은 `session_id`의 `sensor_batch` 행에서 찾을 수 있다
- 사유 값 목록 검증. S2 합의 전이라 64자 이하 문자열이면 받는다
- 앱의 전송 방식(ACK를 기다리지 않음) 변경

## 3. 인터페이스 / API

endpoint와 응답 형식은 LLD-0041과 같다. 본문(앱 develop `17cd0a1`):

```json
{ "stream": "imu_dropped", "session_id": "s-01", "from_seq": 10, "to_seq": 15, "reason": "queue_overflow", "ts_ms": 1760000000200 }
```

| 필드 | 타입 | 필수 | 제약 |
| --- | --- | --- | --- |
| `stream` | string | O | `imu_dropped` |
| `session_id` | string | O | `^[a-z0-9._-]{1,64}$`(IMU 배치와 같다) |
| `from_seq`, `to_seq` | long | O | `>= 0`, `to_seq >= from_seq`. 버린 배치 `seq`의 처음과 끝(양 끝 포함) |
| `reason` | string | O | 1~64자. 현재 워치는 `queue_overflow` |
| `ts_ms` | long | O | 워치가 버린 시각(워치 벽시계 epoch ms) |

`v`, `device_id`, `batch_id`가 없다. 모르는 필드는 거절하지 않는다(원문에 남는다).

응답 `{batchId, seq, result}`: 이 기록에는 `batch_id`가 없어 `batchId`와 `seq`는 null이고 `result`는 `STORED` 또는 `DUPLICATE`다. 앱은 이 기록의 응답을 기다리지 않는다. 검증 실패는 `batch_id`를 읽을 수 없어 LLD-0041과 같이 `/user/queue/errors`(REST는 400 `SENSOR_4003`)로 간다.

## 4. 데이터 모델

`sensor_dropped_range` (엔티티 `com.widyu.sensor.SensorDroppedRange`, widyu-domain, 신규):

| 컬럼 | 타입 | 제약 |
| --- | --- | --- |
| `sensor_dropped_range_id` | BIGINT | PK |
| `member_id` | BIGINT | NOT NULL, FK `member` |
| `session_id` | VARCHAR(64) | NOT NULL |
| `from_seq`, `to_seq` | BIGINT | NOT NULL |
| `reason` | VARCHAR(64) | NOT NULL |
| `ts_ms` | BIGINT | NOT NULL |
| `server_received_at_ms`, `persisted_at_ms` | BIGINT | NOT NULL |
| `payload` | TEXT | NOT NULL, 원문 그대로 |
| `payload_sha256` | CHAR(64) | NOT NULL |
| `created_at`, `updated_at` | DATETIME(6) | NOT NULL |

UK `uk_sensor_dropped_range (member_id, session_id, from_seq)`, 인덱스 `idx_sensor_dropped_range_member_time (member_id, ts_ms)`. DDL `scripts/mysql/create_sensor_dropped_range.sql`.

DTO `SensorDroppedRangeRequest`(widyu-api `sensor/dto/request`), 응답은 기존 `SensorBatchResultResponse`.

## 5. 처리 흐름

1. `SensorBatchService.ingest`: 회원 확인, 본문 크기 확인, `stream` 읽기(기존과 같음). `imu_dropped`를 허용 값에 추가
2. `stream == imu_dropped`면 `SensorDroppedRangeService.record`로 넘긴다. 시계 매핑, S3, `sensor_batch`는 쓰지 않는다
3. 본문을 `SensorDroppedRangeRequest`로 읽고 Bean Validation과 `to_seq >= from_seq`를 본다. 실패면 `SENSOR_BATCH_INVALID`
4. 같은 `(member_id, session_id, from_seq)`가 있으면 `DUPLICATE`
5. 없으면 저장(리포지토리 자체 트랜잭션, 서비스에 `@Transactional` 없음). 유일 키 경합이면 다시 조회해 행이 있을 때만 `DUPLICATE`, 없으면 예외를 그대로 던진다(DeviceHeartbeatService와 같은 규칙)
6. 로그는 `memberId`, `sessionId`, `fromSeq`, `toSeq`, `result`만 남긴다(센서 값 없음)

## 6. 예외 / 에러 처리

새 오류 코드 없음.

| 조건 | 결과 |
| --- | --- |
| 필수 값 없음, 형식 오류, `to_seq < from_seq`, `reason` 64자 초과 | `SENSOR_BATCH_INVALID`(`SENSOR_4003`) |
| 같은 구간이 이미 있음 | `DUPLICATE` |
| 저장 실패(유일 키 경합이 아님) | 예외 전파(WebSocket은 `/user/queue/errors`, REST는 500) |

## 7. 인수조건 (Acceptance Criteria)

- [x] 형식에 맞는 `imu_dropped` 기록을 받으면 구간, 사유, 시각, 원문, 해시를 저장하고 `STORED`를 돌려준다
- [x] 같은 `(회원, session_id, from_seq)`가 이미 있으면 저장하지 않고 `DUPLICATE`를 돌려준다
- [x] 유일 키 경합에서 행이 확인될 때만 `DUPLICATE`, 아니면 예외를 전달한다
- [x] `to_seq < from_seq`이거나 `reason`이 없으면 `SENSOR_BATCH_INVALID`다
- [x] `imu_dropped`는 `sensor_batch`와 S3에 아무것도 쓰지 않는다
- [x] 기존 `hr`, `imu_watch`, `imu_phone` 처리는 바뀌지 않는다
- [x] `./gradlew :backend:widyu-api:test`가 통과한다

## 8. 영향 범위 / 마이그레이션

- **배포 전 DDL 필수:** 운영은 `ddl-auto: validate`라 `scripts/mysql/create_sensor_dropped_range.sql`을 먼저 적용해야 한다. 적용하지 않으면 애플리케이션이 시작하지 않는다
- 개발 서버(`ddl-auto: update`)는 테이블을 자동으로 만든다
- `SensorBatchService` 생성자에 `SensorDroppedRangeService`가 추가된다
- 지금까지 거절된 `imu_dropped` 기록은 앱이 이미 지워 복구할 수 없다

## 9. 미결정 사항 (Open Questions)

- 없음. 사유 값 목록은 정책서 S2 합의 대기이며, 합의되면 검증을 추가한다

## 10. 참고

- 앱 develop `17cd0a1`: `packages/heart_rate/android/.../HeartRatePlugin.kt`(`sensorItemToJson`), `android/wear/.../ImuRemoteDataSourceImpl.kt`(`recordDrop`), `lib/core/model/sensor/sensor_dropped_record.dart`
- 데이터정책서 v1.1 B 1.2.4, 1.2.6, 작업지시서 B 결측 사유, 완료기준 C2-8
