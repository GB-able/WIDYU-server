# LLD-0138: 손목 움직임으로 5초 구간 활동 상태를 저장하고 심박 판정 입력으로 준비한다

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #1016 |
| 관련 ADR | [ADR-0042](../adr/ADR-0042-heart-activity-state-and-personal-baseline.md)(결정 3, 4, 5, 6. 후속 분할 8-B 저장, 8-D), ADR-0013(`context=UNKNOWN` 유지) |
| 선행 LLD | [LLD-0041](LLD-0041-sensor-batch-ingest.md)(IMU 배치, 시계 환산 4.3), [LLD-0047](LLD-0047-heart-rate-batch-v2.md)(심박 배치), [LLD-0048](LLD-0048-location-fix.md)(위치 원본), [LLD-0126](LLD-0126-heart-wear-gate.md)(착용 게이트) |
| 작성자 | Claude |
| 작성일 | 2026-11-12 |

## 1. 목적 / 배경

서버가 심박 AI에 넘기는 활동 상태(`context`)가 늘 `UNKNOWN`이라 AI는 쉬는 중의 150과 운동 중의 150을 구분하지 못한다. 연구팀은 5초마다 손목 움직임으로 정한 상태(`REST`, `LOW`, `ACTIVE`, `RECOVERY`)와 차량 이동(`IN_VEHICLE`)을 판정에 넣어 달라고 했다(노션 1-1, 1-2, 6-1). ADR-0042는 이 계산을 서버가 IMU 측정 시각 기준으로 하기로 정했다.

지금 AI는 `REST`가 오면 고정 기준(40 이하, 150 이상)을 끈다(노션 이슈 3). AI가 이를 고치고 검증하기 전에 상태를 보내면 쉬는 중 급락을 늦게 잡는다. 그래서 이 LLD는 **계산과 저장, 조회 함수까지만 켜고 AI로 보내는 값은 스위치로 막는다**. 스위치가 꺼져 있으면 동작은 지금과 같다.

함께 노션 5-1(심박 25 미만의 내부 표시 `flatline_arrest`가 보호자에게 나가지 않게)을 테스트로 고정한다. 코드 확인 결과 사유(`reason`)는 판정 기록 행에만 저장되고 다른 경로로 나가지 않아 서버 코드 수정은 없다.

## 2. 범위

### In scope
- 변경 모듈: widyu-domain(엔티티 `ActivityWindow`), widyu-api
- `imu_watch` 묶음 저장 직후 5초 구간 합계와 상태(`REST`, `LOW`, `ACTIVE`) 저장. 새 테이블 `activity_window`
- 심박 측정 시각의 상태 조회 함수. `RECOVERY`, `IN_VEHICLE`은 조회 때 계산
- 설정 `sensor.heart-ai.activity-context-enabled`(기본 false), `sensor.activity.*`
- `flatline_arrest` 미노출 테스트(단건 응답·방송 본문, 보호자 FCM·알림센터, 배치 경로)

### Out of scope
- 본인 기준 영속화(ADR-0042 8-A). 기준 테이블, 요청 필드, 04:00 재계산은 AI 담당과 요청 계약을 정한 뒤 한다
- AI 알림 규칙(8-C). 노션 3-1(고정 기준 유지), 4-1~4-3, 새 `context` 값 수용은 Python AI 저장소 작업이다
- 스위치 켜기. ADR-0042 결정 4의 조건을 채운 뒤 운영 설정으로 켠다
- 단건 경로(`/app/heart-rate/send-single`)의 `context`. 단건은 앱이 보낸 값을 정규화해 쓰고(ADR-0041로 제거 예정) 이 LLD는 바꾸지 않는다
- 활동 구간의 자동 삭제와 탈퇴 처리. 보관 기간 정리(LLD-0136), 탈퇴(LLD-0137)와 맞춘다
- 착용 여부에 따른 구간 제외. 구간은 착용과 관계없이 저장하고, 8-A 재계산이 심박 묶음의 착용 값으로 거른다

