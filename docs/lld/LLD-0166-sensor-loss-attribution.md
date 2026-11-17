# LLD-0166: 빠진 센서 묶음의 원인 가리기 (워치 버림, 서버 거절, 수신 경로)

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #1089 |
| 관련 ADR | ADR-0030(센서 배치 v2 원문 보존), ADR-0032(회차 내보내기), ADR-0041(WebSocket 끊김 대응) |
| 선행 LLD | [LLD-0124](LLD-0124-imu-dropped-range.md)(버린 구간), [LLD-0128](LLD-0128-sensor-ingest-channel-rejection-log.md)(수신 경로, 거절 기록), [LLD-0050](LLD-0050-run-export.md)(회차 내보내기), [LLD-0108](LLD-0108-admin-sensor-window-export.md)(기간 내보내기), [LLD-0091](LLD-0091-websocket-slow-path-isolation.md)(WS 실행기) |
| 작성자 | Claude |
| 작성일 | 2026-11-17 |

## 1. 목적 / 배경

센서 묶음 번호(`seq`)가 중간에 빠지는데, 빠진 묶음을 워치가 버렸는지 서버가 받고 거절했는지 끝까지 가릴 수 없다. 지금 서버에는 일부만 있다.

- **워치 버림 기록**: 워치 IMU 큐 넘침(`stream: "imu_dropped"`, 사유 `queue_overflow`)만 받는다. 심박 큐 넘침, 대기열에 넣기 실패는 워치 로그(`Log.w`)에만 남는다. 사유 값 이름도 정해져 있지 않다. 회차 내보내기에는 없다.
- **서버 거절 기록**: 서비스 안 검증 거절만 `sensor_batch_rejection`에 남는다. WebSocket 본문 크기 초과와 센서 실행기 포화는 지표로만 센다(LLD-0128 5.3에서 "이번 단계 제외"로 미뤘다).
- **대조**: 빠진 번호를 버림 기록, 거절 기록과 맞춰 보는 수단이 없어 사람이 CSV 세 개를 손으로 대조한다.
- **수신 경로**: `sensor_batch.ingest_channel`(WS/REST)은 기간 내보내기에만 있고 회차 내보내기에는 없다.

이 LLD는 앞으로 들어오는 묶음부터 네 가지를 채운다. 11/1 시험 자료는 복원되지 않는다.

## 2. 범위

PR 세 개로 나눈다(파일 15개 초과). 각 PR은 아래 절 번호만 구현한다.

| PR | 내용 | 절 |
| --- | --- | --- |
| A | 버림 기록 일반화, Flyway V3, 워치 규격 문서 | 3.1, 4.1, 5.1 |
| B | WS 본문 크기 초과, 실행기 포화 거절도 거절 기록에 남김 | 3.2, 5.2 |
| C | 빠진 번호 대조 보고서, 회차 내보내기에 진단 파일과 수신 경로 | 3.3, 3.4, 5.3, 5.4 |

### In scope
- 변경 모듈: widyu-api, widyu-domain, Flyway 버전 파일
- 버림 기록에 **데이터 종류**(`dropped_stream`)를 더하고, 사유 기본 목록을 문서로 정한다. 기존 `imu_dropped`는 그대로 받는다
- WS 본문 크기 초과와 센서 실행기 포화 거절을 `sensor_batch_rejection`에 남긴다
- 세션별 빠진 번호 구간과 원인 후보(워치 버림, 서버 거절, 기록 없음)를 적은 보고서를 기간, 회차 내보내기에 넣는다
- 회차 내보내기에 버린 구간, 거절 기록, 묶음별 수신 경로를 넣는다
- 워치, 폰이 보낼 형식 규격 문서 `docs/handoff/sensor-dropped-record-spec.md`

