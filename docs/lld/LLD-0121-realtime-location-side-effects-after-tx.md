# LLD-0121: 실시간 위치 트랜잭션에서 Redis 저장과 방송을 커밋 뒤로 분리

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Approved |
| Issue | #955 |
| 관련 ADR | ADR-0009(안전구역 알림 중복 차단), ADR-0039(나감과 돌아옴 30분 묶기), ADR-0040(Redis 명령 3초 제한) |
| 선행 LLD | [LLD-0114](LLD-0114-location-burst-single-connection.md)(요청당 커넥션 1개), [LLD-0058](LLD-0058-safe-zone-alert-rollback.md)(롤백 보정), [LLD-0078](LLD-0078-safe-zone-notice-not-incident.md)(Z01, Z02) |
| 작성자 | Claude (Codex와 2라운드 설계 토론 후 작성) |
| 작성일 | 2026-11-03 |

## 1. 목적 / 배경

위치 한 건의 실시간 반영은 `RealtimeLocationService.applyRealtime()`이 `TransactionTemplate`으로 연 MySQL 트랜잭션 안에서 실행된다. 이 트랜잭션 안에서 DB와 무관한 Redis 쓰기(최신 위치, 이동 경로)와 보호자 WebSocket 방송, 건강 일정 이벤트 발행도 함께 실행된다.

- **커넥션 점유:** 이 구성(Hikari 기본 `autoCommit=true`, `hibernate.connection.provider_disables_autocommit` 미설정)에서는 트랜잭션 시작 때 커넥션을 받는다. 그래서 Redis 명령과 방송을 하는 동안에도 MySQL 커넥션을 쥔다. Redis 명령 제한은 3초(ADR-0040)라서, Redis가 응답하지 않으면 명령마다 최대 3초씩 커넥션을 쥐고 기다린다. 위치가 몰리면 Redis 장애가 Hikari 풀(10개) 고갈로 번진다. 그러면 같은 풀을 쓰는 FCM Outbox 워커, 스케줄러, 인증 필터까지 실패할 수 있다. 2026-10-29 사고와 같은 확산 경로다. 다만 그 사고의 원인은 REQUIRES_NEW 중첩이었고(LLD-0114), 이번 위험은 아직 재현하거나 측정하지 않았다.
- **롤백된 위치의 부수효과:** 최신 위치와 이동 경로 저장, 보호자 방송, `SeniorLocationUpdatedEvent` 발행은 커밋 전에 실행된다. 트랜잭션이 롤백돼도 취소되지 않는다. 이벤트를 받는 `HealthScheduleLocationEventListener`(`@Async`)는 롤백된 위치로 건강 일정 도착을 완료 처리할 수 있다.
- **Redis 오류를 삼키는 체류 조회:** `getStayInfo()`는 `DataAccessException`을 `null`로 바꾼다. 실시간 판정에서 Redis 조회가 실패하면 이전 체류가 "없음"으로 취급된다. 그 결과 알림 없이 새 "밖" 체류가 저장될 수 있다.

이 LLD는 안전구역 판정과 Outbox 저장의 트랜잭션 경계는 지금처럼 유지한다. 그 밖의 Redis 쓰기, 방송, 이벤트만 트랜잭션이 반환된 뒤로 옮긴다.

## 2. 범위

### In scope

- 변경 모듈: widyu-api (`location/realtime/application/RealtimeLocationService`)
- 실시간 트랜잭션 안에서 실행하던 최신 위치 저장, 이동 경로 저장, 보호자 방송, `SeniorLocationUpdatedEvent` 발행을 `TransactionTemplate.execute()` 반환 뒤로 이동
- 트랜잭션 밖 단계의 실패 처리 정책 정의(5.3절)
- 실시간 판정(`calculateStayInfo`)의 체류 조회는 Redis 연결 또는 명령 오류(`DataAccessException`)를 삼키지 않고 전파한다. 깨진 값(`SerializationException`, 좌표나 시작 시각 누락)은 지금처럼 삭제하고 "체류 없음"으로 처리한다. 조회 API(`getTrackedSeniors`, `getOutingStatus`, `getLastLocation`)는 연결 오류도 지금처럼 null로 처리한다(실시간 판정용과 조회용 메서드를 나눈다).
- 실제 MySQL과 Redis를 쓰는 통합 테스트와 단위 테스트

