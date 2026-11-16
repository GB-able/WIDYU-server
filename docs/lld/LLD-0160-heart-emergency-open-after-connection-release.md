# LLD-0160: 심박 위급 사건을 저장 트랜잭션이 커넥션을 돌려준 뒤 열기

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #1056 |
| 관련 ADR | - (같은 구조의 선례는 [LLD-0152](LLD-0152-read-then-record-single-connection.md), [LLD-0159](LLD-0159-location-query-read-then-record.md), [LLD-0114](LLD-0114-location-burst-single-connection.md)) |
| 작성자 | Claude |
| 작성일 | 2026-11-16 |

## 1. 목적 / 배경

심박 위급을 저장하면(`HeartRatePersistenceService.saveMeasurement`, `saveBatchSample`) 저장 트랜잭션 안에서 `HeartRateEmergencyEvent`를 발행하고, 커밋 후 리스너(`HeartRateEmergencyNotificationService`, `@TransactionalEventListener(AFTER_COMMIT)` + `REQUIRES_NEW`)가 사건을 열고 보호자 최초 알림을 냈다. Spring은 커밋 후 콜백을 트랜잭션 정리(커넥션 반환) 전에 돌리므로, 리스너의 새 트랜잭션이 커넥션을 하나 더 잡아 요청 하나가 커넥션 두 개를 쥔다. #1056 점검 때는 추정이었고, 이번에 측정으로 확인했다(9절).

동시 위급이 풀을 넘으면 리스너가 커넥션을 기다리다 대기 한도(3000ms) 뒤 실패한다. Spring은 커밋 후 콜백의 예외를 `TransactionSynchronization.afterCompletion threw exception` ERROR 로그로 삼키므로, 심박과 위급 기록은 저장되고 응답도 성공인데 사건이 열리지 않아 보호자 알림이 나가지 않는다.

## 2. 범위

### In scope
- 변경 모듈: widyu-api
- 저장 트랜잭션에서 위급 이벤트 발행을 없애고, 저장이 끝난 호출자(`HeartRateService.processHeartRate`, `HeartRateBatchService`)가 새 `HeartRatePersistenceService.notifyEmergency`로 사건을 연다.
- `HeartRateEmergencyNotificationService.handleHeartRateEmergency`에서 커밋 후 리스너 지정을 뗀다. 자기 트랜잭션(`REQUIRES_NEW`)과 회원 행 잠금 순서(LLD-0103)는 그대로다.
- 풀을 줄인 실제 MySQL 재현 테스트(`ReadThenRecordConnectionPoolMySqlTest`)에 심박 위급 경우를 추가한다.

### Out of scope
- 위치 REST 조회: #1079(LLD-0159)에서 이미 고쳤다. WebSocket 구독(`JwtChannelInterceptor`의 `WS_SUBSCRIBE`)은 트랜잭션 밖에서 가족 권한 확인이 끝난 뒤 기록하므로 중첩이 없다(10절).
- 사건 열기가 실패했을 때 저하 경로(DB 없이 알림)로 보내는 것. 지금처럼 ERROR 로그만 남긴다(9절 Open Question).
- Hibernate 커넥션 반환 시점(`hibernate.connection.handling_mode`) 전역 변경(10절).

## 3. 인터페이스 / API

HTTP, WebSocket 요청과 응답은 바뀌지 않는다.

## 4. 데이터 모델

DB 스키마 변경 없음.

- `HeartRatePersistenceService`: `ApplicationEventPublisher` 대신 `HeartRateEmergencyNotificationService`에 의존한다. 새 메서드 `notifyEmergency(Long memberId, DecisionRecord decision)`(트랜잭션 없음).
- `HeartRateEmergencyEvent`는 발행하지 않고 `handleHeartRateEmergency`의 인자로만 쓴다.

## 5. 처리 흐름

### 5.1 단건 — `HeartRateService.processHeartRate` (트랜잭션 없음, 기존과 같음)
1. `saveMeasurement`(자기 트랜잭션): 심박 이벤트, 위급이면 위급 기록을 저장하고 커밋한다. 커넥션을 돌려준다.
2. 위급이면 `notifyEmergency(memberId, null)`.
3. `updateLatestResult`(Redis 최신값, 기존과 같다).

### 5.2 배치 — `HeartRateBatchService.saveUnlessConcurrentlyStored`
1. `saveBatchSample`(자기 트랜잭션): 판정 기록, 심박 이벤트, 위급 기록을 한 트랜잭션으로 저장하고 커밋한다(LLD-0053 5.1 유지).
2. 동시 재전송 UK 충돌로 건너뛴 샘플이 아니고 위급이면 `notifyEmergency(memberId, decision)`.

### 5.3 `notifyEmergency`
1. `decision`이 있으면 판정 식별자를 꺼낸다(LLD-0053 5.2의 알림 도달 기록용).
2. `handleHeartRateEmergency(new HeartRateEmergencyEvent(memberId, decisionId))`. 바깥 트랜잭션이 없어 커넥션 1개로 사건을 열고 최초 알림을 낸다.
3. `RuntimeException`은 잡아서 ERROR 로그(회원 식별자, 스택)만 남긴다. 이전 리스너도 예외를 호출자에게 전하지 않았다. 던지면 단건은 DB 장애로 오인돼 저하 경로로 가고, 배치는 재전송이 반복된다.

## 6. 예외 / 에러 처리

새 에러 코드 없음.