### Out of scope
- **앱 구현.** 워치가 심박 버림, 넣기 실패를 기록해 보내는 일은 앱 저장소에서 한다. 서버는 받을 준비만 한다
- **사유 값 검증.** 목록 밖 값도 거절하지 않고 저장한다(LLD-0124와 같다). 목록은 워치 담당과 최종 합의한다(9절)
- **세션 통째 누락 판정.** 한 묶음도 서버에 오지 않은 세션은 서버가 존재를 모른다. 보고서는 서버가 아는 세션의 누락률만 적는다(5.3)
- **기존 행 재분류.** 과거 거절, 버림 기록을 다시 해석하지 않는다. 기존 버림 행은 모두 워치 IMU였으므로 `imu_watch`로 채운다
- 형식서 `04_계약검사기/EXPORT_FORMAT.md`(외부 전달 묶음)의 개정. 서버가 더하는 진단 파일은 5.4에 적고, 형식서 반영은 PM과 따로 한다
- 자동 삭제. 다른 센서 기록과 같이 없다

## 3. 인터페이스 / API

### 3.1 버림 기록 (PR A)

endpoint는 그대로다(WebSocket `/app/sensor/batches/send`, REST `POST /api/v1/sensor/batches`). `stream` 값 `dropped`를 더한다.

```json
{ "stream": "dropped", "dropped_stream": "hr", "session_id": "bf8cd09a-...", "from_seq": 120, "to_seq": 131, "reason": "queue_overflow", "ts_ms": 1762000000200 }
```

| 필드 | 타입 | 필수 | 제약 |
| --- | --- | --- | --- |
| `stream` | string | O | `dropped`(새 형식) 또는 `imu_dropped`(기존 형식) |
| `dropped_stream` | string | `dropped`면 O | 버린 묶음의 `stream`. `^[a-z0-9_]{1,20}$`. 값 목록은 검증하지 않는다(`hr`, `imu_watch`, `imu_phone` 등). `imu_dropped`면 이 필드를 보지 않고 `imu_watch`로 저장한다 |
| `session_id` | string | O | `^[a-z0-9._-]{1,64}$` |
| `from_seq`, `to_seq` | long | O | `>= 0`, `to_seq >= from_seq`, 양 끝 포함 |
| `reason` | string | O | 1~64자. 기본 목록은 아래, 목록 밖 값도 저장 |
| `ts_ms` | long | O | 버린 시각(기기 벽시계 epoch ms) |

**왜 `stream: "dropped"` 하나인가.** 대안은 데이터 종류마다 `hr_dropped`, `imu_phone_dropped`를 더하는 것이다. 그러면 종류가 늘 때마다 서버 분기, 카운터 태그, 폰 변환 코드가 함께 바뀐다. 새 형식은 폰이 Data Layer 경로(`/hr/dropped/...`, `/imu/dropped/...`)에서 종류만 읽어 같은 JSON에 넣으면 되고, 서버는 종류가 늘어도 바뀌지 않는다. 기존 `imu_dropped`는 앱 배포가 끝날 때까지 계속 받는다.

**사유 기본 목록** (워치 코드의 실패 지점과 맞춘 서버 기본안, 9절 확인 필요):

| 값 | 뜻 | 워치 코드 지점(앱 develop) |
| --- | --- | --- |
| `queue_overflow` | 대기열 상한을 넘어 오래된 묶음부터 지웠다 | `DataLayerQueue.trimOldest`. IMU 상한은 `queueMinutes`분치(기본 30분, 1,800개), 심박은 720개(약 1시간) |
| `enqueue_failed` | 묶음을 대기열(Data Layer)에 넣지 못했다. `from_seq = to_seq = 그 묶음 seq` | IMU `ImuRemoteDataSourceImpl.sendBatch`의 `runSafely`, 심박 `HeartRateRepositoryImpl.transmit`의 `catch`. 지금은 로그만 남는다 |
| `expired` | 보관 시간을 넘겨 지웠다 | 지금 워치에는 시간 기준 삭제가 없다. 상한이 30분치 **개수**라 "30분 넘어 버림"도 `queue_overflow`로 온다. 시간 기준 삭제를 넣으면 이 값을 쓴다 |

응답은 LLD-0124와 같다(`batchId`, `seq`는 null, `result`는 `STORED` 또는 `DUPLICATE`). 멱등 키도 `(member_id, session_id, from_seq)` 그대로다. 심박과 IMU는 세션 ID를 따로 만들므로(워치 `HeartRateRepositoryImpl`, `ImuRepositoryImpl` 각각 UUID) 데이터 종류를 키에 넣지 않아도 충돌하지 않는다.