### Out of scope

나중에 하기로 결정한 것이다.

- **안전구역 전이 상태를 DB에 영속화하고 Outbox와 한 트랜잭션에서 확정하는 구조.** 지금도 "이탈 표식(Redis SETNX) 후 커밋 전 프로세스 사망 → 롤백 보정 미실행 → 다음 위치에서 SETNX 실패 → 체류가 '밖'으로 저장되며 Z01 누락" 경로가 있다. 이 LLD는 이 경로를 새로 만들지도 없애지도 않는다. 별도 LLD로 다룬다.
- 같은 기기 위치의 원자적 시각 순서 보장. 예: Redis Lua로 측정 시각이 더 최신일 때만 저장(LLD-0114 2절 한계와 같음).
- 트랜잭션 반환 뒤 부수효과(최신 위치, 이동 경로, 방송, 이벤트)의 내구성과 재시도.
- `saveStayAfterCommit()`이 커밋 후 콜백(커넥션 정리 전)에서 Redis를 쓰는 동안의 커넥션 점유.
- Redis 명령 제한(3초, 앱 전체 공용) 조정이나 위치 전용 연결 설정.
- 실시간 반영이 실패했을 때 v2 원본(`location_fix`) 저장 여부(9절).
- 실시간 경로의 DB 조회 제거(프로필, 안전구역 Redis 캐시). 캐시 무효화 비용이 이 규모에 비해 크다.

## 3. 인터페이스 / API

STOMP 경로와 메시지 형식은 바뀌지 않는다.

```
SEND /app/location/update
ACK  /user/queue/location/ack     (LocationUpdateResponse, 형식 동일)
ERR  /user/queue/errors           (WebSocketExceptionHandler, 형식 동일)
방송 /topic/location/senior/{memberId}
```

바뀌는 것은 실패할 때 어느 응답이 가는지다(6절).

## 4. 데이터 모델

변경 없음. 스키마, Redis 키, TTL 모두 그대로다.

## 5. 처리 흐름

### 5.1 현재 (develop `fe3f814c`)

```
updateAndBroadcast(byte[])                       NOT_SUPPORTED
 ├─ parse, validate
 ├─ applyRealtime()
 │   ├─ verifySelf, isRealtimeCandidate          (Redis 최신 위치 GET, 트랜잭션 밖)
 │   └─ TransactionTemplate.execute {            ← 커넥션 획득
 │        SeniorProfile 조회, Member 지연 로딩     MySQL
 │        최신 위치 저장                          Redis   ← 이동
 │        SeniorLocationUpdatedEvent 발행         이벤트   ← 이동
 │        이동 경로 leftPush, trim, expire        Redis   ← 이동
 │        calculateStayInfo                      Redis GET, 이동 시 MySQL 안전구역 조회,
 │                                               전이 시 Redis 표식 + Outbox INSERT,
 │                                               체류는 afterCommit 저장
 │        응답 DTO 생성
 │        보호자 방송 convertAndSend             WebSocket ← 이동
 │      }                                        ← 커밋, 커넥션 반납
 └─ storeQuietly()                               v2 원본, 별도 트랜잭션
```

### 5.2 변경 후

```
updateAndBroadcast(byte[])                       NOT_SUPPORTED (변경 없음)
 ├─ parse, validate
 ├─ applyRealtime()
 │   ├─ verifySelf, isRealtimeCandidate          (변경 없음)
 │   ├─ TransactionTemplate.execute {            ← 커넥션 획득
 │   │    SeniorProfile 조회, Member 지연 로딩
 │   │    calculateStayInfo                      (변경 없음, 단 체류 GET 오류는 전파)
 │   │    응답 DTO와 반영할 좌표, 측정 시각을 결과 객체로 반환
 │   │  }                                        ← 커밋, 커넥션 반납
 │   └─ publishAfterCommit(결과)                 ← 트랜잭션 밖, 커넥션 없음
 │        a. 최신 위치 저장                       실패 → 예외 전파
 │        b. 이동 경로 leftPush, trim, expire     실패 → WARN, 계속
 │        c. 보호자 방송 convertAndSend           실패 → WARN, 계속
 │        d. SeniorLocationUpdatedEvent 발행      실패 → WARN, 계속
 └─ storeQuietly()                               (변경 없음)
```

