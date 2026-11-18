# 워치, 폰이 보낼 "버린 묶음" 기록 규격

워치나 폰이 센서 묶음을 서버로 보내지 못하고 버렸을 때 남기는 기록의 형식이다. 서버는 이 기록을 저장해 기간 내보내기(`dropped_ranges.csv`)와 회차 내보내기(`diagnostics/dropped_ranges.csv`)에 넣고, 빠진 묶음 번호 구간마다 "기기가 버렸다", "서버가 거절했다", "기록 없음"을 가린다(`seq_gaps.csv`, 회차 내보내기는 `diagnostics/seq_gaps.csv`). 설계 근거는 [LLD-0166](../lld/LLD-0166-sensor-loss-attribution.md)이다.

## 무엇이 바뀌나

| | 지금(앱 develop) | 앞으로 |
| --- | --- | --- |
| 워치 IMU 큐 넘침 | `stream: "imu_dropped"`로 보냄 | 그대로 받는다. 새 형식으로 바꿔도 된다 |
| 심박 큐 넘침 | `Log.w`만 | 새 형식, `dropped_stream: "hr"`, `reason: "queue_overflow"` |
| 대기열에 넣기 실패(IMU, 심박) | `Log.w`만 | 새 형식, `reason: "enqueue_failed"` |

서버는 새 형식을 먼저 배포한다. 서버 배포 전에 앱이 새 형식을 보내면 서버가 `SENSOR_4003`으로 거절한다(앱은 이 응답을 기다리지 않고 지우므로 그 기록은 잃는다).

## 보내는 곳

센서 묶음과 같은 곳이다.

- WebSocket: `/app/sensor/batches/send`
- REST: `POST /api/v1/sensor/batches`

## 본문

```json
{
  "stream": "dropped",
  "dropped_stream": "hr",
  "session_id": "bf8cd09a-1c2d-4e5f-8a9b-0c1d2e3f4a5b",
  "from_seq": 120,
  "to_seq": 131,
  "reason": "queue_overflow",
  "ts_ms": 1762000000200
}
```

| 필드 | 타입 | 필수 | 규칙 |
| --- | --- | --- | --- |
| `stream` | string | O | 항상 `"dropped"` |
| `dropped_stream` | string | O | 버린 묶음의 `stream` 값 그대로. `"hr"`, `"imu_watch"`, `"imu_phone"`. 소문자, 숫자, `_` 1~20자 |
| `session_id` | string | O | 버린 묶음의 `session_id` 그대로. 소문자, 숫자, `.`, `_`, `-` 1~64자 |
| `from_seq` | long | O | 버린 묶음 번호의 처음. 0 이상 |
| `to_seq` | long | O | 버린 묶음 번호의 끝(포함). `from_seq` 이상. 하나만 버렸으면 `from_seq`와 같다 |
| `reason` | string | O | 아래 사유 값. 1~64자 |
| `ts_ms` | long | O | 버린 시각, 기기 벽시계 epoch ms |

- 모르는 필드를 더해도 거절하지 않는다. 원문 그대로 저장된다(예: `device_id`를 넣으면 원문에 남는다)
- 같은 `(session_id, from_seq)`를 다시 보내면 서버는 저장하지 않고 `DUPLICATE`로 답한다. 재전송해도 안전하다
- 응답 `{batchId: null, seq: null, result: "STORED" | "DUPLICATE"}`. 형식 오류는 WebSocket이면 `/user/queue/errors`, REST면 400 `SENSOR_4003`

## 사유 값 (서버 기본안, 합의 필요)

| 값 | 언제 | 워치 코드 지점(앱 develop 기준) |
| --- | --- | --- |
| `queue_overflow` | 대기열 상한을 넘어 오래된 묶음부터 지웠다 | `DataLayerQueue.trimOldest`가 돌려준 번호. IMU는 `ImuRepositoryImpl.transmitOrThrow`(이미 보냄), 심박은 `HeartRateRepositoryImpl.transmit`의 `trimQueue` 결과(지금 `Log.w`만) |
| `enqueue_failed` | 묶음을 Data Layer 대기열에 넣지 못했다. 그 묶음 하나라 `from_seq = to_seq` | IMU `ImuRemoteDataSourceImpl.sendBatch`의 `runSafely` 실패, 심박 `HeartRateRepositoryImpl.transmit`의 `catch` |
| `expired` | 보관 시간을 넘겨 지웠다 | 지금은 없다. IMU 상한이 "30분치 개수"라 30분 넘은 묶음도 `queue_overflow`로 지워진다. 시간 기준으로 지우는 코드를 넣으면 이 값 |

서버는 이 목록 밖 값도 거절하지 않고 저장한다. 이름을 바꾸거나 값을 더하면 이 문서만 고치면 된다.

## 워치, 폰 구현 제안

지금 IMU 큐 넘침 경로를 그대로 넓히면 된다.

1. 워치: 버린 구간마다 Data Layer 항목 하나를 남긴다. IMU는 지금처럼 `/imu/dropped/{session_id}/{from_seq}`, 심박은 `/hr/dropped/{session_id}/{from_seq}`. 키에 `dropped_stream`, `session_id`, `from_seq`, `to_seq`, `reason`, `ts_ms`를 넣는다
2. 폰(`HeartRatePlugin.sensorItemToJson`): `/imu/dropped/`, `/hr/dropped/` 항목을 위 본문으로 바꿔 보낸다. `stream`은 `"dropped"`, `dropped_stream`은 항목 값(없으면 경로로 `imu_watch` 또는 `hr`)
3. 보낸 뒤 항목을 지우는 흐름은 지금과 같다

`enqueue_failed`는 대기열 자체가 실패한 경우라 같은 Data Layer에 기록 항목을 남기는 것도 실패할 수 있다. 그때는 다음 성공 때 함께 보내거나 로그만 남는다. 서버는 이 기록이 없으면 그 구간을 "기록 없음"으로 적는다.

## 옛 형식

```json
{ "stream": "imu_dropped", "session_id": "s-01", "from_seq": 10, "to_seq": 15, "reason": "queue_overflow", "ts_ms": 1760000000200 }
```

계속 받는다. 데이터 종류는 `imu_watch`로 저장한다. `dropped_stream`을 넣어도 쓰지 않는다.

## 내보내기에서 보는 법

`seq_gaps.csv` 한 줄이 한 세션의 빠진 번호 구간이다. `dropped_count`는 그 구간 중 이 기록이 덮는 번호 수, `dropped_reasons`는 사유, `cause`는 `DEVICE_DROPPED`(기기가 버렸다고 알림), `SERVER_REJECTED`, `NO_RECORD` 중 해당하는 것이다. 이 기록을 보내지 않으면 워치가 버린 구간도 `NO_RECORD`로 나온다.
