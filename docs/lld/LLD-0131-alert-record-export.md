# LLD-0131: 알림 한 건마다 판정 근거와 어르신 응답을 한 줄로 내보내기

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #993 |
| 관련 ADR | [ADR-0035](../adr/ADR-0035-alert-decision-and-incident.md)(판정 기록과 사건 분리), [ADR-0038](../adr/ADR-0038-self-check-first-safety-flow.md)(본인확인 선행), ADR-0042(활동 상태·본인 기준, Proposed) |
| 선행 LLD | [LLD-0053](LLD-0053-heart-decision-record.md), [LLD-0054](LLD-0054-incident.md), [LLD-0070](LLD-0070-self-check-first-heart.md), [LLD-0126](LLD-0126-heart-wear-gate.md), [LLD-0108](LLD-0108-admin-sensor-window-export.md) |
| 작성자 | Claude |
| 작성일 | 2026-11-10 |

## 1. 목적 / 배경

대여 실증에서 연구팀이 볼 숫자는 알림이 하루에 몇 번 울렸는지, 어르신이 「괜찮아요」를 누른 비율, 응답하지 않은 비율, 보호자에게까지 간 비율이다. 모두 알림 한 건마다 남는 기록에서 나온다.

서버에는 이 값이 세 표에 흩어져 있다. 사건(`incident`)에 어르신 응답·응답 시각·상태·보호자 알림 시각이, 회차가 있는 배치의 판정 기록(`decision_record`)에 그때 심박·정확도·AI 판정 사유·보호자 FCM 도달이, 센서 묶음(`sensor_batch`)에 착용 값이 있다. 회차 내보내기(LLD-0050)는 회차 하나 단위로 `decisions.jsonl`과 `incidents.jsonl`을 따로 내보내고, 회차 없이 열린 사건은 꺼내지 못한다. 이 LLD는 관리자가 기간만 주면 알림 한 건이 한 줄인 CSV를 받게 한다. 새 저장 구조는 만들지 않고 있는 표를 조인한다.

## 2. 범위

### In scope
- 변경 모듈: widyu-api
- 관리자 전용 동기 API 하나. 응답은 CSV 한 개.
- 조회 전용 저장소 하나(`IncidentAlertRecordRepository`). 사건 → 판정 기록 → 센서 묶음을 JPQL 한 문장으로 left join한다.

### Out of scope
- 본인 기준값·이어진 시간·활동 상태의 저장. ADR-0042 결정 뒤 별도 작업이다. 이번에는 CSV 칸만 두고 비운다(5.3).
- 회차 없는 알림의 판정 근거 저장. 판정 기록 행은 회차가 있는 배치에만 생긴다(LLD-0053). 회차 없는 알림의 근거 칸은 빈다.
- 보호자 푸시의 실제 도달 시각을 회차 없는 알림에도 남기는 일(5.4).
- 회원 이름·전화번호, 위치 좌표. CSV에는 회원 ID만 넣는다.
- 관리자 웹 화면, 비동기 잡.

## 3. 인터페이스 / API

```http
GET /api/v1/admin/alert-records?from=2026-11-01T00:00:00&to=2026-11-08T00:00:00
Authorization: Bearer <관리자 토큰>

200 OK
Content-Type: text/csv;charset=UTF-8
Content-Disposition: attachment; filename="alert_records_20261101T0000-20261108T0000.csv"
```

| 파라미터 | 필수 | 설명 |
| --- | --- | --- |
| `from`, `to` | O | **한국 시간(Asia/Seoul)** ISO 날짜시간. 알림 시각이 `[from, to)`인 행. `from < to`, 최대 31일 |

### 3.1 「알림 한 건」의 정의

**알림 한 건 = 서버가 이상을 감지해 연 사건(`incident`) 한 건**이다. 대상 종류는 `HR_ANOMALY`(심박)와 `FALL_SUSPECTED`(낙상)다.

| 후보 | 채택 | 이유 |
| --- | --- | --- |
| 사건 `incident` | 채택 | 어르신에게 「괜찮으세요?」 확인 알림이 나가는 단위가 사건이다(LLD-0070 5.1). 회차가 있든 없든, 배치든 단건이든 위급 판정이면 사건이 열린다. 어르신 응답·응답 시각·보호자 알림 시각이 이미 이 행에 있다 |
| 판정 기록 `decision_record` | 기각 | 회차가 있는 배치에만 생긴다(LLD-0053). 제품 모드 알림을 빠뜨린다. 같은 상황의 재감지는 사건 하나에 붙어(LLD-0072 `detection_count`) 판정 행 수가 알림 수보다 많다 |
| 보호자 FCM 발송 | 기각 | 보호자가 여럿이면 한 알림이 여러 건이 되고, 본인확인 선행(`self-check-first=true`)에서 어르신이 기한 안에 「괜찮아요」를 누르면 발송이 0건이라 그 알림이 빠진다. 「보호자에게까지 간 비율」의 분모가 될 수 없다 |