## 3. 인터페이스 / API

외부 API 변경 없음. 앱은 지금 보내는 `imu_watch` 묶음을 그대로 보낸다.

내부 인터페이스(`ActivityStateService`):

| 메서드 | 부르는 곳 | 동작 |
| --- | --- | --- |
| `record(memberId, batchId, stream, clock, acc, resend)` | `SensorBatchService.ingestImu` 묶음 저장 직후 | 구간별 합계를 더하고 상태를 다시 정한다. 예외를 던지지 않는다 |
| `contexts(memberId, firstTsMs, lastTsMs)` → `LongFunction<String>` | `HeartRateBatchService.storeAndAssess` | 심박 묶음 범위의 구간과 위치를 한 번 읽고, 측정 시각을 상태 문자열로 바꾸는 함수를 돌려준다 |

AI `POST /api/hr` 요청의 `context`:

| 스위치 | 값 |
| --- | --- |
| 꺼짐(기본) | 지금처럼 `UNKNOWN`. 구간 조회도 하지 않는다 |
| 켜짐 | 샘플 측정 시각의 `REST`, `LOW`, `ACTIVE`, `RECOVERY`, `IN_VEHICLE`, `UNKNOWN` 중 하나 |
| 켜짐 + 착용 불확실 배치(LLD-0126) | AI를 부르지 않으므로 조회하지 않는다 |
| 켜짐 + 조회 실패 | 배치 전체 `UNKNOWN`, WARN |

설정(`application-sensor.yml`, 숫자는 연구팀 후보 값):

| 키 | 기본 | 뜻 |
| --- | --- | --- |
| `sensor.heart-ai.activity-context-enabled` | false | 상태를 AI로 보낼지 |
| `sensor.activity.window-sec` | 5 | 구간 길이. epoch 기준 배수로 자른다 |
| `sensor.activity.min-window-samples` | 125 | 이보다 샘플이 적은 구간은 상태를 정하지 않는다(50Hz × 5초 = 250의 절반) |
| `sensor.activity.rest-max-g` | 0.04 | 움직임 크기가 이 값 미만이면 `REST` |
| `sensor.activity.active-min-g` | 0.20 | 이 값 이상이면 `ACTIVE`, 사이는 `LOW` |
| `sensor.activity.recovery-sec` | 600 | 마지막 `ACTIVE` 구간이 끝나고 이 시간 안이면 `RECOVERY` |
| `sensor.activity.vehicle-speed-kmh` | 20 | 차량 이동 속도 기준(초과) |
| `sensor.activity.vehicle-sec` | 60 | 차량 이동 지속 시간 |
| `sensor.activity.vehicle-max-accuracy-m` | 50 | 이 오차보다 나쁜 위치는 보지 않는다 |

## 4. 데이터 모델

### 4.1 움직임 크기 (가정, 규칙 문서 오면 교체)

연구팀 상태 규칙 문서가 아직 없다. ADR-0042가 미결정으로 둔 「움직임 크기의 정의」를 아래로 가정한다.

- 샘플마다 손목 가속도 3축(mg)의 벡터 크기 `|a| = √(x² + y² + z²)`를 1mg 정수로 반올림하고 1g(1000mg)를 뺀다. `d = min(round(|a|), 64000) − 1000`. 크기는 실수로 계산하고 64g에서 자른다. 축 값은 `int` 범위까지 허용되므로 정수 제곱합이 넘쳐 큰 움직임이 정지로 읽히지 않게 한다. 워치 가속도계 범위(보통 ±16g)보다 넉넉하다.
- 구간의 움직임 크기는 그 구간 `d`의 **모집단 표준편차**(g)다. 1g를 빼도 표준편차는 같으므로 「중력을 뺀 크기의 흔들림」과 같은 값이다.
- 경계값은 위쪽 상태에 넣는다. 0.04g는 `LOW`, 0.20g는 `ACTIVE`다.
- 자이로, 휴대폰 IMU(`imu_phone`)는 쓰지 않는다.