- 저장이 실패하거나(롤백) 락 충돌 재시도가 끝내 실패하면 사건을 열지 않는다. 이전에도 롤백된 트랜잭션의 커밋 후 리스너는 돌지 않았다.
- 락 충돌 재시도(LLD-0076)는 저장 메서드 안에서 끝나므로, 사건은 성공한 저장 뒤 한 번만 열린다.
- 사건 열기 실패는 ERROR 로그만 남기고 심박 응답은 성공이다(기존과 같다).

## 7. 인수조건 (Acceptance Criteria)

- [x] 심박 위급 한 건을 저장하고 사건을 여는 동안 Hikari 사용 중 커넥션이 1개를 넘지 않는다(`ReadThenRecordConnectionPoolMySqlTest`, 수정 전 2개).
- [x] 풀 4개, 대기 3000ms, 16개 스레드에서 시니어 32명의 위급을 동시에 받아도 실패 0건이고 위급 기록 32행, 사건 32행이 남는다.
- [x] 단건, 배치 모두 저장 호출이 끝난 뒤 사건을 연다(`HeartRateServiceTest`, `HeartRateBatchServiceTest` 순서 검증). 저장 메서드는 사건을 열지 않는다(`HeartRatePersistenceServiceTest`, `HeartRatePersistenceRetryIntegrationTest`).
- [x] 사건 열기가 실패해도 `notifyEmergency`는 예외를 내지 않는다(`HeartRatePersistenceServiceTest`).
- [x] `bash scripts/harness/verify.sh`가 통과한다(API 2,158건, 실패 0, 제외 52).

## 8. 영향 범위 / 마이그레이션

- DDL 없음, 설정 변경 없음, 클라이언트 영향 없음.
- 사건 열기가 커밋 후 콜백에서 호출자의 저장 직후로 옮겨진다. 같은 스레드에서 같은 순서(저장 커밋 → 사건 → 최신값)로 돌아 지연은 늘지 않는다.
- `HeartRateEmergencyAfterCommitTest`는 커밋 후 리스너의 예외 처리를 보던 테스트라 지우고, 같은 내용을 `HeartRatePersistenceServiceTest`의 실패 무시 테스트로 옮겼다.

## 9. 미결정 사항 (Open Questions)

- [ ] 사건 열기가 실패했을 때(커넥션 대기 외의 DB 오류 등) 저하 경로(`DegradedEmergencyAlertService`)로 보호자에게 알릴지. 기본값은 지금처럼 ERROR 로그만 남긴다. 이번 수정으로 커넥션 대기 때문에 사건이 빠지는 경로는 없앴다.

### 측정 (2026-11-16, 로컬 Docker MySQL 8.0.44, Redis 7)

같은 테스트(`ReadThenRecordConnectionPoolMySqlTest`, 풀 4개, 대기 3000ms)를 수정 전 코드(`089ad847`)와 수정 후 코드에서 돌렸다. 진입점은 단건 심박 수신이 부르는 `HeartRateService.processHeartRate`이고 AI 판정만 대역(항상 위급)이다. 저하 경로는 꺼서 DB 실패가 응답 성공으로 가려지지 않게 했다. 사용 중 커넥션은 별도 스레드가 `HikariPoolMXBean.getActiveConnections()`를 계속 읽어 최대값을 남겼다.

| 시나리오 | 수정 전 | 수정 후 |
| --- | --- | --- |
| 위급 한 건의 최대 사용 커넥션 | 2 | 1 |
| 시니어 32명 위급 동시 수신(16개 스레드) | 실패 20건(`CannotCreateTransactionException` 18, `DataAccessResourceFailureException` 2), 성공 12건, 위급 기록 12행, 사건 3행(9건 누락), `afterCompletion threw exception` ERROR 9건 | 실패 0건, 위급 기록 32행, 사건 32행 |
| 32건 처리 시간 | 9,475ms | 793ms, 504ms(2회) |

수정 전 성공 12건 중 9건은 응답과 심박 저장은 성공인데 사건이 열리지 않았다. 리스너 예외를 Spring이 삼켜 호출자는 실패를 알 수 없었다. 단일 인스턴스, 로컬 측정이며 운영 부하나 다른 업무 혼합은 검증하지 않았다.

## 10. 참고

- 함께 확인한 경로: 위치 REST 조회는 `LocationQueryFacade`(트랜잭션 없음)가 읽기 커밋 뒤 기록한다(#1079, LLD-0159). 같은 재현 테스트에서 목록, 이동 경로 각 32건 실패 0건, 기록 32행을 다시 확인했다. WebSocket 구독 인가(`JwtChannelInterceptor`)는 트랜잭션 밖이고 가족 권한 확인 트랜잭션이 끝난 뒤 열람 기록을 부르므로 동시에 쥐는 커넥션은 하나다.
- 검토한 대안
  - 리스너를 `@Async`로: 요청 스레드는 커넥션 하나만 쥐지만, 메모리 큐에 쌓인 위급이 재시작이나 큐 포화 때 사라진다.
  - `hibernate.connection.handling_mode`를 트랜잭션 끝에 반환하도록 전역 변경: 모든 커밋 후 리스너가 한 번에 풀리지만 모든 트랜잭션의 커넥션 수명이 바뀌고, Spring이 커넥션 상태(읽기 전용, 격리 수준)를 되돌리기 전에 풀로 돌아간다. 위급 한 경로를 고치려고 감수할 범위가 아니다.
  - 풀 크기 증설: 요청당 2개 구조가 남는다(LLD-0114와 같은 판단).
- [LLD-0103](LLD-0103-incident-lock-before-snapshot.md): 사건 열기의 회원 행 잠금 순서
- [LLD-0053](LLD-0053-heart-decision-record.md): 판정 기록과 알림 도달 기록