`SELF_HELP_REQUEST`(어르신이 직접 누른 도움 요청)는 서버 판정이 울린 알림이 아니라 제외한다. `SAFE_ZONE_EXIT`는 새 사건에 쓰지 않는 옛 값이라 제외한다.

판정 근거는 사건을 연 판정(`incident.decision_id`)의 값을 쓴다. 같은 사건에 붙은 뒤 감지는 `detection_count`로만 보인다.

### 3.2 CSV 칸

한 줄이 사건 한 건이다. 알림 시각 오름차순, 같으면 사건 순.

| 칸 | 출처 | 비고 |
| --- | --- | --- |
| `alert_at_kst` | `incident.opened_at_ms` | `yyyy-MM-dd HH:mm:ss.SSS`, 한국 시간 |
| `alert_at_ms` | `incident.opened_at_ms` | UTC epoch ms |
| `incident_id` | `incident.incident_ref` | 회차 내보내기 `incidents.jsonl`의 `incident_id`와 같다 |
| `member_id` | `incident.member_id` | 어르신 회원 ID |
| `kind` | `incident.kind` | `HR_ANOMALY` / `FALL_SUSPECTED` |
| `run_id` | `incident.run_id` | 회차 없으면 빈칸 |
| `detection_count` | `incident.detection_count` | 같은 상황에 붙은 감지 수 |
| `decision_id` | `incident.decision_id` | 회차 없으면 빈칸 |
| `severity` | `incident.level` | AI `level` |
| `hr_bpm` | `decision_record.hr_bpm` | 회차 있는 심박 알림만 |
| `hr_measured_at_ms` | `decision_record.hr_measured_at_ms` | 〃 |
| `hr_accuracy` | `decision_record.hr_accuracy` | 〃 |
| `ai_reason` | `decision_record.reason` | AI 판정 사유 원문. 〃 |
| `on_body` | `sensor_batch.on_body` | 판정을 일으킨 묶음(`decision_record.trigger_batch_id`)의 값. 회차 있는 알림만 |
| `wear_state` | `sensor_batch.wear_state` | 〃 |
| `baseline_bpm` | — | **빈칸.** 본인 기준값. ADR-0042 이후 |
| `held_seconds` | — | **빈칸.** 이상이 이어진 시간. ADR-0042 이후 |
| `activity_state` | — | **빈칸.** 활동 상태. ADR-0042 이후 |
| `respond_by_ms` | `incident.respond_by_ms` | 어르신 응답 마감(확인 화면이 뜨면 옮겨진 최종값) |
| `senior_response` | 계산 | `OK` / `HELP` / `NO_RESPONSE` / `PENDING` (5.2) |
| `response` | `incident.response` | 어르신이 실제 보낸 값(`OK`/`HELP`) |
| `responded_at_ms` | `incident.responded_at_ms` | 서버 수신 시각 |
| `response_via` | `incident.response_via` | `WATCH` / `PHONE` |
| `state` | `incident.state` | 사건 상태 |
| `guardian_alert_queued_at_ms` | `incident.initial_alert_sent_at_ms` | 보호자 최초 알림 단계에 들어간 시각(5.4) |
| `guardian_push_delivered` | `decision_record.alert_delivered` | 보호자 FCM 첫 전송 성공 여부. 회차 있는 알림만 |
| `guardian_push_delivered_at_ms` | `decision_record.alert_at_ms` | 〃 |
| `outcome` | `incident.outcome` | 보호자 사후 판정 |

### 3.3 이미 남는 항목과 새로 만들어야 하는 항목