카운터 `sensor.batch.received{stream}` 태그에 `dropped`를 더한다(값 종류가 하나 늘 뿐 키 집합은 같다).

### 3.2 거절 기록 보강 (PR B)

수신 API의 응답은 바뀌지 않는다. 바뀌는 것은 거절 기록 행이 생기는 경우다.

| 경우 | 오류 코드(`error_code`) | 경로 |
| --- | --- | --- |
| WS 본문 크기 초과(`sensor.max-payload-bytes` 초과) | `SENSOR_4000` | `WS` |
| 센서 실행기 포화(`TEMPORARILY_BUSY` 응답) | `SRV_5031` | `WS` |

`SRV_5031` 행은 "서버가 그때 받지 못했다"는 뜻이다. 앱은 이 응답을 받으면 다시 보내야 하므로, 이 행이 있는데 묶음이 끝내 없으면 재전송이 실패한 것이다.

### 3.3 빠진 번호 대조 보고서 (PR C)

기간 내보내기(`GET /api/v1/admin/members/{memberId}/sensor-exports`) zip에 두 파일을 더한다.

`seq_gaps.csv` — 세션 안에서 빠진 번호 구간 한 줄씩:

| 열 | 뜻 |
| --- | --- |
| `stream`, `device_id`, `session_id` | 묶음의 자료 종류, 기기, 세션 |
| `from_seq`, `to_seq` | 빠진 번호 구간(양 끝 포함) |
| `missing_count` | `to_seq - from_seq + 1` |
| `gap_start_ms`, `gap_start_kst` | 구간 바로 앞 묶음의 측정 끝 시각 |
| `gap_end_ms`, `gap_end_kst` | 구간 바로 뒤 묶음의 측정 시작 시각 |
| `dropped_count`, `dropped_reasons` | 구간 안 번호 중 워치 버림 기록이 덮는 수, 그 사유(겹치는 기록의 사유를 `;`로) |
| `rejected_count`, `rejection_codes`, `rejection_channels` | 구간 안 번호 중 서버 거절 기록이 있는 수, 오류 코드, 경로(`;`로) |
| `unexplained_count` | 두 기록 어디에도 없는 수 |
| `cause` | `WATCH_DROPPED`, `SERVER_REJECTED`, `NO_RECORD` 중 해당하는 것을 `;`로. 예: `WATCH_DROPPED;NO_RECORD`는 일부만 버림 기록이 있다 |

`seq_sessions.csv` — 세션 한 줄씩. 세션 단위 누락률의 근거다:

| 열 | 뜻 |
| --- | --- |
| `stream`, `device_id`, `session_id` | |
| `first_seq`, `last_seq` | 이 내보내기에 있는 묶음 번호의 처음과 끝 |
| `expected_count` | `last_seq - first_seq + 1` |
| `received_count` | 그 사이 번호 중 서버에 있는 수(재전송으로 채운 번호 포함) |
| `missing_count`, `missing_ratio` | 빠진 수와 비율(소수 4자리) |
| `gap_count` | `seq_gaps.csv`에서 이 세션의 줄 수 |
| `ws_count`, `rest_count` | 이 세션 묶음 중 WebSocket, REST로 처음 저장된 수(경로가 없는 옛 행은 세지 않는다) |

### 3.4 회차 내보내기 (PR C)

`run_<run_id>.zip`에 아래를 더한다(LLD-0050).

| 자리 | 내용 |
| --- | --- |
| `streams/imu_*`, `streams/hr_*` 각 줄의 `_server.ingest_channel` | `WS`, `REST`, 옛 행은 `null` |
| `diagnostics/dropped_ranges.csv` | 기간 내보내기와 같은 열. 회원이 같고 버린 시각(`ts_ms`)이 회차 시작~끝인 행 |
| `diagnostics/rejections.csv` | 기간 내보내기와 같은 열. 회원이 같고 수신 시각이 회차 시작~끝인 행 |
| `diagnostics/seq_gaps.csv`, `diagnostics/seq_sessions.csv` | 3.3과 같은 열. 대상 묶음은 회차에 귀속된 `imu_watch`, `imu_phone`, `hr` |