규칙 문서가 오면 `ActivityStateService.sumByWindow`, `classify`와 이 절을 함께 바꾼다. 정의가 바뀌면 이미 쌓인 합계는 새 정의와 맞지 않으므로 교체 시점 이전 구간을 어떻게 다룰지(재계산 불가, S3 원문에서 다시 계산) 그때 정한다.

### 4.2 `activity_window` (신규)

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| `activity_window_id` | BIGINT PK | |
| `member_id` | BIGINT NOT NULL | 회원 |
| `window_start_ms` | BIGINT NOT NULL | 구간 시작(epoch ms, 구간 길이의 배수). 기기 측정 시각 기준 |
| `window_end_ms` | BIGINT NOT NULL | 구간 끝(시작 + 구간 길이) |
| `sample_count` | INT NOT NULL | 더한 샘플 수 |
| `magnitude_sum_mg` | BIGINT NOT NULL | `d`의 합 |
| `magnitude_sq_sum_mg` | BIGINT NOT NULL | `d²`의 합 |
| `state` | VARCHAR(16) NULL | `REST` / `LOW` / `ACTIVE`. 샘플이 모자라면 null |
| `created_at`, `updated_at` | DATETIME(6) | |

- UK `uk_activity_window_member_start (member_id, window_start_ms)`. 멱등 키이자 심박 조회의 범위 인덱스다. 다른 인덱스는 두지 않는다.
- 상태만 두지 않고 정수 합계를 두는 이유: 워치 묶음은 1초라 한 구간에 묶음 다섯 개쯤이 나눠 들어온다. 묶음마다 상태를 정해 덮어쓰면 마지막에 온 묶음이 결과를 정한다. 정수 합계는 더하는 순서와 무관하므로 늦게 온 묶음, 역순 도착에도 마지막 행이 같다.
- `state`는 `@JdbcTypeCode(VARCHAR)`로 MySQL native ENUM을 쓰지 않는다. 값이 늘어도 ALTER가 필요 없다.