| 노션 1-1 항목 | 회차 있는 알림 | 회차 없는 알림(제품 모드) | 칸 |
| --- | --- | --- | --- |
| 알림 시각 | 남음 | 남음 | `alert_at_*` |
| 어르신 | 남음 | 남음 | `member_id` |
| 알림 종류 | 남음 | 남음 | `kind` |
| 그때 심박 | 남음 | **현재 남지 않음**(판정 기록 없음) | `hr_bpm`, `hr_measured_at_ms`, `hr_accuracy` |
| AI 판정 사유 | 남음 | **현재 남지 않음** | `ai_reason` |
| 착용 상태 | 남음(심박은 착용 게이트 LLD-0126을 거친 묶음) | **현재 남지 않음**(사건과 묶음의 연결이 없음) | `on_body`, `wear_state` |
| 본인 기준값 | **새로 만들어야 함**(ADR-0042) | **새로 만들어야 함** | `baseline_bpm` 빈칸 |
| 얼마나 이어졌는지 | **새로 만들어야 함**(ADR-0042). 사건 단위 재감지 수만 있음 | 〃 | `held_seconds` 빈칸, `detection_count` |
| 활동 상태 | **새로 만들어야 함**(ADR-0042) | **새로 만들어야 함** | `activity_state` 빈칸 |
| 어르신 응답(괜찮아요/도움 요청/응답 없음) | 남음 | 남음 | `senior_response`, `response` |
| 응답 시각 | 남음 | 남음 | `responded_at_ms` |
| 보호자에게 갔는지와 그 시각 | 남음(알림 단계 시각 + FCM 첫 성공) | 알림 단계 시각만 남음. FCM 실제 성공은 **현재 남지 않음** | `guardian_alert_queued_at_ms`, `guardian_push_*` |

## 4. 데이터 모델

스키마 변경 없음. 조회 전용 저장소와 결과 record만 더한다.

- `IncidentAlertRecordRepository extends Repository<Incident, Long>`: `findAlertRecords(fromMs, toMs, kinds)` 한 메서드.
- `IncidentAlertRow` record: 위 3.2의 출처 값을 그대로 담는다(JPQL 생성자 식).

기존 `IncidentRepository`에 메서드를 더하지 않는다. 보관·탈퇴 작업(LLD-0133·0134)이 같은 파일을 고칠 수 있어 충돌을 피한다.

## 5. 처리 흐름

### 5.1 요청
1. `SecurityConfig`의 `/api/v1/admin/**` `hasRole("ADMIN")`로 관리자만 들어온다. `AdminAccessLogInterceptor`(LLD-0057)가 관리자·시각·경로·쿼리 키 이름(`from&to`)·결과를 남긴다. 쿼리 값은 남기지 않는다.
2. `IncidentAlertExportService.export(from, to)`가 `from < to`, 31일 이하를 검사한다. 어기면 `SENSOR_EXPORT_RANGE_INVALID`(400, 「내보내기 시간대가 올바르지 않습니다.」)를 그대로 쓴다.
3. 한국 시간을 epoch ms로 바꿔 JPQL 한 문장으로 읽는다.

```sql
select … from Incident i
  left join DecisionRecord d on d.decisionId = i.decisionId
  left join SensorBatch b on b.batchId = d.triggerBatchId
 where i.openedAtMs >= :fromMs and i.openedAtMs < :toMs and i.kind in :kinds
 order by i.openedAtMs, i.id
```

`decision_record.decision_id`·`sensor_batch.batch_id`는 UK라 한 사건이 두 줄이 되지 않는다. `incident.opened_at_ms`만으로 거는 인덱스는 없다. 사건은 위급 판정마다 하나라 행 수가 작아 이번에는 범위 스캔으로 둔다.