- 트랜잭션 안에 남는 Redis 명령: 체류 GET은 항상 실행된다. 직전 체류 지점에서 30m 넘게 움직였을 때는 안전구역 전이 표식, 경계를 넘었을 때는 Outbox, 커밋 후 콜백에서는 체류 저장이 추가된다. 같은 자리에서 오는 평소 위치는 트랜잭션 안 Redis 명령이 체류 GET 1개만 남는다.
- 실행 순서: 최신 위치(a)를 이동 경로(b)와 방송(c)보다 먼저 저장한다. 보호자가 방송을 받고 조회 API를 다시 부를 때 이미 최신 위치가 저장돼 있어야 하기 때문이다.
- `publishAfterCommit`은 트랜잭션 동기화 콜백이 아니라 `execute()` 반환 뒤의 일반 코드다. 커밋 후 콜백은 커넥션 정리 전에 실행되므로 점유 시간을 줄이지 못한다.

### 5.3 트랜잭션 밖 단계의 실패 정책 (토론 결정)

| 단계 | 실패 시 | 이유 |
| --- | --- | --- |
| a. 최신 위치 저장 | 예외 전파 → ACK 대신 `/queue/errors`. 이미 커밋된 Outbox와 체류는 유지 | 저장되지 않았으면 같은 `ts_ms` 재전송이 `isRealtimeCandidate`를 다시 통과해 복구된다. 삼키면 앱이 성공으로 알고 넘어가 최신 위치가 낡은 채 남는다. |
| b. 이동 경로 | WARN 로그, 다음 단계 계속, ACK 정상 | a가 성공했다면 같은 `ts_ms` 재전송은 걸러지므로 오류를 돌려줘도 복구되지 않는다. 다음 위치에서 이어서 쌓인다. |
| c. 보호자 방송 | WARN 로그, 계속, ACK 정상 | 보호자는 조회 API로 최신 위치를 다시 받을 수 있다. |
| d. 건강 일정 이벤트 | WARN 로그, ACK 정상 | 다음 위치에서 다시 발행된다. 도착 완료 처리는 `@Async`라서 ACK가 완료를 보장하지 않는다(지금과 같음). |

ACK는 "실시간 DB 트랜잭션 커밋과 최신 위치 저장이 끝났다"는 뜻이다. 이동 경로, 방송, 건강 일정의 성공을 보증하지 않는다.

## 6. 예외 / 에러 처리

새 에러 코드는 없다. 실패 상황별 응답은 다음과 같다.

| 상황 | 현재 | 변경 후 |
| --- | --- | --- |
| 실시간 트랜잭션 안 예외(DB, 체류 GET, 전이 표식, Outbox) | 오류. 단 최신 위치, 이동 경로, 방송, 이벤트는 이미 나감 | 오류. 최신 위치, 이동 경로, 방송, 이벤트 모두 없음 |
| 체류 GET Redis 오류 | null로 진행 → 이전 체류 없음으로 판정 | 예외 전파 → 롤백 → 오류 |
| 최신 위치 저장 Redis 오류 | 롤백(Outbox 취소) → 오류 | 커밋 유지 → 오류 |
| 이동 경로, 방송, 이벤트 실패 | 롤백 → 오류 | WARN → ACK 정상 |

Redis 장애 중에는 체류 GET 단계에서 오류가 나므로, 실시간 반영 대상인 위치의 ACK는 지금처럼 오류가 된다. 이때 `applyRealtime()` 뒤에 있는 v2 원본 저장도 실행되지 않는다. 이 동작은 지금과 같고, 바꿀지는 9절에서 결정한다.

## 7. 인수조건 (Acceptance Criteria)

