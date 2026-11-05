# LLD-0123: 위치 ACK가 원본 저장을 확인해 주도록 요청 식별자와 처리 결과를 싣는다

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #969 |
| 관련 ADR | ADR-0041(WebSocket 끊김 대응의 서버와 앱 책임, 결정 2, Proposed) |
| 선행 LLD | [LLD-0048](LLD-0048-location-fix.md)(위치 원본), [LLD-0114](LLD-0114-location-burst-single-connection.md)(재연결 몰림), [LLD-0121](LLD-0121-realtime-location-side-effects-after-tx.md)(실시간 부수효과를 커밋 뒤로) |
| 작성자 | Claude |
| 작성일 | 2026-11-05 |

## 1. 목적 / 배경

시니어 앱(develop `17cd0a1`)은 위치를 STOMP `send()`가 예외 없이 끝나면 보낸 것으로 보고 큐에 남기지 않는다. `send()`는 소켓에 쓰기만 하므로 서버 수신 확인이 아니다. 소켓이 조용히 끊기면 앱은 서버 heart-beat(10초) 기준으로 20~30초 뒤에야 알아차리고, 그 사이 보낸 위치는 서버에 오지 않았는데도 사라진다. 서버도 원본 저장에 실패하면 WARN만 남기고 ACK를 보냈다(`RealtimeLocationService.storeQuietly`).

앱은 위치 ACK(`/user/queue/location/ack`)를 받고 지우는 방식으로 바꿀 수 있다고 답했다. 그런데 지금 ACK에는 어느 위치에 대한 응답인지 알 식별자가 없다. 앱은 밀린 위치를 한꺼번에 보내고 서버는 같은 세션의 메시지를 inbound 실행기에서 동시에 처리하므로 ACK 순서로도 맞출 수 없다.

개발 서버 `location_fix`에서 10/22 이후 같은 세션 안에서 빠진 `seq`는 회원 29가 487건, 회원 30이 667건이다. 큰 구멍은 앱 큐 상한(720건) 초과이고(앱 몫), 작은 구멍은 감지 전 유실과 LLD-0114 전 원본 저장 실패가 섞여 있다.

이 LLD는 위치 ACK를 "앱이 큐에서 지워도 된다"는 신호로 바꾼다. 원본을 저장했거나 이미 있으면 ACK를 보내고, 저장하지 못했으면 ACK를 보내지 않는다.

## 2. 범위

### In scope
- 변경 모듈: widyu-api
- 발신 세션 ACK 전용 응답 `LocationAckResponse`. 기존 ACK 필드(`LocationUpdateResponse`)를 같은 단계에 그대로 두고 `deviceId`, `sessionId`, `seq`, `result`를 덧붙인다
- `result`: `STORED`(새로 저장), `DUPLICATE`(이미 있음), `REJECTED`(요청 자체가 잘못됨)
- 원본 저장이 업무 예외가 아닌 이유로 실패하면 ACK를 보내지 않는다(`storeQuietly` 제거)
- v2 위치 처리 중 업무 예외(`BusinessException`)가 나면 오류 대신 `REJECTED` ACK
- `LocationFixService.store`가 새로 저장했는지 돌려준다

### Out of scope
- 보호자 방송(`/topic/location/senior/{id}`)과 REST 위치 조회 응답 형식. `LocationUpdateResponse`를 그대로 쓴다
- WebSocket 오류 본문 형식(`code`, `retryable`) 변경. 앱이 `/user/queue/errors`를 처리하지 않고 ACK 시간 초과로 재전송하므로 지금은 필요 없다(ADR-0041 결정 3 보류)
- 앱의 큐 상한, 디스크 저장, 재연결 직후 최신 위치 우선 전송(앱 몫)
- 같은 `(device_id, session_id, seq)`가 동시에 두 번 들어와 저장이 유일 키에서 충돌하는 경우의 중복 판정. 실패로 보고 ACK를 보내지 않는다(아래 5절)

## 3. 인터페이스 / API

