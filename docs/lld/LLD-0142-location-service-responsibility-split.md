# LLD-0142: 실시간 위치 서비스를 수신 조율, 판정, 반영, 조회, 유지관리로 나눈다

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #1046 |
| 관련 ADR | ADR-0044 |
| 선행 LLD | [LLD-0114](LLD-0114-location-burst-single-connection.md), [LLD-0121](LLD-0121-realtime-location-side-effects-after-tx.md), [LLD-0139](LLD-0139-location-order-and-raw-isolation.md), [LLD-0140](LLD-0140-location-ttl-auth-connection-supersede.md), [LLD-0141](LLD-0141-location-freshness-and-stay-state-db.md) |
| 작성자 | Claude (Opus 5.5) |
| 작성일 | 2026-11-13 |

## 1. 목적 / 배경

`RealtimeLocationService` 한 클래스(812줄, 의존성 15개)가 위치 수신과 ACK 결정, 회원별 순서 제어, 판정 트랜잭션, Redis 반영과 방송, 보호자 조회와 열람 기록, TTL 갱신과 삭제를 모두 맡았다. 판정 트랜잭션은 같은 클래스 안에서 `TransactionTemplate`으로 열었고, 이름이 같은 `updateAndBroadcast` 두 개가 원본 수신 진입점과 실시간 반영이라는 다른 일을 했다. 처리 순서, 트랜잭션 종료 시점, 단계별 실패 정책, 회원 잠금 범위가 호출 구조가 아니라 주석으로만 드러났다.

이 문서는 동작을 바꾸지 않고 책임을 빈 단위로 나눈다. 줄 수나 클래스 수는 목표가 아니며 성능 향상을 주장하지 않는다.

## 2. 범위

### In scope

- widyu-api `com.widyu.location.realtime.application`의 서비스 분리, 호출부(컨트롤러 2, 탈퇴 리스너, 홈 화면) 교체
- 테스트: 단위 테스트를 새 빈에 맞추고, 판정 빈에 실제 트랜잭션 인터셉터를 씌워 커밋 순서 검증을 유지한다. 실제 Spring 프록시 경계 테스트를 추가한다.

### Out of scope

- 외부 동작(응답 필드, ACK 계약, 신선도, 인가, 열람 기록, TTL, 안전구역 규칙) 변경
- `SeniorStayStateService`, `SafeZoneAlertService`, `MemberLocationSequencer`, `LocationFixService`의 역할 변경

## 3. 구조

### 3.1 분리 전후 책임

| 책임 | 분리 전 | 분리 후 |
| --- | --- | --- |
| 파싱, 검증, 본인, 회차, 실시간과 원본 저장의 실패 분리, 회원 순서, ACK 결정 | `RealtimeLocationService.updateAndBroadcast(byte[])` | `LocationIngestService.receive` |
| 파싱된 위치의 실시간 처리(순서 잠금, 최신 비교) | private `applyRealtime` + 공개 `updateAndBroadcast(request)`(순서 잠금 없이 바로 반영) | `LocationIngestService.applyRealtime`(공개, `receive`와 같은 경로) |
| 판정 트랜잭션: 시니어 확인, 체류, 안전구역, 상태 행, 알림 Outbox | `TransactionTemplate` + private `decideInTransaction`, `calculateStayInfo` | `LocationJudgeService.judge`(`@Transactional`) |
| Redis 최신 위치(필수), 이동 경로, 방송, 위치 이벤트(실패 허용) | private `publishAfterTransaction` 외 | `LocationProjectionService.project`, `latestOrderAt` |
| 마지막 위치, 추적 목록, 이동 경로, 외출 여부 조회, 가족 인가, 열람 기록 | 같은 클래스 공개 메서드 4개 | `LocationQueryService`(`readOnly`) |
| TTL 연장, 비활성 정리, 탈퇴 삭제 | 같은 클래스 `@Scheduled`, `deleteLocation` | `LocationMaintenanceService` |
| 상태 행 읽기, 잠금, 생성, 삭제 | `SeniorStayStateService`(그대로) | 그대로. 판정 빈이 조합한다 |
| 이탈, 복귀 30분 묶음 | `SafeZoneAlertService`(그대로) | 그대로 |