- [x] 실시간 DB 트랜잭션 안에서 예외가 나면 최신 위치, 이동 경로, 보호자 방송, `SeniorLocationUpdatedEvent`가 하나도 생기지 않는다. (`RealtimeLocationServiceTest`: 체류 정보 Redis 오류 테스트)
- [x] 안전구역 전이가 있으면 Outbox 행과 체류 저장이 지금처럼 같은 DB 트랜잭션 결과를 따른다(커밋 시 반영, 롤백 시 체류 미저장과 이탈 표식 해제). 30분 묶기는 기존 테스트로 유지된다. (기존 `RealtimeLocationSafeZoneRedisTest` 2건, `RealtimeLocationServiceTest` 롤백·커밋 체류 저장 테스트 통과)
- [x] 최신 위치 저장, 이동 경로 저장, 방송, 이벤트 발행은 `TransactionTemplate.execute()`가 커밋한 뒤 실행된다. (트랜잭션 매니저 `commit` 호출 순서로 검증)
- [x] 같은 자리 위치(직전 체류 30m 이내)를 처리할 때 커밋 전에 실행되는 Redis 명령은 체류 GET뿐이다. 안전구역 목록 조회와 체류 저장은 일어나지 않는다. (같은 테스트에서 순서와 미호출 검증)
- [x] 실시간 판정의 체류 GET에서 Redis 연결 또는 명령 오류가 나면 예외가 전파되고, 커밋, 안전구역 전이 판정, 최신 위치, 방송이 생기지 않는다. 깨진 체류 값은 지금처럼 삭제 후 "체류 없음"으로 진행하고, 조회 API는 연결 오류에서 지금처럼 null을 반환한다. (기존 깨진 값, 시간 초과 조회 테스트 유지)
- [x] 커밋 뒤 최신 위치 저장이 실패하면 예외를 전파하고(앱은 `/queue/errors`), 이동 경로, 방송, 이벤트는 실행하지 않는다. 최신 위치가 갱신되지 않았으므로 같은 `ts_ms` 재전송은 `isRealtimeCandidate`를 다시 통과한다.
- [x] 이동 경로 또는 방송이 실패해도 나머지 단계를 시도하고 응답을 정상 반환한다. (이동 경로 실패, 방송 실패 테스트 각 1건)
- [x] 실제 MySQL과 Redis 통합 테스트(`LocationRedisWriteStallConnectionMySqlTest`): Redis 쓰기를 2초 멈춘(`CLIENT PAUSE 2000 WRITE`) 동안 같은 자리 위치 8건을 동시에 보내면, 활성 커넥션이 변경 전 3회 모두 8개, 변경 후 3회 모두 0개다(10절 측정 결과).
- [x] 기존 몰림 재현(`LocationBurstConnectionPoolMySqlTest`, LLD-0114) 3개 시나리오가 변경 후에도 실패 0건, 원본 전부 저장이다.
- [x] `bash scripts/harness/verify.sh`가 통과한다(규칙 검사, 컴파일, `:backend:widyu-api:test` 1,745건 실패 0, 건너뜀 40).

## 8. 영향 범위 / 마이그레이션

- 스키마와 데이터 변경 없음. 배포 순서 제약 없음.
- **조회 API가 보는 순서가 바뀐다.** 커밋과 최신 위치 저장 사이의 짧은 구간에는 다음 어긋남이 생길 수 있다. 다음 위치에서 해소된다.
  - `getOutingStatus()`(체류만 읽음): 새 상태
  - `getTrackedSeniors()`, `getLastLocation()`: 이전 최신 좌표에 새 체류 종류를 조합
  - `getLocationTrail()`: 이전 경로