`phase` 열은 회차 시작~끝 기준(`before`, `during`, `after`)이다.

## 4. 데이터 모델

### 4.1 `sensor_dropped_range.dropped_stream` (PR A)

| 컬럼 | 타입 | 제약 |
| --- | --- | --- |
| `dropped_stream` | VARCHAR(20) | NOT NULL. 버린 묶음의 `stream` |

Flyway `V3__sensor_dropped_range_stream.sql`: 기본값 `imu_watch`로 칸을 더해 기존 행을 채우고 기본값을 지운다(엔티티가 늘 값을 넣는다. 기본값이 남으면 빠뜨린 코드가 `imu_watch`로 조용히 저장된다).

엔티티 `SensorDroppedRange.droppedStream`. 요청 DTO `SensorDroppedRangeRequest`에는 두지 않고 서비스가 본문 트리에서 읽는다. DTO에 두면 옛 형식에 타입이 다른 `dropped_stream`이 섞였을 때 쓰지도 않을 칸 때문에 역직렬화가 실패한다.

### 4.2 거절 기록 (PR B)

스키마 변경 없음. `sensor_batch_rejection.ingest_channel`의 CHECK(`WS`, `REST`)도 그대로 맞는다.

### 4.3 조회 (PR C)

- `SensorDroppedRangeRepository.findByMemberIdAndSessionIdIn` — UK `(member_id, session_id, from_seq)`의 앞부분을 탄다
- `SensorBatchRejectionRepository.findByMemberIdAndSessionIdIn` — 인덱스 `(member_id, received_at_ms)`의 `member_id`만 탄다. 거절은 드물어 회원 단위 스캔으로 둔다

## 5. 처리 흐름

### 5.1 버림 기록 저장 (PR A)

1. `SensorBatchService.route`가 `stream`이 `imu_dropped` 또는 `dropped`면 `SensorDroppedRangeService.record`로 넘긴다
2. `imu_dropped`면 `dropped_stream`을 보지 않고 데이터 종류를 `imu_watch`로 정한다. `dropped`면 본문 트리에서 `dropped_stream`을 읽고 없거나 문자열이 아니거나 형식이 틀리면 `SENSOR_BATCH_INVALID`
3. 나머지 검증, 멱등, 저장은 LLD-0124 5절과 같다. 로그에 `droppedStream`을 더한다(센서 값 없음)

### 5.2 서비스 밖 거절 기록 (PR B)

거절 판단은 수신 스레드(STOMP inbound)에서 난다. 여기서 DB에 쓰면 커넥션을 얻을 때까지(최대 `connection-timeout` 3초) 수신 스레드가 멈춰 위치, 심박까지 함께 밀린다. 포화는 대개 S3나 DB가 느릴 때 생기므로 바로 그 순간에 DB 대기를 더하게 된다.

1. 컨트롤러가 본문 크기 초과를 판단하거나 `WebSocketWorkDispatcher.dispatch`가 `false`(실행기 거절)를 돌려주면, 회원 ID, 원문 바이트, 오류 코드, 수신 시각을 `SensorRejectionAsyncRecorder.submit`에 넘기고 바로 돌아간다
2. 기록기는 **전용 스레드 1개, 대기열 64칸**을 쓴다. 가득 차면 기록을 버리고 카운터 `sensor.batch.rejection.record.dropped`를 1 올린다. 거절이 몰려도 DB 커넥션은 최대 1개, 메모리는 64칸 × 본문 상한(WS 64KB) = 4MB를 넘지 않는다
3. 기록 스레드에서 원문을 얕게 읽어(실패하면 식별자 없이) LLD-0128 4.3 규칙으로 `batch_id`, `seq`, `session_id`, `device_id`, `stream`을 채운다. 원문은 저장하지 않는다
4. `SensorBatchRejectionRecorder.record`(`REQUIRES_NEW`)로 저장한다. 실패는 WARN(회원 ID, 오류 코드, 예외 클래스 이름)만 남긴다. 사용자 응답(ACK, `TEMPORARILY_BUSY`)은 이미 나갔고 기록 실패가 바꾸지 않는다

