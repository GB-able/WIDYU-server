# LLD-0188: 알림 기록에 확인 화면이 뜬 시각을 싣고 회차 내보내기에 알림 기록과 충격 기록을 넣는다

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #1155 |
| 관련 ADR | - (LLD-0131 알림 기록 CSV, LLD-0085 확인 화면 수신, LLD-0181 보호자 폰 도착, LLD-0050 회차 내보내기, LLD-0166 진단 파일을 잇는다) |
| 작성자 | Claude / feature-1155 |
| 작성일 | 2026-11-23 |

## 1. 목적 / 배경

대여 실증은 어르신이 확인 화면을 보고 60초 안에 답했는지 센다. 60초는 확인 화면이 폰에 뜬 때부터 잰다(LLD-0085). 서버는 그 시각을 `incident.prompt_displayed_at_ms`에 저장하지만 알림 기록 CSV(LLD-0131)와 회차 내보내기에는 없다. 그래서 「화면을 못 받아서 무응답」과 「화면은 받았는데 답이 없음」을 나눌 수 없다.

또 회차 zip에는 알림 한 건을 한 줄로 모은 표가 없고, 워치 충격은 스트림 줄의 `trigger`로만 짐작할 수 있다. 하루 충격 횟수를 세려면 충격 한 번이 한 줄로 나와야 한다.

노션 「실증 알림 기록에 화면 뜬 시각·판정 기록·충격 기록을 더해 내보내기」 이슈 1(1-1, 1-2)과 이슈 3(3-1)이다.

## 2. 범위

### In scope
- 변경 모듈: widyu-api만. 스키마 변경 없음
- 알림 기록 CSV 끝에 세 칸: 확인 화면이 뜬 시각, 서버 판정 시각, 보호자 폰 도착 시각
- 회차 zip `diagnostics/alert_records.csv`: 그 회차의 알림 기록. 칸은 관리자 CSV와 같다
- 회차 zip `diagnostics/impacts.csv`: 충격 한 번에 한 줄

### Out of scope
- 노션 이슈 2(판정 서버의 알림 근거, 참은 알림, 기준값 갱신, 하루 상태 기록 4종). AI 쪽 응답 형식이 정해진 뒤 따로 한다(2026-11-23 사용자 결정).
- 삼성 낙상 감지 결과 수신. 워치가 보내지 않는다. 칸만 두고 비운다(9절 1번).
- 충격 뒤 어르신이 움직였는지 계산. 노션 본문대로 AI가 한다.
- 기간 내보내기(LLD-0108) zip. 노션이 회차 zip을 요청했다. 기간 zip의 `sensor_batches.csv`에는 이미 `trigger_kind`가 있다.
- 낙상을 알림으로 보내지 않는 11월 정책. 바꾸지 않는다.
- 형식서(`EXPORT_FORMAT.md`) 개정. `diagnostics/`는 형식서 밖 서버 추가물이다(LLD-0166 10절과 같이 PM과 따로).

## 3. 인터페이스 / API

새 API는 없다. 관리자 `GET /api/v1/admin/alert-records`의 CSV 끝에 세 칸이 붙는다. 기존 칸 순서는 그대로다.

| 칸 | 출처 | 비고 |
| --- | --- | --- |
| `prompt_displayed_at_ms` | `incident.prompt_displayed_at_ms` | 폰이 확인 화면 수신을 알린 첫 시각(서버 수신, LLD-0085). 90초 안에 신호가 없었으면 빈칸 |
| `decision_at_ms` | `decision_record.decision_at_ms` | 서버가 AI 응답을 받아 판정한 시각. 회차 없는 알림은 빈칸 |
| `guardian_device_received_at_ms` | `decision_record.alert_device_received_at_ms` | 보호자 폰이 긴급 알림을 받았다고 알린 첫 시각(LLD-0181). 회차 없거나 도착 미확인이면 빈칸 |

알림 시각(`alert_at_ms`), 응답(`senior_response`, `response`, `responded_at_ms`), 보호자에게 간 시각(`guardian_alert_queued_at_ms`, `guardian_push_delivered_at_ms`)은 LLD-0131 3.2절 그대로다.

### 3.1 회차 zip `diagnostics/alert_records.csv`