공개 `updateAndBroadcast(request)`는 순서 잠금과 15분, 최신 비교를 건너뛰는 테스트용 통로였다. 운영 호출부는 없었다. 이것을 `applyRealtime`으로 바꿔 운영 경로와 같은 순서 제어를 타게 했다.

### 3.2 호출 흐름

```
RealtimeLocationController ── receive(byte[]) ─────────────── LocationIngestService (트랜잭션 없음, NOT_SUPPORTED)
  파싱, 검증, 본인, 회차 ─ 업무 거절이면 REJECTED
  applyRealtime ─ 15분 지났으면 생략
     └ MemberLocationSequencer.run(회원, 순서 시각) ── 회원 잠금(커넥션 없이 기다림)
          ├ projection.latestOrderAt  (Redis, 최신보다 먼저 잰 위치면 생략)
          ├ judge.judge               ── LocationJudgeService @Transactional
          │     시니어 확인, 체류 판정, 상태 행 잠금과 변경, 이벤트 → 리스너가 같은 트랜잭션에 Outbox
          │     ← 반환 시 커밋, 커넥션 반납
          └ projection.project        ── LocationProjectionService (트랜잭션 없음)
                최신 위치 저장(실패하면 던짐) → 경로, 방송, 이벤트(실패하면 로그만)
       ← 회원 잠금 해제
  storeRaw ── LocationFixService.store (REQUIRES_NEW, 실시간 결과와 상관없이 시도)
  ACK 결정: 실시간 실패면 던짐(ACK 없음), 대기 초과면 null, 아니면 STORED 또는 DUPLICATE

RealtimeLocationRestController, GuardianHomeService ── LocationQueryService (readOnly)
WithdrawnMemberLocationListener, @Scheduled ────────── LocationMaintenanceService
```

### 3.3 트랜잭션과 잠금 경계

| 경계 | 소유자 | 근거 |
| --- | --- | --- |
| 회원 순서 잠금 | `LocationIngestService.applyRealtime` | 최신 비교, 판정, 최신 위치 저장, 방송 요청까지 쥔다. 판정이 끝났다고 풀면 뒤에 온 위치가 먼저 저장되고 앞선 위치가 덮는다(LLD-0139) |
| 판정 트랜잭션 | `LocationJudgeService.judge`(별도 빈, 프록시 경유) | 반환 때 커밋된다. 호출자가 트랜잭션 없이 부르므로 `REQUIRED`가 늘 새 트랜잭션을 연다. 자기 호출이 아니라 프록시를 탄다 |
| 원본 저장 트랜잭션 | `LocationFixService.store`(`REQUIRES_NEW`) | 판정 트랜잭션이 끝난 뒤 따로 연다. 동시에 두 커넥션을 쥐지 않는다(LLD-0114) |
| 방송 인가 읽기 | `FamilyAccessService.verifyActiveFamilyAccess`(`readOnly`) | 보내는 스레드에서 짧게 끝난다(LLD-0140) |
| 조율자 범위 | `LocationIngestService` 클래스 `NOT_SUPPORTED` | 바깥 트랜잭션을 잇지 않는다. 이 클래스는 트랜잭션 없는 JPA 조회를 하지 않는다(그 EntityManager가 메서드 끝까지 커넥션을 쥔다, LLD-0139) |
| 활성 회원 조회 | `LocationMaintenanceService`의 `TransactionTemplate`(읽기 전용) | 회원 저장소의 선언 쿼리는 스스로 트랜잭션을 열지 않는다. 짧은 읽기 트랜잭션으로 끝내야 회원별 삭제(`REQUIRES_NEW`)가 두 번째 커넥션을 기다리지 않는다. 그래서 남겼다 |

