# LLD-0141: 위치 신선도를 응답에 싣고, 체류와 안전구역 판정 상태를 MySQL에서 알림 Outbox와 함께 커밋한다

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #1045 |
| 관련 ADR | ADR-0044, ADR-0039(30분 묶기), ADR-0041 |
| 선행 LLD | [LLD-0139](LLD-0139-location-order-and-raw-isolation.md)(#1040), [LLD-0140](LLD-0140-location-ttl-auth-connection-supersede.md)(#1044), [LLD-0121](LLD-0121-realtime-location-side-effects-after-tx.md), [LLD-0058](LLD-0058-safe-zone-alert-rollback.md) |
| 작성자 | Claude (Opus 5.5) |
| 작성일 | 2026-11-13 |

## 1. 목적 / 배경

ADR-0044 맥락 그대로다. (1) 보호자에게 보이는 좌표가 언제 잰 것인지 알 수 없고, 이동 경로가 측정 시각 기준 15분이 아니다. (2) 체류와 30분 알림 묶음은 Redis, 알림 Outbox는 MySQL이라 함께 커밋되지 않는다.

## 2. 범위

### In scope

- 변경 모듈: widyu-domain(`SeniorStayState`, `SafeZoneAlertState`, `SafeZoneTransition`, `SeniorLocation` 필드), widyu-api
- 신선도 필드: 마지막 위치 REST, 추적 목록, 실시간 방송, 발신 세션 ACK
- 이동 경로 점의 측정 시각과 15분 거르기
- `senior_stay_state` 테이블과 판정 경로, 조회 경로(외출 여부, 추적 목록 fallback, 마지막 위치 fallback), 삭제 경로
- 예전 Redis 상태 가져오기, 롤백 절차
- 건강 일정 수동 방문 인증이 오래된 좌표를 쓰지 않게 기존 인증 가능 시간을 관측 시각에도 적용
- 실제 MySQL, Redis, STOMP k6 전후 측정

### Out of scope

- "오래됨", "오프라인" 기준 시간(제품 결정). 서버는 측정 시각과 경과만 준다.
- 안전구역 경계 흔들림 완화(연속 N점, 히스테리시스), 30m, 75m, 30분 규칙 변경
- 앱 코드(새 필드 표시는 앱팀 요청 사항)
- 다중 인스턴스 순서 보장(LLD-0139 한계 그대로)
- 운영 DB, Redis 데이터 변경(DDL과 가져오기는 배포 때 운영자가 실행)

## 3. 인터페이스 / API

경로는 그대로이고 응답 필드만 늘어난다(모두 nullable, 기존 필드 의미 유지).

`LocationUpdateResponse`(REST `GET /api/v1/location/realtime/senior/{seniorId}`, 방송 `/topic/location/senior/{id}`, ACK `/user/queue/location/ack`):

| 필드 | 뜻 |
| --- | --- |
| `updatedAt`(기존) | 이 응답을 만든 서버 시각. **위치를 잰 시각이 아니다**(값은 그대로, 문서만 바로잡음) |
| `measuredAtMs` | 앱이 보낸 측정 시각(epoch ms) 그대로. 기기 시계가 앞서면 서버보다 늦을 수 있다. 모르면 null |
| `receivedAtMs` | 서버가 그 위치를 받은 시각. 모르면 null |
| `locationSource` | `LATEST`(Redis 최신 위치), `STAY`(체류 시작 지점, 측정 시각 모름), `REQUEST`(이 요청의 좌표, 실시간 미반영 ACK) |
| `ageSeconds` | 응답 시각 - 측정 시각(초). 측정 시각이 미래면 0, 모르면 null |
| `stayStartTime`, `stayDurationMinutes`(기존) | 체류 상태가 없으면 null(예전에는 지금 시각으로 채웠다) |

`TrackedSeniorResponse`(`GET /api/v1/location/realtime/seniors`): `measuredAtMs`, `receivedAtMs`, `locationSource`, `ageSeconds` 추가. 위치가 없으면 좌표와 네 필드 모두 null.

`LocationPoint`(이동 경로 점): `measuredAtMs` 추가(순서 시각 `min(ts_ms, 수신 시각)`). `timestamp`는 서버 저장 시각 그대로.

호환성: 필드 추가뿐이라 기존 앱은 영향 없다. `stayStartTime`이 체류 없음일 때 null이 되는 것은 동작 변경이다(예전 값 "지금"이 거짓이었다).

## 4. 데이터 모델

`senior_stay_state`(widyu-domain `SeniorStayState`, `scripts/mysql/create_senior_stay_state.sql`):

| 컬럼 | 타입 | 뜻 |
| --- | --- | --- |
| `member_id` | BIGINT PK | 시니어 회원 |
| `latitude`, `longitude` | DOUBLE NOT NULL | 체류 시작 지점 |
| `stay_started_at` | DATETIME(6) NOT NULL | 체류 시작(서버 시각, 기존 규칙) |
| `location_type`, `location_name` | VARCHAR NULL | 안전구역 종류와 이름, 밖이면 null |
| `alert_state` | VARCHAR(16) NULL | `EXITED`, `RETURNED`(30분 묶음 안 마지막 알림) |
| `alert_until` | DATETIME(6) NULL | 묶음 끝. 지나면 `alert_state`는 없는 것과 같다 |
| `created_at`, `updated_at` | DATETIME(6) | BaseTimeEntity |

`SeniorLocation`(Redis) 필드 추가: `deviceMeasuredAtMs`, `receivedAtMs`. 기존 `measuredAtMs`는 순서 시각 그대로(LLD-0140).

## 5. 처리 흐름

### 5.1 위치 한 건(DB 트랜잭션 안, LLD-0121 경계 그대로)

1. 시니어 프로필 조회
2. `stayStateService.find(memberId)`로 잠그지 않고 읽는다. 체류 지점 30m 안이면 그 상태로 응답하고 끝(쓰기, 잠금 없음)
3. 아니면 `lockForChange`: 행이 있으면 `refresh(PESSIMISTIC_WRITE)`(MySQL `SELECT ... FOR UPDATE`, 있는 PK라 그 행만 잠근다)로 다시 읽고 30m 안이면 끝. 행이 없으면 잠그지 않는다(5.3)
4. 안전구역 매칭(75m) → 행이 없으면 INSERT, 있으면 새 체류로 옮김
5. `SafeZoneAlertService`가 `state.applySafeZoneTransition`으로 알림을 정하고 이벤트를 발행 → `SafeZoneNotificationListener`가 같은 트랜잭션에서 알림 센터 행과 Outbox를 넣는다
6. 커밋. 실패하면 상태, 알림 모두 롤백

트랜잭션 밖(LLD-0121): Redis 최신 위치 저장(측정 시각, 수신 시각 포함), 이동 경로(점의 측정 시각 포함), 방송(신선도 포함), 위치 이벤트.

### 5.2 30분 묶음 규칙(ADR-0039, 예전 Redis 표식과 같다)

| 전이 | 조건 | 결과 |
| --- | --- | --- |
| 이탈(안 → 밖) | 묶음이 없거나 끝남 | `EXITED`, 끝=지금+30분, Z01 |
| 이탈 | 묶음 진행 중 | 알림 없음 |
| 복귀(밖 → 안) | `EXITED`이고 묶음 진행 중 | `RETURNED`(끝 유지), Z02 |
| 복귀 | 그 밖 | 알림 없음 |
| 안전구역끼리, 같은 쪽 | | 알림 없음 |

### 5.3 동시성

- 같은 회원: JVM 순서 잠금(LLD-0139)이 한 번에 하나씩 보낸다. DB 행 잠금은 다른 인스턴스나 순서 잠금 밖 경로에 대한 방어다.
- 첫 상태 생성: 행이 없을 때 잠금 조회를 하지 않고 바로 INSERT(`persist`)한다. REPEATABLE READ에서 없는 PK를 `FOR UPDATE`로 읽으면 틈(gap) 잠금이 걸려, 첫 행을 함께 만드는 다른 회원들의 INSERT가 서로 막혀 교착(1213)한다. 첫 구현(650ef315)이 그렇게 해 실제 MySQL에서 회원 20명 첫 위치 동시 수신 때 46건이 교착했고(k6 smoke, `LocationBurstConnectionPoolMySqlTest`), 고친 뒤 0건이다. H2는 틈 잠금이 없어 단위, 통합 테스트로는 드러나지 않았다.
- 같은 회원 첫 상태 동시 생성: PK가 한 행만 허용한다. 진 쪽은 트랜잭션 전체(상태, 알림)가 롤백되고 실시간 기술 오류로 ACK가 없어 재전송된다.
- 잠금 대기: 단일 인스턴스에서는 순서 잠금이 겹침을 막아 행 잠금 대기가 생기지 않는다. MySQL 행 잠금 대기 한도는 `innodb_lock_wait_timeout` 기본값을 따른다(Hibernate MySQL 방언은 잠금 시간 제한 힌트를 지원하지 않는다).
- 커넥션: 판정 트랜잭션 안에 Redis 명령이 없다. 요청 하나가 동시에 쥐는 커넥션은 1개다(LLD-0114).

### 5.4 조회와 삭제

- 외출 여부, 추적 목록과 마지막 위치의 체류 fallback, 체류 시작 시각: `senior_stay_state`에서 읽는다.
- 마지막 위치: Redis 최신 위치가 있으면 `LATEST`(측정 시각은 앱 값, 예전 값이면 순서 시각), 없으면 체류 좌표 `STAY`(측정 시각 null).
- 이동 경로: 점의 `measuredAtMs`가 15분 안인 것만. 예전 점(필드 없음)은 `timestamp`(서버 저장 시각)로 거른다.
- TTL 갱신: 최신 위치 만료만 연장. 활성 회원 조회는 짧은 읽기 트랜잭션.
- 삭제(탈퇴 커밋 뒤, 비활성 정리): `senior_stay_state` 행(REQUIRES_NEW)을 먼저 지우고, 실패해도 Redis 최신 위치, 경로, 예전 체류 키 삭제는 이어서 한다. TTL 갱신 때 Redis 최신 위치가 없는 회원도 활성이 아니면 상태 행을 잠그지 않고 골라(일괄 DELETE는 훑은 행을 잠근다) 회원별로 지운다.

### 5.5 건강 일정 수동 방문 인증

마지막 위치의 관측 시각(순서 시각, 없으면 수신 시각)이 기존 인증 가능 시간(일정 당일 0시 ~ 일정 30분 뒤) 안이어야 한다. 새 기준을 만들지 않고 기존 규칙을 관측 시각에도 적용했다. 자동 도착 처리(`completeArrivedSchedules`)는 실시간에 반영된 15분 안 위치 이벤트로만 돌아 바꾸지 않는다.

## 6. 예외 / 에러 처리

| 조건 | 결과 |
| --- | --- |
| 상태 조회, 잠금, INSERT, Outbox 실패 | 트랜잭션 롤백(상태, 알림 없음), 실시간 기술 오류로 ACK 없음. 원본은 LLD-0139대로 저장 시도 |
| 첫 상태 PK 충돌 | 위와 같음, 재전송에서 회복 |
| 커밋 뒤 Redis 최신 위치 저장 실패 | 상태, 알림 유지. 예외로 ACK 없음. 재전송은 같은 자리라 알림 중복 없음 |
| 방문 인증 위치가 오래됨 | 400 `방문 인증 가능 시간 안에 잰 위치가 없습니다.` |

## 7. 인수조건 (Acceptance Criteria)

- [x] 오래된 최신 위치를 조회하면 `measuredAtMs`, `receivedAtMs`, `ageSeconds`로 나이를 알리고 `updatedAt`과 구분된다. 체류 상태가 없으면 `stayStartTime`은 null이다(`RealtimeLocationServiceTest`)
- [x] 최신 위치가 없으면 체류 좌표로 답하고 `locationSource=STAY`, `measuredAtMs=null`이다
- [x] 필드가 없던 예전 최신 위치는 순서 시각을 측정 시각으로 쓰고, 시각이 전혀 없는 옛 형식은 비운다
- [x] 미래로 잰 위치는 방송에 앱 측정 시각을, 최신 위치에 앱 측정 시각과 수신 시각, 순서 시각을 구분해 남기고 경과는 0이다
- [x] 이동 경로는 측정 시각 기준 15분 점만 돌려주고 예전 점은 저장 시각으로 거른다
- [x] 30분 묶음 규칙(첫 이탈 Z01, 묶음 안 재이탈 없음, 첫 복귀 Z02, 묶음 없으면 복귀 없음, 묶음 끝난 뒤 재이탈 Z01, 같은 쪽 변화 없음)이 그대로다(`SafeZoneAlertServiceTest`)
- [x] 같은 자리 위치는 상태를 잠그지도 쓰지도 않는다(`RealtimeLocationServiceTest`)
- [x] 실제 Spring 트랜잭션에서 나갔다 돌아오면 알림 센터 Z01, Z02가 한 번씩, 상태는 `RETURNED`다. 알림 저장 실패로 롤백되면 상태도 남지 않고 재전송으로 함께 한 번 저장된다. 커밋 뒤 최신 위치 저장 실패 후 재전송해도 알림이 중복되지 않는다. 같은 회원 첫 상태 동시 생성에도 행은 하나다. 상태 행이 없는 회원 20명이 첫 위치를 동시에 보내도 실제 MySQL에서 교착 없이 20행이 생긴다(`LocationBurstConnectionPoolMySqlTest`)(`StayStateOutboxAtomicityTest`, H2)
- [x] 예전 Redis 체류와 진행 중 묶음을 옮기고(남은 TTL로 묶음 끝), 이미 있는 행은 덮지 않고 깨진 값은 건너뛴다(`LegacyStayStateImporterTest`)
- [x] Redis 삭제가 실패해도 상태 행은 지우고, 최신 위치가 만료된 비활성 회원의 상태 행도 TTL 갱신 때 지운다(`RealtimeLocationServiceTest`, `StayStateOutboxAtomicityTest`)
- [x] 인증 가능 시간 전에 잰 위치로는 방문 인증하지 않는다(`HealthScheduleProgressServiceTest`)
- [x] 실제 MySQL, Redis, STOMP k6 기존 회귀 유지와 전후 성능 비교(10절)
- [x] `bash scripts/harness/verify.sh` 통과

## 8. 영향 범위 / 마이그레이션

- **배포 순서:** (1) `scripts/mysql/create_senior_stay_state.sql` 실행(운영 `ddl-auto: validate`) (2) 새 버전 배포. 서버가 시작할 때 `location.stay-state.import-legacy-redis=true`(기본)면 Redis `location:stay:*`와 `safezone:alert:*`를 읽어 비어 있는 행만 채운다. 묶음 끝은 남은 TTL로 정한다. Redis 키는 지우지 않는다. (3) 확인 뒤 다음 배포에서 꺼도 된다.
- **가져오기 없이 시작해도:** 상태가 없으면 "이전 체류 없음"이라 알림이 새로 생기지 않는다(복귀는 `EXITED` 묶음이 있어야, 이탈은 이전 체류가 안이어야 알린다). 잃는 것은 진행 중 30분 묶음(묶음 안 재이탈 Z01 중복 가능, 복귀 Z02 누락 가능)과 체류 시작 시각이다.
- **롤백(이전 버전으로):** 이전 버전은 Redis 체류 키를 읽는데 배포 이후 갱신되지 않아 낡았다. 되돌리기 전에 Redis `location:stay:*`, `safezone:alert:*`를 지우면 "이전 체류 없음"으로 시작해 잘못된 알림이 나가지 않는다(진행 중 묶음은 잃는다). `senior_stay_state` 테이블은 남겨도 이전 버전이 읽지 않는다.
- **롤백 뒤 다시 배포:** 롤백 기간에는 이전 버전이 Redis만 갱신해 `senior_stay_state` 행이 낡는다. 가져오기는 있는 행을 건너뛰므로, 다시 배포하기 전에 운영자가 `senior_stay_state` 행을 지워 Redis 값을 다시 가져오게 한다. 지우지 않으면 회원마다 다음 한 번의 전이에서 잘못된 이탈 알림이 나가거나 복귀 알림이 빠질 수 있다(이후 전이부터 맞춰진다).
- **보존, 삭제:** 행은 회원당 1개이고 탈퇴, 비활성 정리 때 지운다(예전 Redis 24시간 TTL은 활성 회원이면 4분마다 늘어나 사실상 같은 수명이었다). 보관 기간 정리(ADR-0043) 대상 표에는 넣지 않았다(현재 상태 1행).
- **성능:** 위치마다 PK 조회 1건(예전 Redis GET), 전이 때 잠금 조회와 쓰기, 알림. 측정 10절.
- `backend/CLAUDE.md` location 절, `docs/architecture/websocket.md`, ERD를 갱신한다.

## 9. 미결정 사항 (Open Questions)

- [ ] 앱 화면에서 오래된 위치를 언제부터 "오래됨"으로 보일지(제품, 앱팀). 서버는 값만 준다.
- [ ] 건강 일정 방문 인증에 기존 인증 가능 시간보다 짧은 신선도 기준이 필요한지(제품). 지금은 기존 시간 창만 적용했다.

## 10. 참고: 전후 측정

k6로 실제 인증, STOMP, 보호자 구독, MySQL 8.0, Redis 7을 거쳐 #1043 최종(6fcc99f6)과 이 변경(deac77f7)을 버전마다 42회 같은 조건으로 쟀다(로컬 단일 인스턴스, 합성 입력, 조건당 3회).

| 상황 | 지표 | 6fcc99f6 | deac77f7 |
| --- | --- | --- | --- |
| 같은 자리 20명 x 40건(ACK마다) | 원본, ACK | 800, 800 | 800, 800 |
| | UPDATE 문(서버 전체, 측정기 20건 포함) | 20 | 20 |
| | 행 잠금 대기 | 0 | 0 |
| 안심구역 들락날락 20명 x 10건 | 이탈, 복귀 알림 | 20, 20 | 20, 20 |
| | UPDATE 문 | 20 | 200(회원 20명 x 이동 9번) |
| | 행 잠금 대기 | 0 | 0 |
| TTL 갱신 경쟁, Redis 정지, 20명 구독 | 최신값 지연, 성공 ACK, 원본 | 0, 0/8, 20/20 | 0, 0/8, 20/20 |

같은 자리 위치는 상태를 쓰지 않고, 전이는 바뀔 때만 쓴다. ACK p95와 커넥션 사용 평균은 3회 범위가 겹쳐 차이를 주장하지 않는다. 첫 구현(650ef315)은 상태 행이 없는 회원 20명이 첫 위치를 함께 보낼 때 틈 잠금 교착이 났고(5.3), smoke에서 같은 자리 800건 중 176건만 저장됐다. 고친 뒤 0건이다.