`dispatch`는 실행기에 넣었는지 여부를 `boolean`으로 돌려준다. 심박 등 다른 호출부는 값을 쓰지 않으므로 동작이 같다.

### 5.3 빠진 번호 계산 (PR C)

`SensorSeqGapReport.build(batches, droppedRanges, rejections)`(상태 없는 계산):

1. 묶음을 `(stream, session_id)`로 묶는다. 세션의 번호 집합 = 각 묶음의 `seq` ∪ **재전송 계보**(`is_resend=true`인 묶음의 `(original_session_id, original_seq)`를 원 세션의 번호로 더한다). 재전송으로 채운 번호는 빠진 것이 아니다
2. 세션 안에서 **처음과 끝 번호 사이**만 본다. 세션 앞(번호 0~처음 직전)과 뒤는 보지 않는다. 내보내기 구간이 세션 중간에서 시작하거나 끝날 수 있어, 앞뒤를 빠짐으로 적으면 구간 밖 묶음이 모두 빠진 것으로 잘못 나온다. 세션 끝에서 버린 묶음은 `dropped_ranges.csv`에서 본다
3. 빈 번호를 이어진 구간으로 묶는다. 구간마다 같은 세션의 버림 기록(번호 범위가 겹치는 것), 거절 기록(`session_id`가 같고 `seq`가 구간 안, `stream`이 있으면 같은 것)을 찾아 3.3 열을 채운다
4. 버림, 거절 기록은 **세션으로** 찾는다(4.3). 시각으로 찾으면 큐 넘침 기록(`ts_ms`)은 버린 묶음보다 최대 30분 늦어 내보내기 구간 밖으로 빠지고, 그 구간이 "기록 없음"으로 잘못 나온다. 세션을 읽지 못한 거절(본문 해석 불가 등)은 대조할 수 없고 `rejections.csv`에만 나온다

### 5.4 내보내기 조립 (PR C)

- 기간 내보내기: `SensorWindowExportService`가 묶음 목록으로 세션 ID를 모아 버림, 거절 기록을 세션으로 한 번 더 읽고 두 파일을 쓴다. `summary.csv`에 행 수가 남는다
- 회차 내보내기: `RunExportAssembler`가 회차 묶음 전체로 같은 계산을 하고 `diagnostics/` 네 파일을 쓴다. 진단 파일은 스트림 파일이 아니므로 `manifest.files[]`와 `streams_absent`에 넣지 않는다. **0건이어도 머리글만 있는 파일을 만든다** — 스트림 파일은 "파일 없음 + 부재 선언"이 규칙이지만, 진단 파일은 "찾아봤는데 없다"를 적는 자리라 파일이 없으면 "안 찾았다"와 구별되지 않는다. 검사기(`check_export.py`)는 `streams/` 밖 파일을 읽지 않는다
- 두 내보내기가 같은 CSV 행 규칙(`SensorWindowCsv`)을 쓴다

## 6. 예외 / 에러 처리

새 오류 코드 없음.

| 조건 | 결과 |
| --- | --- |
| `stream: "dropped"`인데 `dropped_stream`이 없거나 형식 오류 | `SENSOR_BATCH_INVALID`(`SENSOR_4003`) + 거절 기록 |
| 서비스 밖 거절 기록 대기열이 가득 참 | 기록을 버리고 카운터 1 증가. 응답은 그대로 |
| 서비스 밖 거절 기록 저장 실패 | WARN 한 줄. 응답은 그대로 |
| 내보내기에서 버림, 거절 기록 조회 실패 | 내보내기 실패(기존 조회와 같다) |

## 7. 인수조건 (Acceptance Criteria)

PR A
- [x] `stream: "dropped"` 기록을 받으면 데이터 종류, 사유, 시각, 번호 범위, 원문과 함께 저장하고 `STORED`를 돌려준다
- [x] 기존 `imu_dropped` 본문은 그대로 받아 데이터 종류를 `imu_watch`로 저장한다
- [x] `dropped`인데 `dropped_stream`이 없거나 형식이 틀리면 `SENSOR_BATCH_INVALID`다
- [x] 목록 밖 사유도 저장한다
- [x] V3 적용 뒤 기존 행의 `dropped_stream`이 `imu_watch`이고 칸에 기본값이 없다(`FlywayMigrationMySqlTest`)
- [x] 기간 내보내기 `dropped_ranges.csv`에 `dropped_stream` 열이 있다