판정 트랜잭션의 `TransactionTemplate`은 없앴다. 같은 클래스에서 트랜잭션을 열려고 둔 것인데, 판정이 별도 빈이 되면서 `@Transactional`이 같은 일을 프록시로 한다.

### 3.4 단계별 실패 정책(그대로)

| 단계 | 실패 시 | 이유 |
| --- | --- | --- |
| 파싱, 검증, 본인, 회차, 시니어 아님 | `REJECTED`, 원본 없음 | 다시 보내도 같다 |
| 판정 기술 오류(DB) | 상태, 알림 롤백, 원본 저장 시도, ACK 없이 오류 | 재전송에서 다시 판정된다 |
| 최신 위치 저장(필수) | 판정은 커밋된 채, 원본 저장 시도, ACK 없이 오류 | 저장 안 됐으니 재전송이 다시 후보가 된다. 상태가 이미 바뀌어 재전송은 같은 자리라 알림이 중복되지 않는다 |
| 이동 경로, 방송, 위치 이벤트 | 로그만, 다음 단계 계속 | 최신 위치가 이미 저장돼 재전송이 걸러지므로 오류를 돌려줘도 복구되지 않는다 |
| 회원 대기 초과 | 원본만 저장, ACK 없음(`null`) | 재전송 |
| 원본 저장 실패 | ACK 없이 오류 | 재전송 |

## 4. 데이터 모델

변경 없음.

## 5. 인수조건

- [ ] 기존 위치, 홈, WebSocket, 건강 일정 회귀가 그대로 통과한다(단위 테스트는 판정 빈을 실제 `TransactionInterceptor`로 감싸 커밋이 최신 위치 저장보다 먼저인지 계속 본다)
- [ ] 실제 Spring 프록시에서 판정 중에는 트랜잭션이 있고, Redis 최신 위치를 저장할 때는 트랜잭션이 없고 활성 커넥션이 0이며 상태 행이 다른 커넥션에서 보인다(`LocationIngestTransactionBoundaryTest`)
- [ ] 실제 프록시에서 판정이 DB 기술 오류로 실패해도 원본 행이 남고 상태 행, 최신 위치는 남지 않으며 성공 ACK가 없다
- [ ] 시니어가 아닌 회원의 위치는 판정 트랜잭션의 업무 거절이 그대로 전달돼 원본 없이 `REJECTED`다
- [ ] 실제 MySQL 테스트(`LocationBurstConnectionPoolMySqlTest`, `LocationRedisWriteStallConnectionMySqlTest`)가 그대로 통과한다
- [ ] k6 핵심 시나리오(같은 조건, 실제 MySQL, Redis, STOMP)에서 원본, ACK, 최신값, 알림 결과가 #1045 기준(deac77f7)과 같다
- [ ] `bash scripts/harness/verify.sh` 통과

## 6. 파일 지도

```
location/realtime/application/
  LocationIngestService        수신 조율, 순서 잠금, ACK (트랜잭션 없음)
  LocationJudgeService         판정 트랜잭션 (체류, 안전구역, 상태, Outbox)
  LocationProjectionService    Redis 최신 위치, 경로, 방송, 이벤트
  LocationQueryService         보호자, 홈 조회와 열람 기록
  LocationMaintenanceService   TTL, 비활성 정리, 탈퇴 삭제
  MemberLocationSequencer      회원별 순서 잠금 (그대로)
  SafeZoneAlertService         30분 묶음 판정과 이벤트 (그대로)
location/stay/application/
  SeniorStayStateService       상태 행 읽기, 잠금, 생성, 삭제 (그대로)
location/realtime/dto/StayInfo  from(SeniorStayState) 추가, 쓰이지 않던 of(...지금 시각) 삭제
```

## 7. 미결정 사항

없음.