**규모와 보존.** 착용 중인 회원 1명당 하루 최대 17,280행(86,400초 ÷ 5초)이다. 행은 고정 길이 정수 위주라 인덱스를 합쳐 약 100바이트, 회원당 하루 약 1.7MB다. 묶음 하나(1초)는 구간 1~2개를 건드려 잠금 조회와 INSERT/UPDATE가 묶음당 1~2번 늘어난다. 자동 삭제는 하지 않는다. 보존 기간은 `heart_rate_event`와 함께 정하며(#650), 보관 기간 정리(LLD-0136)에 넣을지는 그 작업에서 맞춘다.

## 5. 처리 흐름

### 5.1 구간 저장 (`record`)

1. `stream`이 `imu_watch`가 아니거나 가속도가 없으면(자이로 보강 묶음) 끝낸다.
2. 같은 계보(원 묶음과 그 재전송들)에서 이 묶음보다 먼저 저장된 묶음(`sensor_batch` PK가 더 작은 같은 회원의 묶음)이 있으면 끝낸다. 계보의 뿌리는 재전송이면 `original_batch_id`, 아니면 자기 `batch_id`다. 내용이 바뀐 재전송은 새 `batch_id`로 오므로 원 묶음과 샘플이 겹친다. 묶음은 이 단계 전에 저장돼 PK가 정해지므로, 원 묶음이 늦게 오든 재전송이 여러 개 오든 둘이 거의 동시에 저장되든 계보에서 가장 먼저 저장된 묶음 하나만 더한다. 이 조회는 4단계와 같은 트랜잭션의 공유 잠금 조회(`FOR SHARE`)라, 다른 요청이 넣고 아직 커밋하지 않은 계보 행이 있으면 그 커밋을 기다린 뒤 본다. 원 묶음이 자기 자신이거나 다른 회원 것이면 겹친 샘플이 아니므로 더한다. 이 조회를 위해 `sensor_batch.original_batch_id`에 인덱스를 둔다.
3. 샘플 배열을 한 번 돈다. 각 샘플의 측정 시각은 LLD-0041 4.3 환산식(`anchor_epoch_ms + floor((t − anchor_elapsed_ns) / 10⁶)`)으로 구하고 그 시각이 속한 구간에 `(1, d, d²)`를 더한다. 수신 시 검증·파싱한 DTO를 쓰고 S3 원문을 다시 읽지 않는다.
4. 한 트랜잭션에서 구간마다 `SELECT ... FOR UPDATE`로 행을 읽어(없으면 새 행) 합계를 더하고 합계 전체로 상태를 다시 정해 저장한다. 잠금은 같은 구간에 동시에 더하는 두 묶음이 서로의 합계를 덮지 않게 한다.
5. 두 묶음이 같은 새 구간을 동시에 INSERT하면 UK 충돌이나 잠금 충돌(`DataIntegrityViolationException`, `ConcurrencyFailureException`)이 난다. 한 번 다시 하면 잠금 조회로 합친다.

같은 `batch_id` 재전송은 `sensor_batch` UK에서 `DUPLICATE`로 끝나 이 단계에 오지 않는다. 그래서 같은 묶음이 두 번 더해지지 않는다.

### 5.2 심박에 상태 붙이기 (`contexts`)

1. 스위치가 꺼져 있거나 착용 불확실 배치면 조회 없이 모든 샘플 `UNKNOWN`.
2. 켜져 있으면 배치의 첫·마지막 측정 시각으로 한 번씩 읽는다.
   - 구간: `[floor(first − recovery) − window, last]`
   - 위치: `[first − 2 × vehicle-sec, last]`, 정확도 50m 이내·속도 있음·모의 위치 아님만 쓴다
3. 샘플마다 아래 순서로 정한다.
   1. **`IN_VEHICLE`**: `ts − vehicle-sec` 이전의 마지막 위치(앞쪽 관측)와 그 뒤 `ts`까지의 위치(한 개 이상)가 모두 시속 20km를 넘는다. 움직임 상태보다 앞선다. 앞쪽 관측은 `ts − 2 × vehicle-sec`까지만 찾는다
   2. `ts`를 포함하는 구간이 없거나 상태가 null이면 **`UNKNOWN`**
   3. 그 구간이 `ACTIVE`면 **`ACTIVE`**
   4. 그 구간보다 앞선 `ACTIVE` 구간의 끝에서 `ts`까지 600초 이하면 **`RECOVERY`**(`REST`, `LOW` 모두 해당)
   5. 아니면 구간 상태(**`REST`** 또는 **`LOW`**)
4. 붙인 상태는 AI 요청에만 쓰고 심박 행에 저장하지 않는다(ADR-0042 결정 3-4).

구간 허용 간격(포함 구간이 없을 때 직전 구간을 인정할지)은 미정이라 지금은 포함 구간만 본다. 심박 묶음이 같은 시각 IMU 묶음보다 먼저 오면 `UNKNOWN`이다.

### 5.3 `flatline_arrest` 노출 경로 확인

`reason`을 읽는 곳은 `HeartRateBatchService`가 판정 기록 행(`decision_record.reason`)을 만드는 한 곳과 연구용 내보내기(`decisions.jsonl`의 `evidence.reason`)뿐이다. 보호자에게 나가는 값은 모두 사유 필드가 없는 객체에서 만든다.

| 경로 | 만드는 곳 | 사유 |
| --- | --- | --- |
| 단건 응답, 발신자 ACK, `/topic/heart-rate/{id}` 방송 | `HeartRateStatusResponse`(회원, 상태, bpm, 시각) | 없음 |
| 심박 조회 REST(최신값, 그래프, 위급 이력) | `heart_rate_result`, `heart_rate_event`, `heart_rate_emergency` | 컬럼 없음 |
| 보호자 FCM, 알림센터 | `HeartRateEmergencyEvent(memberId, decisionId)` → 사건 → 알림 문구 | 없음 |

앱과 워치 화면이 다른 경로로 사유를 그리는지는 서버에서 확인할 수 없어 앱 담당 확인으로 남긴다.

## 6. 예외 / 에러 처리

새 ErrorCode 없음. 활동 상태는 IMU 저장과 심박 판정의 부수 입력이라 실패가 본 흐름을 막지 않는다(판정 기록 실패와 같은 방식).

| 상황 | 결과 |
| --- | --- |
| 구간 계산·저장 실패(설정 누락, DB 오류 등) | WARN(`memberId`, 예외 클래스명). IMU 묶음은 이미 저장됐고 응답은 `STORED` 그대로 |
| 새 구간 동시 INSERT 충돌 | 한 번 재시도. 다시 실패하면 위와 같다 |
| 스위치 켬 + 구간·위치 조회 실패 | WARN, 그 배치 `UNKNOWN`. 심박 저장과 판정은 계속 |

로그에는 원시 센서값, 움직임 크기, 상태, 위치를 남기지 않는다(정책 1.6.7). 식별자와 예외 클래스명만 남긴다.

## 7. 인수조건 (Acceptance Criteria)

- [ ] 움직임 크기가 0.039g면 `REST`, 0.040g면 `LOW`, 0.199g면 `LOW`, 0.200g면 `ACTIVE`로 저장된다.
- [ ] 한 구간을 나눠 담은 두 묶음을 어떤 순서로 받아도 구간의 샘플 수, 합계, 상태가 같고 행은 하나다.
- [ ] 구간 경계에 걸친 묶음은 측정 시각에 따라 두 구간에 나눠 더해진다.
- [ ] 원 묶음과 그 재전송들(같은 회원) 중 먼저 저장된 묶음 하나만 구간에 더해진다. 원 묶음이 늦게 와도, 재전송이 여럿이어도 같다. 원 묶음이 자기 자신이거나 다른 회원 것이면 재전송도 더한다.
- [ ] `imu_phone`과 가속도 없는 묶음은 구간을 만들지 않는다.
- [ ] 샘플 수가 `min-window-samples`보다 적은 구간은 상태가 null이고 그 시각에는 `UNKNOWN`이 붙는다.
- [ ] 구간 계산·저장이 실패해도 예외가 나지 않는다. 워치 IMU 묶음 저장 뒤 계산에 수신 시 파싱한 가속도가 넘어간다.
- [ ] 심박 시각을 포함하는 구간의 상태가 붙고, 구간이 없으면 `UNKNOWN`이다.
- [ ] 마지막 `ACTIVE` 구간 끝에서 600초 이하면 `RECOVERY`, 넘으면 원래 상태다.
- [ ] 60초 전과 그 뒤 위치가 모두 시속 20km를 넘으면 `IN_VEHICLE`이 움직임 상태보다 앞선다. 사이에 느린 위치가 있거나, 앞쪽 위치가 없거나, 앞쪽 위치의 오차가 50m를 넘으면 아니다.
- [ ] 스위치가 꺼져 있으면 구간을 조회하지 않고 AI `context`는 `UNKNOWN`이다.
- [ ] 스위치를 켜면 샘플 측정 시각 순으로 그 시각의 상태가 AI `context`로 간다.
- [ ] 스위치를 켜고 조회가 실패하면 `UNKNOWN`으로 판정하고 심박은 저장된다.
- [ ] AI가 `level=EMERGENCY`, `reason=flatline_arrest`를 내면 단건 응답 본문, 보호자 FCM(제목, 본문, 데이터), 알림센터 문구에 `flatline`이 없고 사유는 판정 기록 행에만 있다.
- [ ] `./gradlew :backend:widyu-api:test`가 통과한다.

## 8. 영향 범위 / 마이그레이션

- `sensor_batch`에 인덱스 `idx_sensor_batch_original_batch_id (original_batch_id)`를 더한다. 운영 DDL은 같은 스크립트에 있다.
- 새 테이블 `activity_window`. dev는 `ddl-auto: update`가 만든다. 운영 DDL은 `scripts/mysql/create_activity_window.sql`이며 **운영 구축 때 적용**한다(운영 서버 구축 전).
- MySQL ENUM 변경 없음(`state`는 VARCHAR).
- `SensorProperties`에 `activity` 묶음, `HeartAi`에 `activityContextEnabled`가 늘어 테스트 픽스처 7곳을 고친다.
- `SensorBatchService`는 생성자 인자 하나와 저장 직후 호출 한 줄만 바뀐다. 열린 PR #988(수신 경로·거절 기록)이 같은 파일을 크게 고쳐, 충돌을 줄이려고 생성자 인자를 기존 인자 중간에 넣었다.
- `HeartRateBatchService`에 `ActivityStateService`를 주입한다(heart → sensor 방향. sensor → heart 의존은 기존대로라 순환이 없다).
- 스위치 켜기 조건(ADR-0042 결정 4, 모두 충족):
  1. AI가 노션 3-1(고정 기준을 모든 상태에서 유지)을 반영한 이미지를 배포했다.
  2. 그 이미지에서 `context=REST`의 150 이상 30초, 40 이하 30초가 `EMERGENCY`이고, `LOW`, `ACTIVE`, `RECOVERY`, `IN_VEHICLE`, `UNKNOWN`에서도 같으며, `RECOVERY`, `IN_VEHICLE`을 400 없이 받는 것을 LLD-0019 3절 형식으로 기록했다.
  3. 운영에서 구간 저장이 돌고 상태 분포를 연구팀이 확인했다.
  켜면 `SENSOR_HEART_AI_ACTIVITY_CONTEXT_ENABLED=true`로 두고 ADR-0013 헤더에 개정 행, LLD-0019 허용값과 인수조건을 갱신한다.

## 9. 미결정 사항 (Open Questions)

구현은 아래 가정으로 진행했다. 바뀌면 설정 또는 4.1절 계산을 바꾼다.

- 움직임 크기의 정의(4.1)와 경계 포함 방향. 연구팀 규칙 문서 대기.
- `RECOVERY`가 `LOW`에도 붙는지(지금은 `REST`, `LOW` 둘 다).
- 구간 최소 샘플 수 125(50Hz 기준 절반)가 적절한지.
- 구간 허용 간격. 지금은 포함 구간만 본다.
- 차량 이동의 「60초 동안」 해석. 지금은 60초 전 마지막 위치와 그 뒤 위치가 모두 기준을 넘는지로 본다. 위치 주기가 성기면 `IN_VEHICLE`이 덜 붙는다.
- `activity_window` 보존 기간과 탈퇴 시 처리(#650, LLD-0136, LLD-0137).

## 10. 다음 단계

- **8-A 본인 기준 영속화**: 기준 테이블(회원 × 낮·밤), 04:00 재계산(지난 7일, 이 LLD의 `REST` 구간 + 착용 확실 + 정확도 정상), AI 요청의 기준 필드. 요청 계약을 AI 담당과 정한 뒤 시작한다.
- **8-C AI 알림 규칙**: 노션 3-1, 4-1~4-3과 새 `context` 값 수용(Python 저장소). 끝나면 ADR-0042 결정 4의 검증 기록을 남기고 이 LLD의 스위치를 켠다.

## 11. 참고

- 노션 「심박 판단에 활동 상태와 본인 기준 넣기」 이슈 1, 5, 6
- ADR-0042 후속 이슈 분할안 8-B, 8-D