4. 머리글 + 행을 UTF-8 CSV로 만든다. 스프레드시트가 수식으로 읽는 문자(`= + - @` 탭·CR)로 시작하는 문자열은 앞에 작은따옴표를 붙인다(LLD-0108과 같은 규칙). AI 사유 원문이 들어가기 때문이다. BOM은 붙이지 않는다(LLD-0108과 같음). 엑셀에서는 「데이터 가져오기」로 UTF-8을 지정해 연다.
5. 로그에는 기간 일수 없이 **행 수만** 남긴다. 회원 ID·심박·사유를 남기지 않는다(#639).

### 5.2 `senior_response` 계산

서버가 상태를 정하는 규칙(LLD-0070 5.2)과 같은 기준으로 계산한다. 응답 시각이 마감과 같거나 늦으면 늦은 응답이다.

| 조건 | 값 |
| --- | --- |
| `response = HELP` | `HELP` |
| `response = OK`이고 `responded_at_ms < respond_by_ms` | `OK` |
| `response = OK`이고 마감과 같거나 뒤 | `NO_RESPONSE` (늦은 응답. `response` 칸에 `OK`가 남는다) |
| 응답 없음, 내보낸 시각이 마감 이후 | `NO_RESPONSE` |
| 응답 없음, 아직 마감 전 | `PENDING` |

늦은 `OK`를 `NO_RESPONSE`로 두는 이유: 서버는 이미 그 사건을 무응답으로 보고 보호자 단계로 올렸다. 「괜찮아요 비율」에 넣으면 보호자에게 간 알림이 괜찮았던 일로 잡힌다. 원래 값은 `response`에 남아 연구팀이 다시 나눌 수 있다.

### 5.3 ADR-0042 값은 칸을 두고 비운다

빼는 대신 빈칸으로 둔다. 연구팀이 이 CSV로 분석 양식을 먼저 만들면, ADR-0042 뒤 값이 채워져도 칸 순서가 바뀌지 않아 양식을 고치지 않는다. 비어 있는 것이 「현재 남지 않음」이라는 뜻임은 Swagger와 3.3 표에 적는다.

### 5.4 보호자 알림 칸 두 가지

- `guardian_alert_queued_at_ms`(`incident.initial_alert_sent_at_ms`): 보호자 최초 알림을 발송 대기열에 넣은 시각이다. 모든 사건에 남는다. 다만 가족이 없거나 방장이 0명이면 발송 없이도 이 시각이 찍힌다(LLD-0070 5.3, 게이트를 닫는 용도). 또 `self-check-first=false`(현재 기본값)이면 사건이 열리자마자 찍혀, 어르신 응답과 무관하게 모든 사건이 보호자 단계에 들어간다.
- `guardian_push_delivered`·`_at_ms`(`decision_record.alert_delivered`·`alert_at_ms`): FCM이 받아 준 첫 성공이다. 회차 있는 알림만 남는다. 「보호자에게까지 간 비율」은 이 칸이 더 정확하다.

## 6. 예외 / 에러 처리

새 ErrorCode 없음.

| 상황 | 응답 |
| --- | --- |
| `from >= to` 또는 31일 초과 | 400 `SENSOR_4004` |
| `from`·`to` 누락·형식 오류 | 400 (공통 요청 검증) |
| 관리자 아님 | 403 (보안 설정) |
| 기간에 알림 없음 | 200, 머리글만 있는 CSV |

## 7. 인수조건 (Acceptance Criteria)

- [x] 기간 안에 열린 심박·낙상 사건이 사건마다 한 줄로 알림 시각 순서대로 내려온다. 기간 밖, `SELF_HELP_REQUEST`, `SAFE_ZONE_EXIT` 사건은 없다.
- [x] 회차 있는 심박 알림은 판정 기록의 `hr_bpm`·`hr_measured_at_ms`·`hr_accuracy`·`ai_reason`·`guardian_push_delivered`·`guardian_push_delivered_at_ms`와 판정 묶음의 `on_body`·`wear_state`가 채워진다.
- [x] 회차 없는 알림은 그 칸들이 비고 사건 칸(응답·상태·보호자 알림 단계 시각)은 채워진다.
- [x] `baseline_bpm`·`held_seconds`·`activity_state`는 머리글에 있고 값은 비어 있다.
- [x] `senior_response`가 5.2 표대로 계산된다(기한 안 OK, HELP, 정확히 마감의 OK, 응답 없음 마감 후, 마감 전).
- [x] 수식처럼 시작하는 사유 문자열은 작은따옴표가 붙고, 쉼표·따옴표가 든 값은 CSV 규칙대로 감싸진다.
- [x] `from >= to`나 31일 초과는 `SENSOR_4004`다.
- [x] 관리자 경로라 접속기록이 남고(기존 인터셉터), 로그에는 행 수만 남는다.
- [x] Swagger에 파라미터·칸 설명·오류가 반영된다.
- [x] `bash scripts/harness/verify.sh`가 통과한다.

## 8. 영향 범위 / 마이그레이션

신규 API·저장소만 추가한다. DDL·ENUM 변경 없음. 기존 회차 내보내기와 센서 시간대 내보내기는 바뀌지 않는다.

## 9. 미결정 사항 (Open Questions)

- 없음. 본인 기준값·이어진 시간·활동 상태를 언제 채울지는 ADR-0042가 정한다.

## 10. 참고

- 노션 「알림마다 판정 근거와 어르신 응답을 저장하고 내보낼 수 있게 하기」(긴급도 1) 1-1~1-3
- `IncidentService.openForAlert`(사건 열기), `IncidentEscalation`(보호자 최초 알림 게이트), `FcmOutboxTransactions.finish`(판정의 알림 도달), `SensorWindowExportService`(관리자 내보내기 패턴)
