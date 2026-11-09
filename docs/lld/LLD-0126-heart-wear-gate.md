# LLD-0126: 착용이 확실하지 않은 심박은 판정하지 않는다

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #958 |
| 관련 ADR | ADR-0035(판정 기록), ADR-0031(심박 배치 저장 우선), ADR-0041(단건 경로 제거) |
| 선행 LLD | [LLD-0047](LLD-0047-heart-rate-batch-v2.md), [LLD-0053](LLD-0053-heart-decision-record.md), LLD-0086(단건 DB 장애 저하 경로) |
| 작성자 | Claude |
| 작성일 | 2026-11-09 |

## 1. 목적 / 배경

워치를 벗었거나 헐겁게 찼을 때 들어온 심박도 지금은 AI 판정에 들어간다. 헐겁게 차면 심박 25 같은 틀린 값이 들어와 보호자에게 잘못된 위급 알림이 갈 수 있다. 정책은 「미착용이거나 착용이 확실하지 않으면 심박으로 판정하지 않고 알림도 보내지 않는다」이고, 착용 판정은 출시 전 필수로 정했다(11/2).

배치에는 `on_body`와 `wear_state`가 이미 들어오지만 판정 함수 `HeartRateBatchService.storeAndAssess`는 샘플만 받아 착용 값을 보지 못한다. 이 LLD는 착용이 불확실한 배치의 심박을 저장만 하고 판정하지 않게 한다.