주소는 그대로다. 시니어 앱 `SEND /app/location/update` → 발신 세션 `/user/queue/location/ack`.

v2 위치 ACK(저장 또는 중복):

```json
{
  "memberId": 1, "name": "시니어", "profileImage": null,
  "latitude": 37.5551, "longitude": 126.9707, "updatedAt": "2026-11-05T15:00:00",
  "stayStartTime": "2026-11-05T14:40:00", "stayDurationMinutes": 20,
  "locationType": "OTHER", "locationName": null,
  "deviceId": "ph-9c1", "sessionId": "s-2026", "seq": 5120, "result": "STORED"
}
```

v2 위치 거절:

```json
{ "deviceId": "ph-9c1", "sessionId": "s-2026", "seq": 5120, "result": "REJECTED" }
```

구형 위치(원본 저장 대상이 아님, `isRawFix()=false`): 기존 필드는 이전과 같고 `deviceId`, `sessionId`, `seq`, `result`는 null이다. 구형 위치의 ACK는 계속 "실시간 처리 완료"만 뜻한다.

| 상황 | ACK | 앱 동작 |
| --- | --- | --- |
| 원본 저장 | `result=STORED` | 큐에서 삭제 |
| 같은 위치가 이미 저장됨 | `result=DUPLICATE` | 큐에서 삭제 |
| v2 위치가 업무 예외로 거절됨 | `result=REJECTED` | 큐에서 삭제(다시 보내도 같다) |
| 원본 저장 실패, Redis 장애 등 | 보내지 않음(`/user/queue/errors`로 오류) | 시간 초과 뒤 재전송 |
| 본문을 읽을 수 없음 | 보내지 않음(`LOCATION_4000` 오류) | 식별자를 알 수 없어 시간 초과로 처리 |

## 4. 데이터 모델

테이블 변경 없음.

- `com.widyu.location.realtime.dto.LocationAckResponse`(widyu-api): `@JsonUnwrapped LocationUpdateResponse location`, `deviceId`, `sessionId`, `seq`, `result`. 팩토리 `of`, `rejected`, `legacy`
- `com.widyu.location.realtime.dto.LocationAckResult`(widyu-api): `STORED`, `DUPLICATE`, `REJECTED`

## 5. 처리 흐름

`RealtimeLocationService.updateAndBroadcast(byte[], memberId)`(트랜잭션 없음, LLD-0114):

1. 본문 파싱. 실패하면 `LOCATION_FIX_INVALID` 예외(식별자를 읽을 수 없어 ACK 없음)
2. 구형 위치면 검증, 실시간 반영 후 `LocationAckResponse.legacy`. 예외는 그대로 던진다(이전과 같음)
3. v2 위치는 아래를 한 `try`에서 차례로 실행한다
   1. 요청 검증, 원본 검증(`LocationFixService.validate`)
   2. 실시간 반영(`applyRealtime`, LLD-0114 반영 조건, LLD-0121 트랜잭션 경계 그대로)
   3. 원본 저장(`LocationFixService.store`, REQUIRES_NEW). 이미 있으면 false
4. 그 안에서 `BusinessException`이 나면 `REJECTED` ACK. 이 경로의 업무 예외는 모두 요청 자체의 문제다: 좌표와 필드 검증(`LOCATION_4000`), 본인 아님(`FORBIDDEN`), 시니어 아님(`BAD_REQUEST`), 이 회원과 기기에 배정되지 않은 회차(`RUN_*`)
5. 그 밖의 예외(DB, Redis, 직렬화)는 잡지 않는다. `WebSocketExceptionHandler`가 `/user/queue/errors`로 보내고 ACK는 나가지 않는다
6. 저장했으면 `STORED`, 이미 있었으면 `DUPLICATE` ACK

재전송 시 실시간 경로: 첫 시도에서 최신 위치가 저장됐다면 같은 측정 시각의 재전송은 "최신보다 나중에 잰 위치"가 아니라서 반영되지 않고 원본만 저장된다(LLD-0114). 최신 위치 저장 전에 실패했다면 재전송이 실시간 후보로 다시 평가된다(LLD-0121 `publishAfterTransaction`).