- **동시 처리 중인 같은 기기 위치의 순서 경쟁 구간이 늘어난다.** `isRealtimeCandidate()`가 읽는 최신 측정 시각이 이제 트랜잭션이 끝난 뒤에 갱신된다. 그만큼 두 위치가 모두 이전 값을 보고 통과할 수 있는 시간이 길어진다. 앱이 이전 위치의 ACK를 받은 뒤 다음 위치를 보내면(LLD-0114 앱 요청) 기기당 동시 처리는 1건이라 영향이 없다. 원자적 보장은 Out of scope다.
- **새로 생기는 유실 구간:** DB 커밋 직후, 최신 위치를 저장하기 전에 프로세스가 죽으면 그 위치의 최신 위치, 이동 경로, 방송, 이벤트가 빠진다. 체류와 Outbox는 커밋돼 있다. 안전구역 알림(Z01, Z02) 경로에는 새 누락이 없다.
- 탈퇴 시 `deleteLocation()` 뒤에 늦게 끝난 요청이 최신 위치를 다시 쓸 수 있다. 이 경우는 기존 LLD-0059 처리(4분마다 `refreshLocationTtl()`이 활성 회원이 아닌 위치를 지움)로 다음 주기에 사라진다. 지금도 트랜잭션 안에서 같은 교차가 가능했고, 늦어지는 구간이 커밋 직후 몇 ms 늘어날 뿐이라 별도 처리는 하지 않는다.
- **측정 계획:** 변경 전후로 같은 조건(로컬 MySQL, Redis, 풀 10)에서 Redis 멈춤 중 위치 요청의 커넥션 점유와 처리 결과를 비교한다. 결과는 `apiDocs/api/websocket/`에 기록한다. 운영 환경에서는 실험하지 않는다.

## 9. 미결정 사항 (Open Questions)

- [x] 실시간 반영이 실패했을 때(체류 GET 오류 등) v2 원본도 저장하지 않는 현재 동작을 유지한다(사용자 결정, 2026-11-03). 데이터 정책서 1.6.6과의 충돌 여부는 후속으로 확인한다.
- [x] 건강 일정 도착 이벤트가 커밋 뒤 프로세스 사망으로 빠지는 것을 허용한다(사용자 결정, 2026-11-03). 다음 위치에서 다시 발행되며, 마지막 위치였다면 도착 처리가 빠질 수 있다는 한계로 남긴다.
- [x] 이 변경 후에도 남는 트랜잭션 안 Redis 대기(체류 GET, 전이 표식, 커밋 후 콜백의 체류 저장)를 줄이기 위한 위치 경로 전용 Redis 명령 제한은 이 PR에서 정하지 않는다. 운영 Redis 지연 측정 뒤 후속으로 판단한다.
- [x] 후속 LLD(이 PR 범위 밖) 우선순위: (1) 안전구역 전이 상태의 DB 영속화와 Outbox 원자화, (2) 같은 기기 위치의 원자적 순서 보장. (1)이 되면 "구역 경계를 넘을 때만 쓰기 트랜잭션을 열고, 평소 위치는 트랜잭션 없이 짧은 조회만 하는" 구조가 안전해진다. 지금은 이탈 표식(Redis)과 Outbox 보정이 한 트랜잭션의 롤백 콜백에 기대고 있어, 표식을 트랜잭션 밖으로 빼면 1라운드에서 기각한 누락 경로가 된다.

## 10. 참고

### 검토한 대안 (Claude와 Codex 토론, 2026-11-03)

| 대안 | 결론 | 이유 |
| --- | --- | --- |
| 방송과 이벤트를 커밋 후 콜백(`afterCommit`)으로 이동 | 기각 | 커밋 후 콜백은 커넥션 정리 전에 실행되어 점유 시간이 줄지 않는다. LLD-0114에서 원본 저장 위치를 정할 때와 같은 이유다. |
| 이탈 표식(Redis)을 먼저 잡고, Outbox만 짧은 별도 트랜잭션에 저장한 뒤 실패하면 try/catch로 표식 해제 | 기각 | 표식을 잡은 뒤 Outbox 커밋 전에 프로세스가 죽으면 보정이 실행되지 않는다. 다음 위치가 체류를 "밖"으로 저장하며 Z01이 영구 누락된다. 체류 판정과 저장 사이 동시 갱신에서도 누락된다. 같은 사망 경로가 현재 코드에도 있음을 확인했다. 근본 해결은 전이 상태 DB 영속화(후속 LLD)다. |
| `LazyConnectionDataSourceProxy`로 커넥션 획득을 첫 SQL까지 미룸 | 기각 | 첫 SQL(프로필 조회)이 트랜잭션 맨 앞에 있어 핵심 구간을 줄이지 못한다. DataSource와 JPA 설정 검증 부담만 늘어난다. |
| 위치 전용 짧은 Redis 명령 제한 | 보류 | 장애 때 대기를 줄이는 완화책이다. 다만 정상 지연도 실패가 되고 누적 대기는 남는다. 3초는 Outbox 저하 경로(ADR-0040)와 공용이라 운영 지연 측정이 먼저다. |
| 실시간 경로에서 DB 조회 제거(프로필, 안전구역 Redis 캐시) | 기각 | 평시 점유 감소 효과는 가장 크다. 하지만 프로필, 안전구역, 회원 상태, 가족 관계의 캐시 무효화가 필요해 이 규모에 비해 비용이 크다. |
| 트랜잭션 밖 실패를 모두 전파 또는 모두 무시 | 기각 | 최신 위치 저장 실패만 재전송으로 복구할 수 있다(5.3절). |