- 그 회차(`incident.run_id`)의 `HR_ANOMALY`, `FALL_SUSPECTED` 사건이 알림 시각 순으로 한 줄씩이다. 칸, 계산(`senior_response`), 수식 방지 규칙은 관리자 CSV와 같다(`IncidentAlertExportService` 한 곳).
- 시각은 밀리초(UTC epoch)다. 첫 칸 `alert_at_kst`만 사람이 읽는 한국 시간이다.
- 사건 원본은 기존 `streams/incidents.jsonl`에 그대로 있다. 이 표는 실증 분석용 한 줄 요약이라 따로 둔다.

### 3.2 회차 zip `diagnostics/impacts.csv`

| 칸 | 출처 | 비고 |
| --- | --- | --- |
| `impact_at_kst` | `trigger_ts_ms` | `yyyy-MM-dd HH:mm:ss.SSS`, 한국 시간 |
| `impact_at_ms` | `sensor_batch.trigger_ts_ms` | 워치가 보낸 충격 시각 |
| `member_id` | `sensor_batch.member_id` | 어르신 회원 ID |
| `device_id` | `sensor_batch.device_id` | |
| `batch_id` | `sensor_batch.batch_id` | 충격을 처음 실은 묶음. 원문은 `streams/`의 같은 `batch_id` 줄 |
| `trigger_kind` | `sensor_batch.trigger_kind` | 워치 값 그대로(지금은 `impact`) |
| `smv_g` | `sensor_batch.trigger_smv_g` | 워치가 보낸 g 값 그대로. 구간 최대값인지는 9절 2번 |
| `samsung_fall_detected` | — | **빈칸.** 서버로 오지 않는다(9절 1번) |

## 4. 데이터 모델

변경 없음. 충격 기록은 새 테이블 없이 `sensor_batch`의 기존 칸(`trigger_kind`, `trigger_smv_g`, `trigger_ts_ms`)으로 만든다.

| 후보 | 채택 | 이유 |
| --- | --- | --- |
| 내보낼 때 `sensor_batch`에서 읽기 | 채택 | 워치가 충격마다 표시를 붙인 묶음을 바로 보내고(1.8g 초과), 서버는 그 값을 이미 칸으로 저장한다. 회차 내보내기는 진단 파일을 위해 회차 묶음 전체를 이미 읽는다(`findByRunIdOrderByMeasuredAtStartMsAsc`) |
| 충격 테이블 새로 두기 | 기각 | 같은 값을 두 번 저장한다. 수신 경로(`SensorBatchService`)를 바꿔야 하고 보관 기간 정리 대상도 는다 |
| S3 원문에서 최대 g 다시 계산 | 기각 | 충격 하나에 원문을 내려받아야 한다. 워치가 보낸 값이 무엇인지(9절 2번) 정해지기 전에 서버가 다른 정의를 만들지 않는다 |

## 5. 처리 흐름

1. `IncidentAlertRecordRepository`의 기간 조회와 새 회차 조회(`findRunAlertRecords`)가 같은 SELECT 칸에 `i.promptDisplayedAtMs`, `d.decisionAtMs`, `d.alertDeviceReceivedAtMs`를 더한다. 판정 기록은 지금처럼 left join이다.
2. `IncidentAlertExportService.runCsv(runId)`가 관리자 CSV와 같은 머리글과 행 규칙으로 문자열을 만든다. 0건이면 머리글만이다. 로그는 남기지 않는다(회차 내보내기 워커가 결과를 남긴다).
3. `SensorLossDiagnostics.impacts(batches)`가 `trigger_kind`가 있는 묶음을 측정 시작 순으로 훑어 한 줄씩 만든다.
   - **같은 충격은 한 줄이다.** 키는 (`device_id`, `trigger_ts_ms`)다. 내용이 달라진 재전송은 새 `batch_id`로 오므로(ADR-0030) 같은 충격이 두 묶음에 실릴 수 있다. 먼저 저장된 묶음(`sensor_batch` PK가 작은 쪽)을 쓴다. 입력은 측정 시작 순이라 앞에 나온 묶음이 먼저 받은 묶음이라는 보장이 없다. 줄 순서는 그 충격이 처음 나온 측정 시작 순이다.
   - `trigger_ts_ms`가 없는 묶음은 합칠 기준이 없어 묶음마다 한 줄이고 시각 칸이 빈다.