동시 중복: 같은 위치가 거의 동시에 두 번 오면 둘 다 존재 확인을 통과하고 한쪽이 유일 키 `(device_id, session_id, seq)`에서 실패할 수 있다. 실패한 쪽은 ACK가 없고, 앱이 다시 보내면 `DUPLICATE`가 된다.

## 6. 예외 / 에러 처리

새 오류 코드 없음.

| 조건 | 결과 |
| --- | --- |
| 본문 파싱 실패 | `LOCATION_FIX_INVALID` 예외 → `/user/queue/errors` |
| v2 위치의 `BusinessException`(검증, 권한, 회차) | `REJECTED` ACK, WARN 로그 `memberId`, `seq`, `code`(좌표 없음, LLD-0029) |
| v2 위치의 원본 저장 실패(업무 예외 아님) | ACK 없음, `/user/queue/errors` |
| 구형 위치의 예외 | 이전과 같이 `/user/queue/errors` |

주의: 이 경로에 일시적 실패를 `BusinessException`으로 던지는 코드를 추가하면 그 위치는 `REJECTED`로 지워져 재전송되지 않는다. 일시적 실패는 업무 예외가 아닌 예외로 던진다.

## 7. 인수조건 (Acceptance Criteria)

- [x] 저장된 v2 위치의 ACK에 요청과 같은 `deviceId`, `sessionId`, `seq`와 `result=STORED`가 실리고 기존 필드는 그대로다
- [x] 이미 저장된 v2 위치가 다시 오면 `result=DUPLICATE` ACK를 돌려준다
- [x] 원본 저장이 업무 예외가 아닌 이유로 실패하면 ACK를 돌려주지 않고 예외를 전달한다
- [x] v2 위치가 원본 검증에 실패하면 방송과 저장 없이 `result=REJECTED` ACK를 돌려준다
- [x] 다른 회원의 v2 위치는 `REJECTED`이고 원본을 저장하지 않는다
- [x] 원본 저장이 회차 귀속 검증으로 거부되면 `REJECTED`다
- [x] 구형 위치의 ACK는 새 필드가 null이고 기존 필드는 이전과 같다. 구형 위치의 검증 실패는 이전처럼 예외다
- [x] ACK JSON은 기존 필드를 감싸지 않고 같은 단계에 둔다(`location` 키 없음)
- [x] 보호자 방송과 REST 조회 응답 형식은 바뀌지 않는다(`LocationUpdateResponse` 그대로)
- [x] `./gradlew :backend:widyu-api:test`가 통과한다

## 8. 영향 범위 / 마이그레이션

- DB 변경 없음
- **구형 ACK 수신자 호환:** 필드만 추가했다. 현재 시니어 앱은 위치 ACK를 구독하지 않는다
- **동작 변경:** v2 위치의 원본 저장 실패와 검증 실패가 이전에는 각각 "ACK 정상"과 "`/user/queue/errors`"였다. 이제 "ACK 없음"과 "`REJECTED` ACK"다. 앱이 ACK를 받고 지우도록 바뀌기 전까지 앱 동작에는 영향이 없다(앱은 ACK와 오류를 모두 무시)
- `LocationFixService.store`의 반환형이 `void`에서 `boolean`이 된다. 호출자는 `RealtimeLocationService` 하나다
- `backend/CLAUDE.md` location 절의 "저장 실패는 WARN만 남기고 ACK를 막지 않는다"를 고친다
- `docs/architecture/websocket.md`의 위치 ACK 설명을 갱신한다

## 9. 미결정 사항 (Open Questions)

- 없음. 앱의 ACK 대기 시간과 재전송 간격은 앱이 정한다(앱 문서는 센서와 같은 패턴을 제안했다)

## 10. 참고

- 앱 문서 「위듀 앱 WebSocket 동작 정리」(시니어 앱 develop `17cd0a1`, 2026-11-05) 5, 6, 12절
- ADR-0041 결정 2(PR #957)
