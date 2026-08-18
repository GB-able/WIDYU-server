# LLD-0023: 심박 단건 전송 경로

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Approved |
| Issue | #482 |
| 관련 ADR | ADR-0017 |
| 관련 LLD | LLD-0019 |
| 작성자 | Claude |
| 작성일 | 2026-08-18 |

## 1. 목적 / 배경

워치가 1초 간격 측정값 15개를 약 15.5초마다 배치로 전송해, 심박 이상치 조회 API를 초 단위로 폴링해도
최신값이 15초에 한 번만 갱신된다. AI는 이미 단건 계약이므로 배치는 앱 계약 보존을 위한 선택이었다(ADR-0013).

측정값을 1초마다 1건씩 받아 즉시 판정·저장하는 경로를 추가해 조회 지연을 없앤다.

## 2. 범위

### In scope

- 변경 모듈: widyu-api
- WebSocket 단건 전송 엔드포인트 `/app/heart-rate/send-single`을 추가한다.
- 수신 즉시 AI에 1회 요청하고 결과를 저장한다.
- 멱등성 기준을 배치 시작 시각에서 측정 시각 단건으로 둔다.
- 기존 15개 배치 경로는 그대로 유지한다.

### Out of scope

- 기존 배치 경로 제거 (워치 앱 전환 완료 후 별도 작업)
- REST 전송 엔드포인트 추가
- AI 판정 로직, 위급 저장 정책, 조회 API 계약 변경
- 저장 묶음 처리나 큐 도입 등 부하 대응

## 3. 인터페이스 / API

```text
SEND      /app/heart-rate/send-single
SUBSCRIBE /topic/heart-rate/{memberId}
ACK       /user/queue/heart-rate/result
```

브로드캐스트 토픽과 ACK 큐는 배치 경로와 동일하다.

요청:

```json
{
  "heartRate": 78,
  "measuredAt": "2026-08-18T22:00:53.284",
  "location": "서울시",
  "context": "REST"
}
```

- `heartRate`: 정수, 1~299 (AI가 0과 300을 거부한다)
- `measuredAt`: 필수
- `location`: 선택. 위급상황 기록에 사용한다
- `context`: `REST`, `LOW`, `ACTIVE`, `UNKNOWN`과 공백만 허용. 값과 무관하게 AI에는 `UNKNOWN`을 전달한다(#477)

응답은 기존 `HeartRateStatusResponse`를 그대로 사용한다.

```json
{
  "memberId": 1023,
  "heartRateStatus": "NORMAL",
  "heartRate": 78,
  "measuredAt": "2026-08-18T22:00:53.284"
}
```

## 4. 데이터 모델

신규 테이블·컬럼은 없다. `heart_rate_event`의 `(member_id, measured_at)` 유니크 제약을 멱등성 근거로 사용한다.

## 5. 처리 흐름

1. `HeartRateWebSocketController.sendHeartRate()`가 단건 요청을 받는다.
2. `HeartRateService.processHeartRate()`가 회원 존재를 확인한다.
3. 같은 `memberId`와 `measuredAt`의 이벤트가 있으면 저장 없이 현재 상태를 반환한다.
4. `HeartRateAnomalyDetector.detect()`에 측정값 1건을 담은 리스트를 전달한다.
   배치 경로와 같은 메서드를 사용하며, 15개 고정 검증은 배치 요청 DTO의 `@Size`가 담당한다.
5. `HeartRatePersistenceService.saveMeasurement()`가 최신 결과와 이벤트 1건을 저장하고,
   위급상황이면 `HeartRateEmergency`도 저장한다.
6. 위급상황이면 `HeartRateEmergencyEvent`를 발행한다.
7. 결과를 토픽과 발신자 ACK로 전달한다.

트랜잭션 경계는 ADR-0008을 유지한다. AI 호출은 트랜잭션 밖, 저장만 `@Transactional`이다.

## 6. 예외 / 에러 처리

배치 경로와 동일하게 `WebSocketExceptionHandler`가 처리해 `/user/queue/errors`로 전달한다.

| 조건 | 발신자가 받는 `error` | 저장 |
| --- | --- | --- |
| 심박수가 1 미만 또는 299 초과 | `MethodArgumentNotValidException` | 안 함 |
| `measuredAt` 누락 | `MethodArgumentNotValidException` | 안 함 |
| 정규화 후 `context`가 허용값이 아님 | `MethodArgumentNotValidException` | 안 함 |
| AI 통신 실패·빈 응답·지원하지 않는 `level` | `BusinessException` | 안 함 |
| 회원 없음 | `BusinessException` (`MEMBER_NOT_FOUND`) | 안 함 |

측정값 하나가 실패해도 다른 측정에는 영향이 없다. 배치 경로에서는 값 하나가 잘못되면 15개 전체가 저장되지 않았다.

## 7. 인수조건 (Acceptance Criteria)

- [x] `/app/heart-rate/send-single`로 측정값 1건을 보내면 AI 1회 호출 후 저장하고 결과를 반환한다.
- [x] 같은 `memberId`와 `measuredAt`을 재전송하면 저장 없이 기존 상태를 반환한다.
- [x] 위급 판정이면 `HeartRateEmergency`를 저장하고 `HeartRateEmergencyEvent`를 발행한다.
- [x] 위급이 아니면 이벤트를 발행하지 않는다.
- [x] AI 실패 시 아무것도 저장하지 않는다.
- [x] 기존 배치 경로(`/app/heart-rate/send`)의 동작이 변하지 않는다.
- [x] `bash scripts/harness/run-module-tests.sh`가 통과한다.

## 8. 영향 범위 / 마이그레이션

- 시니어 1명당 WebSocket 메시지가 약 0.065건/초에서 1건/초로 늘어난다. DB 쓰기 트랜잭션도 같은 비율로 늘고 각각은 짧아진다.
- 조회 지연이 배치 수집 주기만큼(약 15초) 사라진다.
- AI 호출 총량은 변하지 않는다.
- AI의 30초 지속 판정은 시간 기준이므로 전송 단위 변경으로 판정 결과가 달라지지 않는다.
- 전환 기간에 두 경로가 공존한다. 앱이 두 경로로 같은 측정값을 동시에 보내면 멱등성 제약으로 한쪽만 저장된다.

## 9. 미결정 사항 (Open Questions)

- 워치 앱 전환 완료 시점. 이후 배치 경로 제거를 별도 이슈로 진행한다.
- 동시 접속 규모가 커질 때의 메시지 유입량과 DB 쓰기 부하. 측정 후 필요하면 대응한다.

## 10. 참고

- ADR-0017
- ADR-0013 (배치 유지 결정, 이 문서로 대체)
- LLD-0019