4. `RunExportAssembler.writeDiagnostics`가 기존 네 파일 뒤에 `impacts.csv`와 `alert_records.csv`를 쓴다. 0건이어도 머리글을 쓴다(LLD-0166 5.4의 진단 파일 규칙). `manifest.files[]`, `streams_absent`에는 넣지 않는다.

트랜잭션은 회차 조립의 읽기 트랜잭션(`RunExportAssembler.build`, `readOnly`) 안이다. 쓰기는 없다.

## 6. 예외 / 에러 처리

- 새 에러 코드 없음.
- 알림 조회가 실패하면 회차 조립 전체가 실패하고 그 내보내기는 `FAILED`로 끝난다(다른 진단 파일과 같다). 워커는 `QUEUED`만 집고 `FAILED`를 자동으로 다시 시도하지 않으므로, 운영자가 같은 회차에 내보내기를 새로 요청해야 한다.
- 로그에 심박 값, 사유, g 값을 남기지 않는다(#639, 정책 1.6.7). 이 변경은 로그를 더하지 않는다.

## 7. 인수조건 (Acceptance Criteria)

- [x] 관리자 알림 기록 CSV 머리글 끝에 `prompt_displayed_at_ms`, `decision_at_ms`, `guardian_device_received_at_ms`가 있고 기존 칸 순서는 같다.
- [x] 회차 있는 알림은 세 칸이 사건과 판정 기록 값으로 채워지고, 화면 수신 신호가 없던 알림과 회차 없는 알림은 빈칸이다.
- [x] 회차 조회는 그 회차의 심박, 낙상 사건만 알림 시각 순으로 돌려주고 도움 요청과 다른 회차 사건은 뺀다.
- [x] 회차 zip에 `diagnostics/alert_records.csv`와 `diagnostics/impacts.csv`가 있고 0건이면 머리글만 있다. `manifest.json`에는 들어가지 않는다.
- [x] 충격 표시가 있는 묶음만 한 줄이 되고, 같은 기기의 같은 충격 시각이 두 묶음에 실려도 먼저 저장된 묶음 한 줄이다. 재전송의 측정 시작이 더 일러도 마찬가지다.
- [x] `samsung_fall_detected`는 머리글에 있고 값은 비어 있다.
- [x] `./gradlew :backend:widyu-api:test`가 통과한다.

## 8. 영향 범위 / 마이그레이션

- DDL 없음. 배포 순서 제약 없음.
- 관리자 알림 기록 CSV에 칸이 셋 는다. 칸 이름으로 읽는 분석 스크립트는 영향이 없고, 칸 위치로 읽는 스크립트도 끝에 붙어 기존 위치가 같다.
- 회차 zip에 진단 파일이 둘 는다. 검사기(`check_export.py`)는 `streams/` 밖 파일을 읽지 않는다(LLD-0166 5.4).

## 9. 미결정 사항 (Open Questions)

- [ ] 1. **삼성 낙상 감지**: 워치가 삼성 낙상 감지 결과를 서버로 보내지 않는다. 보내려면 앱 담당(인협)과 형식을 정해야 한다. 후보는 충격 묶음의 `trigger`에 칸 하나(`samsung_fall: true|false|null`)를 더하는 것이다. 정해지면 `sensor_batch`에 칸을 더하고 `impacts.csv`의 빈칸을 채운다.
- [ ] 2. **`smv_g`의 뜻**: 워치가 보내는 `trigger.smv_g`가 충격 구간의 가장 큰 값인지, 1.8g 문턱을 넘은 순간의 값인지 앱 코드로 확인하지 못했다. 노션은 「가장 큰 g」를 요청했다. 문턱 순간 값이면 워치가 최대값을 보내게 바꾸는 쪽이 서버 재계산보다 단순하다.
- [ ] 3. **노션 이슈 2(판정 서버 기록 4종)**: AI 쪽 열 이름과 응답 형식이 오면 별도 LLD로 받는다.

## 10. 참고

- 노션 요청사항 「실증 알림 기록에 화면 뜬 시각·판정 기록·충격 기록을 더해 내보내기」
- LLD-0131(알림 기록 CSV), LLD-0085(확인 화면 수신 시각), LLD-0181(보호자 폰 도착), LLD-0050(회차 내보내기), LLD-0166(진단 파일 규칙), LLD-0051(충격 묶음과 낙상 판정)