PR B
- [ ] WS 본문 크기 초과는 `SENSOR_4000`, `WS`, 읽을 수 있는 식별자와 함께 거절 기록 한 행을 남기고 ACK는 전과 같다
- [ ] 센서 실행기 포화는 `SRV_5031`, `WS`, 식별자와 함께 거절 기록 한 행을 남기고 `TEMPORARILY_BUSY` 응답은 전과 같다
- [ ] 거절 기록 쓰기는 수신 스레드가 아닌 기록 스레드에서 하고, 대기열이 가득 차면 버리고 카운터를 올린다
- [ ] 기록 저장 실패가 응답을 바꾸지 않는다
- [ ] 원문과 센서 값이 거절 기록, 로그에 없다

PR C
- [ ] 세션 안 빠진 번호 구간마다 버림 기록 수와 사유, 거절 기록 수와 오류 코드, 둘 다 없는 수와 원인을 `seq_gaps.csv`에 적는다
- [ ] 재전송으로 채운 번호는 빠진 것으로 적지 않는다
- [ ] 세션 앞뒤 경계 밖 번호는 빠진 것으로 적지 않는다
- [ ] 버림 기록이 내보내기 구간 밖 시각이어도 세션이 같으면 대조한다
- [ ] `seq_sessions.csv`에 세션별 처음, 끝 번호, 받은 수, 빠진 수와 비율, 경로별 수가 있다
- [ ] 회차 내보내기 스트림 줄에 `_server.ingest_channel`이 있고 `diagnostics/` 네 파일이 있다. 0건이면 머리글만 있다
- [ ] `bash scripts/harness/verify.sh`가 통과한다

## 8. 영향 범위 / 마이그레이션

- **Flyway V3** (PR A): `sensor_dropped_range`에 NOT NULL 칸 추가. 행 수가 적어(dev 약 2천 행) 잠금 시간은 짧다. 다른 PR이 V3를 먼저 머지하면 번호를 올린다
- 앱이 `stream: "dropped"`를 보내기 전 서버가 먼저 배포돼야 한다. 반대 순서면 새 기록이 `SENSOR_BATCH_INVALID`로 거절되고 거절 기록에 남는다(앱은 이 응답을 기다리지 않고 지우므로 그 기록은 잃는다)
- 거절 기록 행이 늘어난다(포화 순간 거절 수만큼). 연구 철회 수동 삭제 대상은 LLD-0128과 같다
- 회차 내보내기 zip에 `diagnostics/` 폴더가 생긴다. `manifest.json`, 스트림 파일 형식은 바뀌지 않고 `_server`에 칸 하나가 는다

## 9. 미결정 사항 (Open Questions)

- [ ] **사유 값 목록**(기본값으로 정함, 확인 필요): `queue_overflow`, `enqueue_failed`, `expired`. 워치 담당(인협)과 최종 합의한다. 서버는 목록을 검증하지 않으므로 이름이 바뀌어도 서버 변경은 없고 이 문서와 규격 문서만 고친다
- [ ] **형식서 반영**: `diagnostics/`와 `_server.ingest_channel`을 `EXPORT_FORMAT.md` 다음 판에 넣을지 PM과 정한다

## 10. 참고

- 팀 문서 "이슈 3. 묶음이 빠졌을 때 어디서 빠졌는지 가릴 수 없습니다" 3-1, 3-2, 3-3
- 앱 develop: `android/wear/.../ImuRemoteDataSourceImpl.kt`(`recordDrop`), `ImuRepositoryImpl.kt`(`transmitOrThrow`), `HeartRateRepositoryImpl.kt`(`transmit`), `data/util/DataLayerQueue.kt`(`trimOldest`), `packages/heart_rate/.../HeartRatePlugin.kt`(`sensorItemToJson`)
- 워치 규격 문서: `docs/handoff/sensor-dropped-record-spec.md`