### 커넥션 획득 시점 근거

- 프로젝트 설정에 Hikari `auto-commit=false`와 `hibernate.connection.provider_disables_autocommit=true`가 없다(`application-datasource.yml`, `application.yml`).
- Hibernate는 이 경우 트랜잭션 시작 때 커넥션을 받아 `setAutoCommit(false)`를 호출한다(Hibernate 6.5 사용자 가이드, Transaction type and connection handling).

### 관련 코드

- `RealtimeLocationService`: `applyRealtime`, `updateAndBroadcast(LocationUpdateRequest, Long)`, `calculateStayInfo`, `getStayInfo`, `saveStayAfterCommit`, `isRealtimeCandidate`
- `SafeZoneAlertService.handleSafeZoneTransition`, `SafeZoneNotificationListener`, `HealthScheduleLocationEventListener`
- `RedisConfig`(트랜잭션 연동 없음), `application-redis.yml`(`timeout: ${REDIS_COMMAND_TIMEOUT:3s}`), `WebSocketExecutorConfig.websocketOutboundExecutor`(4스레드, 대기열 1,000, `CallerRunsPolicy`)

### 측정 결과 (2026-11-03, 로컬)

- 환경: MacBook, 일회용 MySQL 9.0.1(포트 3416), Redis 7.2.4(포트 6392), Hikari 풀 10, 대기 3000ms, Redis 명령 제한 3초(기본값). 운영과 개발 서버에서는 측정하지 않았다.
- 방법: `LocationRedisWriteStallConnectionMySqlTest`. 회원 8명이 같은 자리 위치를 한 번씩 보내 체류를 만든 뒤, Redis에 `CLIENT PAUSE 2000 WRITE`를 걸고 같은 자리 위치 8건을 동시에 보낸다. 멈춘 뒤 300~1,700ms 동안 20ms 간격으로 `HikariPoolMXBean.getActiveConnections()`를 재서 최댓값을 남긴다. 변경 전은 develop `fe3f814c`에 테스트 파일만 넣어 같은 명령으로 실행했다.

| | 1회 | 2회 | 3회 |
| --- | ---: | ---: | ---: |
| 변경 전: 멈춘 동안 최대 활성 커넥션 | 8 | 8 | 8 |
| 변경 후: 멈춘 동안 최대 활성 커넥션 | 0 | 0 | 0 |
| 변경 전: 실패 / 소요 | 0 / 2,149ms | 0 / 2,134ms | 0 / 2,061ms |
| 변경 후: 실패 / 소요 | 0 / 2,025ms | 0 / 2,080ms | 0 / 2,065ms |

- 해석: 변경 전에는 Redis 쓰기를 기다리는 위치 요청 수만큼 커넥션이 잡혀 있었다. 변경 후에는 트랜잭션이 끝난 뒤 쓰기를 기다리므로 커넥션을 쥐지 않는다. 요청 자체는 양쪽 모두 Redis가 풀린 뒤 성공했다(멈춤 2초 < 명령 제한 3초).
- 한계: 같은 자리 위치만 쟀다. 이동하거나 구역을 넘는 위치는 트랜잭션 안에 체류 저장(커밋 후 콜백)과 전이 표식 Redis 명령이 남는다. 읽기까지 멈추는 장애(`CLIENT PAUSE ... ALL`)에서는 트랜잭션 안의 체류 GET 동안 커넥션을 쥔다. 각 조건 3회의 소규모 측정이다.

