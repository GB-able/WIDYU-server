# LLD-0143: 위치 원본 신규 저장을 중단하고 위치 ACK를 실시간 처리 결과로 바꾼다

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #1047 |
| 관련 ADR | ADR-0045 |
| 대체 | LLD-0048의 원본 저장, LLD-0123의 저장 기반 ACK, LLD-0139의 원본 격리 |
| 선행 LLD | [LLD-0141](LLD-0141-location-freshness-and-stay-state-db.md)(#1045), [LLD-0142](LLD-0142-location-service-responsibility-split.md)(#1046) |
| 작성자 | Claude (Opus 5.5) |
| 작성일 | 2026-11-13 |

## 1. 목적 / 배경

ADR-0045. 위치 원본의 신규 영구 저장을 실증 회차까지 포함해 중단하고, 그 때문에 수신 경로에 붙어 있던 원본 전용 처리를 없앤다. 과거 자료와 내보내기는 그대로 둔다.

## 2. 범위

### In scope

- `LocationIngestService`: 원본 저장, 유일 키 경합 복구, 실시간 실패 뒤 원본만 저장하는 분기, 회차 귀속 확인을 없앤다. v2 입력 검증을 옮겨 둔다. 반영하지 않은 위치의 시니어 확인을 더한다.
- `LocationFixService` 삭제(쓰는 곳이 수신 경로뿐이었다). `LocationFix` 엔티티와 `LocationFixRepository`는 과거 자료 조회용으로 남긴다.
- ACK 결과 `APPLIED`, `SKIPPED`, `REJECTED`
- 회차 내보내기: 위치 스트림이 없으면 `LOCATION_RAW_NOT_COLLECTED`로 신고한다.

### Out of scope

- `location_fix` 테이블, 행 삭제(DROP, TRUNCATE, 일괄 삭제 없음). 보관 기간 정리(LLD-0136), 탈퇴 삭제는 그대로다.
- 심박, IMU, 기기 하트비트 원본 수집
- 앱 코드

## 3. 인터페이스 / API

`/app/location/update` 요청 형식은 그대로다(구형 4필드, v2). v2의 `run_id`, `study_id`, `participation_id`는 받되 쓰지 않는다.

`/user/queue/location/ack`(발신 세션) 응답:

| 상황 | 이전 | 이후 |
| --- | --- | --- |
| 판정 커밋, Redis 최신 위치 반영 | `STORED`(원본 새로 저장) 또는 `DUPLICATE` | `APPLIED` |
| 15분 넘게 지남, 최신보다 먼저 잰 위치, 같은 위치 재전송, 더 늦은 위치가 반영됨 | 원본 저장 결과(`STORED`, `DUPLICATE`) | `SKIPPED` |
| 입력 오류, 본인 아님, 시니어 아님 | `REJECTED` | `REJECTED` |
| 남의 회차를 실어 보냄 | `REJECTED` | 회차를 쓰지 않으므로 실시간 결과(`APPLIED` 등) |
| 판정, 최신 위치 저장 기술 오류 | ACK 없음(원본은 저장 시도) | ACK 없음, `/user/queue/errors` |
| 회원 대기 초과 | ACK 없음(원본은 저장) | ACK 없음 |
| 구형 4필드 | 식별자, 결과 null | 그대로 |

ACK는 "서버가 이 위치의 처리를 끝냈다"는 뜻이다. 보호자가 받았다는 뜻은 아니다. `SKIPPED`의 `location`은 이 요청의 좌표이고 `locationSource=REQUEST`다.

**소비자 확인:** 앱 develop `33efddb5`(2026-11-12)는 `/app/location/update`로 v2를 보내기만 하고 `/user/queue/location/ack`를 구독하지 않는다(`lib/core/network/socket_client.dart`는 센서 결과와 `/user/queue/errors`만 구독). 따라서 결과 이름을 바꿔도 깨지는 앱 소비자가 없다. 앱이 ACK를 쓰게 되면 `APPLIED`, `SKIPPED`, `REJECTED` 모두 큐에서 지워도 되고, ACK가 없으면 다시 보낸다.

## 4. 데이터 모델

변경 없음. `location_fix`는 이 변경 이후 새 행이 생기지 않는다.

## 5. 처리 흐름

```
receive ─ 파싱, 검증(구형: 좌표 / v2: ts_ms, session_id, seq, reason, 좌표 범위)
        ─ applyRealtime(본인, 15분, 회원 잠금 → 최신 비교 → judge → project)
        ─ 반영 안 했으면 judge.requireSenior (짧은 읽기 트랜잭션)
        ─ ACK: APPLIED / SKIPPED / REJECTED, 기술 오류는 던짐, 대기 초과는 null
```

- 시니어 확인: 반영한 위치는 판정 트랜잭션의 프로필 조회가 확인한다. 반영하지 않은 위치는 예전에 원본 저장이 확인했으므로, 이제 `requireSenior`가 한다. 조율자는 트랜잭션 없는 JPA 조회를 하지 않는다(LLD-0139).
- 중복: 같은 위치의 재전송은 Redis 최신 순서 시각과 같아 `SKIPPED`다. 체류 상태와 알림은 판정을 다시 돌지 않아 중복되지 않는다.
- 실패 정책(LLD-0142 3.4)은 그대로다. 최신 위치 저장 실패는 던지고 경로, 방송, 이벤트 실패는 로그만 남긴다.

## 6. 내보내기

- 과거 회차: `location_fix` 행을 그대로 `streams/location_<기기>.jsonl`로 쓴다.
- 행이 없는 회차: 위치 파일이 없고 `streams_absent`에 `{"stream":"location","device_id":<폰>,"reason":"LOCATION_RAW_NOT_COLLECTED"}`. 「자료 없음」(`NO_DATA_IN_THIS_RUN`)이면 유실로 읽히기 때문이다. 이 변경 이전 회차에서 이 사유는 그때 저장된 위치가 없었다는 뜻이다.
- 관리자 센서 구간 내보내기의 `location.csv`(`SensorWindowExportService`): 과거 행만 나오고 이후 구간은 머리글만 남는다.
- 활동 상태 `IN_VEHICLE`: 위치 속도를 쓰므로 새 자료에서는 판정되지 않는다(`sensor.heart-ai.activity-context-enabled` 기본 false).

## 7. 인수조건

- [ ] v2 위치를 반영하면 `APPLIED`, 오래됐거나 최신보다 먼저 잰 위치, 같은 위치 재전송, 밀린 위치는 `SKIPPED`, 입력 오류, 남의 위치, 시니어 아님(건너뛰는 위치 포함)은 `REJECTED`다(`RealtimeLocationAckContractTest`)
- [ ] 판정 DB 오류, Redis 오류면 ACK가 없고 최신 위치, 방송, 위치 원본이 남지 않는다. 최신 위치 저장 실패 뒤 재전송은 `APPLIED`로 복구되고 방송은 한 번이다
- [ ] 실제 프록시에서 판정 커밋 뒤 커넥션 0, 이미 있던 `location_fix` 행이 그대로이고 새 행이 없다(`LocationIngestTransactionBoundaryTest`)
- [ ] 실제 MySQL에서 과거 행이 있는 회원들의 몰림, 오래된 몰림, 최신 우선, 첫 위치 동시, 같은 위치 16건 동시 뒤 `location_fix` 행 수가 같고 교착이 없다. 같은 위치 동시 수신은 `APPLIED` 한 번, 상태 행 하나다(`LocationBurstConnectionPoolMySqlTest`)
- [ ] 위치가 없는 회차는 `LOCATION_RAW_NOT_COLLECTED`로 신고하고 과거 위치는 그대로 내보낸다(`RunExportAssemblerTest`)
- [ ] k6 같은 조건 전후 비교(8절)
- [ ] `bash scripts/harness/verify.sh` 통과

## 8. 전후 측정

구현 뒤 채운다.

## 9. 미결정 사항

없음(사용자 결정).