워치가 다시 차도 `on_body=false`를 보내는 문제(앱 #109, PR #113)가 실기기에서 확인되기 전에 켜면 정상 착용 중 심박까지 모두 판정에서 빠진다. 그래서 스위치로 넣고 기본은 꺼 둔다.

## 2. 범위

### In scope
- 변경 모듈: widyu-api
- 설정 `sensor.heart-ai.wear-gate-enabled`(기본 false)
- 배치 경로(REST, WebSocket 공통 `SensorBatchService.ingestHeartRate`)의 착용 게이트
- 단건 경로(`HeartRateService.processHeartRate`)의 정상 경로와 DB 장애 저하 경로
- 회차 배치의 「판단 안 함」(`ABSTAIN_INSUFFICIENT_INPUT`) 행에 사유 `WEAR_UNCERTAIN`, 배치별 결정적 `decision_id`로 동시 재전송 중복 방지
- 내보내기 `decisions.jsonl`에 사유만 있는 ABSTAIN 행의 `evidence.reason`
- 관측용 카운터

### Out of scope
- 워치가 착용 값을 채워 보내는 일(앱 #110), 소문자 `"unknown"` 정규화. 지금 `@Pattern`이 묶음 전체를 거절하며, 앱이 대문자로 맞추기로 했다
- 정확도 `LOW`의 별도 필터. 워치가 착용 중 `LOW`·`UNRELIABLE`이면 `WORN_INVALID`로 보내기로 했다
- 단건 엔드포인트 삭제(앱에서는 이미 제거, ADR-0041). 별도 이슈
- Python AI의 다른 입력 경로와 본인 기준 재계산의 제외 조건(활동 상태 과업의 ADR에서 다룬다)
- 회차가 없는 배치의 판정 기록 행. LLD-0053대로 만들지 않는다

## 3. 인터페이스 / API

외부 API 변경 없음.

설정(`application-sensor.yml`):

```yaml
sensor:
  heart-ai:
    wear-gate-enabled: ${SENSOR_HEART_AI_WEAR_GATE_ENABLED:false}
```

내보내기 `decisions.jsonl`의 착용 불확실 ABSTAIN 행:

```json
{"decision_id":"dec-ab-01j…","decision_output":"ABSTAIN_INSUFFICIENT_INPUT", …,
 "evidence":{"reason":"WEAR_UNCERTAIN"}}
```

`hr_bpm`이 있는 행(심박 ALERT)은 지금과 같다. 낙상 행처럼 `hr_bpm`과 `reason`이 모두 없으면 `evidence`를 붙이지 않는다.

## 4. 데이터 모델

테이블 변경 없음. `decision_record.decision_id`(VARCHAR(40), UK `uk_decision_record_decision_id`)에 ABSTAIN 행만 `dec-ab-` + `batch_id`(소문자 ULID 26자, 합계 33자)를 넣는다. ALERT 행은 지금처럼 무작위 `dec-<uuid hex>`다.

`SensorProperties.HeartAi`에 `boolean wearGateEnabled` 추가.

## 5. 처리 흐름

### 5.1 착용 불확실 판정

배치 단위로 본다. 스위치가 켜져 있고 다음 중 하나면 착용 불확실이다.

- `on_body == false`
- `wear_state`가 `UNKNOWN`, `WORN_INVALID`, `PLANNED_NOT_WORN`, `ACTUAL_NOT_WORN` 중 하나

`wear_state`가 null이면(워치 #110 전) `on_body`만 본다. `WORN_VALID`이고 `on_body == true`면 지금처럼 판정한다.

### 5.2 배치 경로

1. `SensorBatchService.ingestHeartRate`가 `storeAndAssess`에 `onBody`, `wearState`를 넘긴다.
2. `storeAndAssess`가 5.1로 착용 불확실 여부를 한 번 계산한다. 불확실하면 샘플 루프에서 **AI 호출 분기만 건너뛰고** 저장은 그대로 한다(`UNKNOWN`). 중복 skip, 동시 재전송 skip, 최신값 갱신은 지금과 같다.
3. 루프 뒤 기존 ABSTAIN 조건(`runId != null`, 저장 1건 이상, `aiTargetCount == 0`, AI 미호출)에 그대로 들어간다. 착용 불확실이면 `reason = "WEAR_UNCERTAIN"`을 채운다. 착용과 무관한 기존 ABSTAIN(모든 샘플이 `UNRELIABLE` 등)은 `reason`이 null이다.
4. ABSTAIN 행의 `decision_id`는 `dec-ab-` + `batch_id`다. 같은 배치를 WS와 REST가 동시에 처리하면서 샘플 저장이 나뉘면 두 요청 모두 저장 1건 이상이 되어 ABSTAIN을 만들려 한다. 두 번째 저장은 `uk_decision_record_decision_id` 충돌이 되고, 이를 **중복으로 보고 INFO 로그**만 남긴다. 다른 저장 실패는 지금처럼 WARN을 남기고 삼킨다(심박 저장을 막지 않는다).
5. 착용 불확실로 AI를 건너뛴 배치가 저장 1건 이상이면 회차 유무와 관계없이 카운터 `heart.wear_uncertain`(태그 없음)을 1 올린다. 회차가 없는 배치는 판정 행이 없으므로 이 카운터가 유일한 관측 수단이다. 기존 `heart.decision`은 `output` 태그만 쓰므로 태그 키가 다른 미터를 같은 이름에 붙이지 않는다(Prometheus는 같은 이름에 다른 태그 키 집합을 거부한다).

### 5.3 단건 경로

앱에서는 단건 전송이 제거됐다(ADR-0041, 앱 #113). 단건 요청에는 착용 정보가 없으므로 스위치가 켜지면 착용 불확실로 본다.

1. 정상 경로: AI를 부르지 않고 `UNKNOWN`, 비위급으로 저장한다. 앱이 쓰지 않는 경로라 카운터는 두지 않는다.
2. DB 장애 경로(판정 전 조회 실패 `:73`, 판정 후 저장 실패 `:95`): 스위치가 켜져 있으면 저하 알림 비활성 때와 같이 **원래 예외를 던진다**. AI 호출과 저하 알림이 없다. 저장하지 못한 요청에 성공 응답을 만들지 않는다.
3. 스위치가 꺼져 있으면 정상, 장애 경로 모두 지금과 같다.

### 5.4 내보내기

`RunExportAssembler.putEvidence`는 `hr_bpm`과 `reason`이 모두 null이면 `evidence`를 붙이지 않는다. `hr_bpm`이 있으면 지금처럼 `hr_bpm`, `hr_measured_at_ms`, `hr_accuracy`, `reason`을 붙이고, `hr_bpm` 없이 `reason`만 있으면 `reason`만 붙인다.

### 5.5 로그

bpm, AI 응답, 판정 사유를 로그에 남기지 않는 규칙(#639)은 그대로다. `WEAR_UNCERTAIN`은 서버가 정한 고정 사유지만 같은 규칙을 따라 로그에 넣지 않는다. 중복 ABSTAIN 로그는 `memberId`, `decisionId`, `batchId`만 남긴다.

## 6. 예외 / 에러 처리

새 ErrorCode 없음.

| 상황 | 결과 |
| --- | --- |
| 착용 불확실 배치 | 저장, AI 미호출, 회차 있으면 ABSTAIN 한 행 |
| 같은 배치 ABSTAIN 두 번째 저장 | `decision_id` UK 충돌을 중복으로 보고 INFO. 심박 저장 영향 없음 |
| ABSTAIN의 다른 저장 실패 | 기존대로 WARN, 심박 저장 영향 없음 |
| 스위치 켬 + 단건 DB 장애 | 원래 예외(저하 알림 비활성 때와 같음) |

## 7. 인수조건 (Acceptance Criteria)

- [ ] 스위치를 켜고 `on_body=false`인 배치를 받으면 모든 샘플이 `UNKNOWN`으로 저장되고 AI는 0회 호출된다.
- [ ] `wear_state`가 `UNKNOWN`, `WORN_INVALID`, `PLANNED_NOT_WORN`, `ACTUAL_NOT_WORN`이면 `on_body=true`여도 같은 결과다.
- [ ] `wear_state`가 null이고 `on_body=true`이면 지금처럼 AI로 판정한다.
- [ ] 회차가 있는 착용 불확실 배치는 `decision_id = "dec-ab-" + batchId`, `reason = "WEAR_UNCERTAIN"`인 ABSTAIN 행 하나를 남긴다.
- [ ] 회차가 없는 착용 불확실 배치는 판정 행이 없고 카운터 `heart.wear_uncertain`이 1 오른다.
- [ ] 착용 불확실 배치의 샘플이 전부 중복이면 판정 행도 카운터 증가도 없다.
- [ ] 같은 배치의 ABSTAIN 저장이 `decision_id` UK 충돌로 실패하면 실패(WARN) 로그 없이 중복으로 처리되고 심박 저장은 그대로다.
- [ ] ABSTAIN 저장이 다른 이유로 실패하면 지금처럼 WARN을 남기고 심박 저장은 그대로다.
- [ ] 착용과 무관한 기존 ABSTAIN(모든 샘플 `UNRELIABLE`)은 `reason`이 null이다.
- [ ] 스위치를 끄면 착용 값과 관계없이 지금과 같이 판정한다.
- [ ] 스위치를 켜면 단건 정상 경로는 AI를 부르지 않고 `UNKNOWN`으로 저장한다.
- [ ] 스위치를 켜면 단건의 판정 전 조회 실패와 판정 후 저장 실패 모두 원래 예외를 던지고 AI 호출 0회, 저하 알림 0회다.
- [ ] 스위치를 끄면 단건 DB 장애 저하 경로는 지금과 같다.
- [ ] 내보내기에서 사유만 있는 ABSTAIN 행은 `evidence.reason`만 갖고, `hr_bpm`·`reason`이 모두 없는 행은 `evidence`가 없다. 심박 ALERT 행은 지금과 같다.
- [ ] `./gradlew :backend:widyu-api:test`가 통과한다.

## 8. 영향 범위 / 마이그레이션

- DDL 없음. 기존 ABSTAIN 행의 `decision_id`는 그대로 둔다.
- `SensorProperties.HeartAi` 생성자가 바뀌어 테스트 픽스처 4곳을 고친다.
- `HeartRateService`에 `SensorProperties`를 주입한다.
- LLD-0053의 3절(evidence 조건), 5.1(ABSTAIN `decision_id`·`reason`), 5.3(내보내기), 7절을 이 문서로 갱신했다고 LLD-0053에 한 줄 남긴다.
- `backend/CLAUDE.md` heart 절에 착용 게이트를 한 줄 추가한다.
- 운영 활성화: 앱 #109(PR #113) 실기기 확인 뒤 `SENSOR_HEART_AI_WEAR_GATE_ENABLED=true`.

## 9. 미결정 사항 (Open Questions)

- 없음.

## 10. 참고

- 노션 「[이슈] 착용이 확실하지 않을 때 들어온 심박은 판단하지 않게 하기」, 「[이슈] 워치를 다시 차도 서버에 미착용으로 남는 문제 고치기」
- 계획 검수 기록: Codex 3차 검수 APPROVE_WITH_NOTES(2026-11-09)
