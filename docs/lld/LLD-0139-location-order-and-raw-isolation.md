# LLD-0139: 같은 회원 위치를 순서대로 처리하고, 실시간 처리 오류와 원본 저장을 분리한다

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #1039 |
| 관련 ADR | ADR-0041(WebSocket 끊김 대응의 서버와 앱 책임, 결정 2, 3, 5) |
| 선행 LLD | [LLD-0048](LLD-0048-location-fix.md)(위치 원본), [LLD-0114](LLD-0114-location-burst-single-connection.md)(재연결 몰림), [LLD-0121](LLD-0121-realtime-location-side-effects-after-tx.md)(실시간 부수효과를 커밋 뒤로), [LLD-0123](LLD-0123-location-ack-storage-confirmation.md)(위치 ACK의 저장 확인) |
| 작성자 | Claude (Opus 5.5) |
| 작성일 | 2026-11-12 |

## 1. 목적 / 배경

위치 한 건은 실시간 처리(최신 위치 판단 → 체류, 안전구역 판정 → 최신 위치 저장 → 보호자 방송)를 거친 뒤 원본(`location_fix`)을 저장한다. 두 가지 문제가 있다.

1. **같은 회원의 위치 순서가 뒤바뀐다.** 최신 시각 비교(`isRealtimeCandidate`)와 최신 위치 저장(`publishAfterTransaction`)이 떨어져 있다. 같은 회원 위치 두 건이 동시에 처리되면 둘 다 옛 최신값과 비교해 통과하고, 먼저 잰 위치가 나중에 저장되면 옛 위치가 최신 위치로 남아 방송된다. 안전구역 판정도 이 뒤바뀐 순서로 실행된다. WebSocket 수신은 실행기(최대 16스레드)에서 병렬로 처리되고, 시니어 앱(develop `33efddb`)은 재연결 때 밀린 위치를 속도 제한 없이 한꺼번에 보낸다. 수정 전 코드에서 이 경합을 단위 테스트로 재현했다(10절).
2. **실시간 처리 오류가 원본 저장을 막는다.** 지금은 실시간 처리가 성공해야 원본을 저장한다(LLD-0121 9절, 2026-11-03 결정). Redis 장애처럼 실시간 처리만 실패해도 MySQL이 정상인데 원본 저장을 시도하지 않는다. 원본은 연구 자료이고 실시간 처리와 보장 수준이 다르다.

이 LLD는 단일 API 인스턴스(`docs/architecture/websocket.md` 7절) 전제로 회원별 실시간 처리를 한 번에 하나씩 실행하고, 실시간 처리의 기술 오류가 나도 원본 저장을 시도하게 한다. LLD-0121 9절의 "실시간 실패 시 원본 미저장 유지" 결정은 2026-11-12 사용자 요청(구현 지시서)으로 바뀐다.

## 2. 범위

### In scope

- 변경 모듈: widyu-api
- 회원별 순서 잠금 `MemberLocationSequencer`: 최신 판단부터 방송 요청까지 같은 회원 안에서 한 번에 하나씩 실행. 회원별 대기 수와 대기 시간 상한, 다른 회원 병렬, DB 트랜잭션을 열기 전에 기다림
- v2 위치 처리 순서 변경: 입력, 본인, 회차 검증 → 실시간 처리(기술 오류는 보관) → 원본 저장 → 결과에 따른 ACK
- 앱이 실어 보낸 `run_id`의 회차 검증을 실시간 처리 앞으로 이동(`LocationFixService.verifyAttribution`)
- 같은 원본 키 동시 수신의 유일 키 충돌을 `DUPLICATE`로 처리
- STOMP 응답 순서 보존(`setPreservePublishOrder(true)`)
- 실제 MySQL 재현 테스트에 동시 중복, 앱 새 전송 방식(최신 위치 먼저) 시나리오, 원본 묶음 INSERT 후보 측정 추가

### Out of scope

- **다중 인스턴스 순서 보장.** JVM 안 잠금이다. 인스턴스를 늘리면 회원별 처리 위치 고정(예: 회원 ID 기준 라우팅)이나 Redis 상태 버전이 필요하다.
- **원본 배치 저장 API.** 10절 측정 결과로 별도 결정한다(9절).
- **최신 위치 Redis CAS(Lua).** 잠금 안에서 비교와 저장을 함께 하므로 단일 인스턴스에서는 필요 없다. CAS만으로는 이미 실행된 안전구역 판정을 되돌릴 수 없어 대안이 되지 못한다.
- **보호자 방송에 측정 시각 싣기.** ADR-0041 결정 6처럼 보호자 앱 동작 확인 뒤로 보류한다. 대신 같은 회원 방송은 측정 시각 순서로 만들어지고 세션별 응답 순서를 보존한다.
- **안전구역 전이 상태 DB 영속화**(LLD-0121 Out of scope 그대로).
- **잠금 밖 최신값 선확인으로 밀린 옛 위치가 차례를 기다리지 않게 하는 최적화.** 앱이 동시 전송을 4건 이하로 제한하면(8절 요청 사항) 필요 없다. 기존 앱의 무제한 몰림에서 실시간 반영이 빠지는 문제가 운영에서 보이면 검토한다.
- 앱의 디스크 보존, ACK 처리, 전송 제한. 앱 코드는 이 작업에서 바꾸지 않는다. 앱팀에 요청할 동작은 8절에 적는다.

## 3. 인터페이스 / API

STOMP 경로와 본문 형식은 바뀌지 않는다. `SEND /app/location/update` → 발신 세션 `/user/queue/location/ack`(`LocationAckResponse`), 오류 `/user/queue/errors`.

바뀌는 것은 v2 위치에서 어떤 상황에 ACK가 나가는지다.

| 상황 | 원본 | 실시간 | ACK |
| --- | --- | --- | --- |
| 원본 새로 저장, 실시간 처리 완료 또는 대상 아님 | 저장 | 완료/생략 | `STORED` |
| 원본 이미 있음(재전송, 동시 중복의 유일 키 충돌 포함), 실시간 처리 완료 또는 대상 아님 | 유지 | 완료/생략 | `DUPLICATE` |
| 입력, 좌표 검증, 본인 아님, 남의 회차, 시니어 아님 | 저장 안 함 | 실행 안 함 | `REJECTED` |
| 실시간 처리 기술 오류(Redis, DB 장애 등) | **저장 시도** | 실패 | 없음(`/user/queue/errors`) |
| 같은 회원 위치가 몰려 실시간 차례를 못 얻음 | **저장 시도** | 실행 안 함 | 없음(아무것도 보내지 않음, WARN 로그) |
| 원본 저장 실패(업무 예외 아님) | 실패 | 무관 | 없음(`/user/queue/errors`) |

**ACK 의미(LLD-0123 갱신):** `STORED`, `DUPLICATE`는 "원본이 저장돼 있고, 이 위치의 실시간 처리가 끝났거나 할 필요가 없다. 앱은 지워도 된다"이다. ACK가 없다는 것은 "원본이 없다"가 아니라 "다시 보내라"다. 원본 저장, 실시간 반영, 보호자 방송 전달은 서로 다른 보장이다. 방송, 이동 경로, 건강 일정 이벤트 성공은 지금처럼 보장하지 않는다(LLD-0121 5.3).

## 4. 데이터 모델

테이블, Redis 키 변경 없음.

- `com.widyu.location.realtime.application.MemberLocationSequencer`(widyu-api, `@Component`): 회원 ID별 `{공정 ReentrantLock, 사용자 수}`를 `ConcurrentHashMap`에 둔다. 마지막 사용자가 나가면 항목을 지운다.
- `com.widyu.location.realtime.application.LocationSequenceBusyException`(widyu-api): `RuntimeException`. 업무 예외가 아니다(업무 예외면 `REJECTED`가 되어 앱이 지운다).
- 설정(`application.yml`): `location.realtime.sequencer.max-waiting`(기본 4, `LOCATION_SEQUENCER_MAX_WAITING`), `location.realtime.sequencer.wait-timeout-ms`(기본 3000, `LOCATION_SEQUENCER_WAIT_TIMEOUT_MS`)
- 두 설정은 0 이상이어야 한다. 음수는 시작 시 `IllegalArgumentException`으로 거절한다. 0은 대기하지 않는 설정이며, 비어 있는 잠금의 즉시 처리는 허용한다(PR #1040 리뷰 반영).
- `LocationFixService.verifyAttribution(memberId, request)`, `LocationFixService.exists(request)`(`@Transactional(readOnly = true)`) 추가. `LocationFixService.store`가 `SeniorProfileRepository.existsByMemberId`로 시니어인지 확인한다(원본 저장당 SELECT 1건 증가)

## 5. 처리 흐름

`RealtimeLocationService.updateAndBroadcast(byte[], memberId)`(트랜잭션 없음, LLD-0114):

1. 본문 파싱. 실패하면 `LOCATION_FIX_INVALID` 예외(식별자를 몰라 ACK 없음, 이전과 같음)
2. 구형 위치(`isRawFix()=false`)는 검증 후 실시간 처리하고 `legacy` ACK(순서 잠금은 같이 탄다)
3. v2 위치 검증. 하나라도 업무 예외면 부수효과 없이 `REJECTED`
   1. 요청 검증(`validate`), 원본 검증(`LocationFixService.validate`)
   2. 본인 확인(`verifySelf`)
   3. 회차 확인(`verifyAttribution`): `run_id`가 있을 때만 `requireAttributableRun`(자기 읽기 트랜잭션). 없으면 조회하지 않는다(저장 때 서버가 찾고 거절할 일이 없다)
   4. 시니어 확인은 여기서 하지 않는다. 이 메서드는 트랜잭션이 없어(`NOT_SUPPORTED`) 저장소를 바로 부르면 스레드에 묶인 EntityManager가 요청 끝까지 커넥션을 쥐고, 뒤의 실시간 트랜잭션이 커넥션을 하나 더 기다리는 LLD-0114 교착이 된다(10절 실패 실행). 실시간 대상이면 실시간 트랜잭션 첫 조회가, 아니면 원본 저장 트랜잭션 첫 조회(`existsByMemberId`)가 확인한다
4. 실시간 처리 `applyRealtime`
   1. 15분 넘게 지난 위치면 잠금 없이 생략(최신 위치를 읽지 않는다)
   2. `sequencer.run(memberId, ...)` 안에서: 15분 재확인(기다리는 동안 넘을 수 있다), 최신 위치 조회와 비교(`isNewerThanLatest`) → 대상이면 `updateAndBroadcast(request)`(DB 트랜잭션: 프로필, 체류, 안전구역 판정 → 커밋 → 최신 위치 저장, 이동 경로, 방송, 이벤트, LLD-0121 그대로)
   3. 업무 예외(시니어 아님)는 부수효과 전에 나므로 원본 없이 `REJECTED`
   4. 그 밖의 `RuntimeException`(대기 초과 포함)은 보관하고 다음 단계로
5. 원본 저장 `storeRaw`(REQUIRES_NEW). 트랜잭션 안에서 시니어인지 먼저 확인한다(아니면 `BAD_REQUEST` → `REJECTED`). `DataIntegrityViolationException`이면 `exists`(자기 읽기 트랜잭션)로 다시 확인해 있으면 `DUPLICATE`, 없으면 그대로 던진다. 업무 예외면 `REJECTED`
6. 보관한 실시간 오류가 대기 초과면 WARN 후 `null`(컨트롤러가 아무것도 보내지 않는다), 그 밖이면 WARN 후 그 예외를 던진다(`WebSocketExceptionHandler` → `/user/queue/errors`)
7. 둘 다 성공이면 `STORED`/`DUPLICATE`

### 5.1 순서 잠금

- 잠금은 DB 트랜잭션(`TransactionTemplate`)을 열기 **전**에 잡는다. 기다리는 동안 커넥션을 쥐지 않는다.
- 잠금 범위: 최신 위치 조회 → 체류, 안전구역 판정(트랜잭션) → 최신 위치 저장 → 이동 경로 → 방송 요청 → 위치 이벤트 발행. 방송은 `convertAndSend`가 응답 채널에 넣는 시점까지만 순서가 정해진다(5.2).
- 처리 중 1건 + 대기 `max-waiting`건을 넘으면 기다리지 않고 바로 대기 초과. 차례가 `wait-timeout-ms` 안에 오지 않아도 대기 초과. 대기 초과는 원본을 저장하고 ACK를 보내지 않는다.
- 값의 근거: 수신 실행기는 최대 16스레드(`MetricsConfig`). 앱이 동시 전송을 4건으로 제한하면(8절 요청 사항, 앱 미반영) 처리 중 1 + 대기 3이라 대기 초과가 나지 않고, 한 회원이 수신 스레드를 5개보다 많이 붙잡지 못한다. 대기 3초는 Redis 명령 제한(3초, ADR-0040)과 커넥션 대기 한도(3초)에 맞췄다.
- 공정 잠금이라 먼저 기다린 위치가 먼저 처리된다. 그래도 반영 여부는 측정 시각 비교가 정한다.

### 5.2 보호자 방송 전달 순서

같은 회원 방송은 잠금 안에서 측정 시각 순서로 응답 채널에 들어간다. 응답 실행기(4스레드)는 같은 세션의 메시지도 순서 없이 보내므로 `MessageBrokerRegistry.setPreservePublishOrder(true)`로 세션마다 보낸 순서를 지킨다. ACK와 오류 메시지도 세션별 순서를 따른다. 보호자 앱이 재연결 뒤 REST 재조회와 방송이 엇갈리는 경우는 ADR-0041 결정 6 그대로다.

## 6. 예외 / 에러 처리

새 오류 코드 없음.

| 조건 | 결과 | 로그 |
| --- | --- | --- |
| v2 업무 예외(검증, 본인, 회차, 시니어 아님) | `REJECTED`, 원본, 방송 없음 | WARN `memberId`, `seq`, `code` |
| 실시간 기술 오류 | 원본 저장 시도 후 예외 → `/user/queue/errors` | WARN(원본 저장, ACK 보류) + `WS_EXCEPTION` ERROR |
| 실시간 대기 초과 | 원본 저장 시도 후 응답 없음 | WARN(원본 저장, 대기 초과) |
| 원본 유일 키 충돌 + 행 있음 | `DUPLICATE` | INFO |
| 원본 유일 키 충돌 + 행 없음 / 원본 저장 실패 | 예외 → `/user/queue/errors` | `WS_EXCEPTION` ERROR |

좌표는 어떤 로그에도 남기지 않는다(LLD-0029). 이 경로에 일시 실패를 `BusinessException`으로 던지는 코드를 추가하면 `REJECTED`로 지워지므로 금지(LLD-0123 6절 그대로).

## 7. 인수조건 (Acceptance Criteria)

- [x] 같은 회원의 새 위치와 옛 위치가 동시에 처리돼 둘 다 옛 최신값을 읽을 수 있는 상황에서, 최신 위치와 마지막 방송은 나중에 잰 위치다. 수정 전 코드에서는 이 테스트가 실패한다(`RealtimeLocationOrderTest`, 10절)
- [x] 한 회원의 실시간 처리가 멈춰 있어도 다른 회원의 위치는 기다리지 않고 처리, 방송된다(`RealtimeLocationOrderTest`)
- [x] 같은 회원 대기 수가 상한을 넘으면 바로, 대기 시간을 넘기면 실행 없이 대기 초과가 나고, 끝나면 회원 항목이 남지 않는다(`MemberLocationSequencerTest`)
- [x] 음수 대기 설정은 시작 시 거절하고, 대기 수와 시간이 0이어도 비어 있는 잠금은 처리한다. 대기 상한 테스트는 대기 스레드의 실제 진입을 확인한 뒤 검사한다.
- [x] 최신 위치 조회 또는 체류 조회에서 Redis 장애가 나도 원본 저장을 시도하고, ACK 없이 예외를 전달하며 방송하지 않는다(`RealtimeLocationRawIsolationTest`)
- [x] 원본을 저장한 뒤 실시간 처리만 실패한 위치를 다시 보내면 실시간 처리가 다시 평가돼 최신 위치 저장과 방송이 되고 `DUPLICATE` ACK가 나간다
- [x] 원본 저장이 실패하면 성공 ACK 없이 예외를 전달한다
- [x] 남의 회차, 다른 회원, 시니어 아님은 원본 저장 없이 `REJECTED`이고, 남의 회차와 다른 회원은 최신 위치 조회와 방송도 없다. 시니어 아님은 15분 넘은 위치(실시간 경로를 건너뜀)에서도 원본 저장 트랜잭션이 거절한다(`LocationFixServiceTest`, `RealtimeLocationRawIsolationTest`)
- [x] 실시간 처리 전 검증이 트랜잭션 없는 구간에서 커넥션을 쥐지 않는다. 실제 MySQL 몰림 재현이 실패 0건이다(10절, 시니어 확인을 앞에 뒀던 실행은 실패)
- [x] 같은 원본의 유일 키 충돌은 행이 있으면 `DUPLICATE`, 없으면 예외다. 실제 MySQL에서 같은 원본을 16스레드가 동시에 보내면 행 1개, `STORED` 1, `DUPLICATE` 15, 예외 0이다(수정 전 같은 조건에서 예외 9건, 10절)
- [x] 실시간 차례를 얻지 못한 위치는 원본만 저장하고 ACK를 보내지 않는다
- [x] 기존 몰림 재현(`LocationBurstConnectionPoolMySqlTest`) 시나리오와 새 "최신 위치 먼저" 시나리오가 실패 0건, 원본 전부 저장이다(10절)
- [x] `bash scripts/harness/verify.sh`가 통과한다(결과는 PR 본문과 작업 기록에 남긴다)

## 8. 영향 범위 / 마이그레이션

- DB, Redis 스키마 변경 없음. 배포 순서 제약 없음.
- **앱팀 요청 사항(앱 미구현, 이 작업 범위 밖):** ADR-0041 결정 2, 3, 5를 따른다. 위치를 보내기 전에 디스크에 보존하고, `/user/queue/location/ack`의 `{deviceId, sessionId, seq, result}`로 큐 항목을 찾는다. `STORED`, `DUPLICATE`, `REJECTED`만 지운다. ACK가 없거나 모르는 결과면 시간 초과 뒤 같은 원문으로 다시 보낸다. 재연결 뒤 가장 최근 위치를 먼저 보내고 그 ACK 뒤에 나머지를 동시 4건 이하, 속도 제한을 두고 보낸다. 동시 전송 상한은 서버 `max-waiting`(4)보다 작아야 대기 초과가 나지 않는다. 앱팀이 다른 값을 정하면 `LOCATION_SEQUENCER_MAX_WAITING`을 맞춘다.
- **현재 앱(ACK 미구독, 무제한 몰림)에 미치는 영향:** 원본은 실시간 대기 초과, 기술 오류에도 저장되므로 지금보다 원본 보존이 늘어난다. 한 회원 위치 84건을 16스레드가 한꺼번에 처리하면 79건이 실시간 대기 초과로 원본만 저장됐다(10절). 기존 앱은 ACK를 무시하므로 재전송되지 않고, 그 위치들은 실시간에 반영되지 않는다. 몰림 중 가장 최근 위치까지 대기 초과면 보호자 화면은 다음 새 위치(이동 중 약 5초 뒤)까지 이전 위치에 머문다.
- **기존 앱이 오래된 것부터 몰아 보낼 때의 실시간 작업량:** 수정 전에는 경합으로 일부 위치가 "더 최신 위치가 이미 반영됨"으로 생략됐다. 순서를 지키면 오래된 것부터 차례로 들어온 위치가 각각 그 시점의 최신이 되어 반영될 수 있다. 10절 측정에서 회원 20명 × 40건 시나리오의 실시간 반영 수가 실행마다 크게 달라(커밋 960~2,400) 이 차이를 수치로 결론 내지 않았다. 앱이 최신 위치부터 보내면 밀린 위치는 모두 생략돼 실시간 작업이 회원당 1건이다(커밋 840 = 원본 800 + 실시간 20 + 건강 일정 리스너 20, 3회 동일).
- **LLD-0121 9절 결정 변경:** "실시간 반영이 실패하면 v2 원본도 저장하지 않는다"(2026-11-03)를 이 LLD가 대체한다. 원본 저장은 실시간 처리 뒤에 있으므로 원본은 실시간 처리 시간(대기 최대 3초 포함)만큼 늦게 저장된다.
- **로그:** 실시간 기술 오류 때 원본을 저장했다는 WARN이 추가된다. 실시간 대기 초과는 ERROR가 아니라 WARN이다.
- `backend/CLAUDE.md` location 절, `docs/architecture/websocket.md` 6절을 갱신한다.

## 9. 미결정 사항 (Open Questions)

- [ ] **원본 묶음 저장 API 도입 여부(사용자 결정).** 10절에서 원본 저장 단계만 보면 800건 건별(문장 4,801, 순차 약 1.6초)과 100건 묶음(문장 41, 약 50~68ms)의 차이가 크다. 하지만 지금 재현의 전체 처리는 변경 전후 모두 1초 안팎이고 실패가 없으며, 운영 RDS 비용, CPU를 재지 않았다. 묶음 API는 크기, 부분 실패, 회차 귀속, ACK 계약이 새로 필요하다(ADR-0041 대안 3). 선택지: (a) 보류하고 앱팀의 ACK, 디스크 보존 반영과 배포 뒤 개발 서버 몰림 지표로 다시 판단, (b) 재연결 밀린 원본 전용 묶음 업로드 API LLD 작성. 권장은 (a).
- 그 밖에는 없음. 순서 잠금 값(4건, 3초)은 설정으로 바꿀 수 있다.

## 10. 참고

### 재현과 측정 (2026-11-12, 로컬)

- 환경: MacBook(arm64), 일회용 MySQL 9.0.1(포트 3417), Redis 7.2(포트 6393), Hikari 풀 10, 대기 3000ms, 테스트 스레드 16. 운영, 개발 서버에서는 실행하지 않았다.
- 변경 전: develop `c5cff9d2`를 별도 worktree에 두고 새 테스트 파일만 복사. 변경 후: `feature/1039` 미커밋 작업본.
- 명령: `LOCATION_MYSQL_URL=jdbc:mysql://127.0.0.1:3417/location_burst_<before|after> LOCATION_REDIS_PORT=6393 DB_USERNAME=root DB_PASSWORD= ./gradlew :backend:widyu-api:test --tests '*LocationBurstConnectionPoolMySqlTest' --rerun -i`
- 문장 수는 측정 구간 앞뒤 `SHOW GLOBAL STATUS`(`Com_select`, `Com_insert`, `Com_commit`, `Questions`)의 차이다. 서버 전체 값이라 스케줄러 쿼리와 비동기 리스너가 섞인다.

| 항목 | 변경 전 (3~4회) | 변경 후 (3~4회) |
| --- | --- | --- |
| 순서 역전 재현(`RealtimeLocationOrderTest`) | 실패: 최신 위치 = 5초 먼저 잰 위치 | 통과 |
| 같은 원본 16스레드 동시 | 행 1, `STORED` 1, `DUPLICATE` 6, 무결성 예외 9 (4회 같음) | 행 1, `STORED` 1, `DUPLICATE` 15, 예외 0 (4회 같음) |
| 한 회원 84건(최근 7분) | 실패 0, 원본 84, ACK `STORED` 84, 276~316ms | 실패 0, 원본 84, ACK `STORED` 5, 없음 79, 197~281ms |
| 20명 × 40건 최근분 | 실패 0, 원본 800, 336~509ms, 커밋 960~1,240 | 실패 0, 원본 800, 407~853ms, 커밋 960~2,400 |
| 20명 × 40건 1시간 밀림 | 실패 0, 원본 800, 212~291ms | 실패 0, 원본 800, 246~477ms |
| 20명 × 40건 최신 위치 먼저 | 실패 0, 원본 800, 370~395ms, 커밋 840 | 실패 0, 원본 800, 452~472ms, 커밋 840 |
| 원본만 800건 건별 저장(순차) | 1,540~1,666ms, 문장 4,801(조회 1,600, INSERT 800, 커밋 800) | 1,564~1,697ms, 같음 |
| 원본만 100건 묶음 INSERT 8회 | 49~68ms, 문장 41(조회 8, INSERT 8, 커밋 8) | 49~57ms, 같음 |

- 시나리오 실행 순서는 JUnit이 정하고 첫 시나리오가 예열을 겪는다. 시간은 범위로만 적고 개선율을 계산하지 않는다.
- "최신 위치 먼저"에서 변경 후가 약 80ms 느린 것은 밀린 39건이 회원 잠금 안에서 최신 위치를 읽으려고 차례를 기다리기 때문으로 보인다(대기 초과 0건). 원인은 계측하지 않았다.
- 묶음 후보는 회차 조회를 생략하고 해시를 고정값으로 넣은 측정용 SQL이다. 실제 API의 비용은 이보다 크다.
- 위 표의 변경 후 값은 시니어 확인을 넣기 전 작업본이다. 로컬 Codex 검수(BLOCKER 2건: 15분 넘은 위치의 시니어 미확인, 잠금 대기 중 15분 초과) 반영 뒤 재측정은 아래와 같다.

| 실행 | 시니어 확인 위치 | 결과 |
| --- | --- | --- |
| r4 (실패) | `updateAndBroadcast(byte[])` 앞, `seniorProfileRepository.existsByMemberId` 직접 호출 | 한 회원 84건: 실패 54(`CannotCreateTransactionException` 27, `DataAccessResourceFailureException` 27), 원본 33, 18,174ms. 20명 × 40건: 실패 779, 원본 174, 229,127ms. Hikari `active=10, waiting=10`. 선언 쿼리 메서드는 트랜잭션이 없어 `NOT_SUPPORTED` 구간의 EntityManager가 커넥션을 요청 끝까지 쥐었고, 그 상태로 실시간 트랜잭션이 두 번째 커넥션을 기다렸다(LLD-0114와 같은 교착) |
| r5, r6 | 원본 저장 트랜잭션 안 | 모든 시나리오 실패 0, 원본 800(84). 20명 × 40건 398~821ms, 최신 위치 먼저 417~454ms, 1시간 밀림 277~458ms. 같은 원본 16스레드: STORED 1, DUPLICATE 15. 원본만 800건 건별 Questions 5,601(SELECT 2,400, 시니어 확인 800건 증가), 1,784~1,789ms |

- 이 교착은 실제 MySQL 태그 테스트(`location-mysql`)에서만 드러난다. CI와 `verify.sh`는 환경변수가 없어 이 테스트를 건너뛴다. 위치 수신 경로를 바꾸면 이 테스트를 직접 돌린다.

### 관련 코드

`RealtimeLocationService.updateAndBroadcast(byte[], Long)`, `applyRealtime`, `isTooOld`, `isNewerThanLatest`, `storeRaw`, `MemberLocationSequencer`, `LocationFixService.verifyAttribution`, `LocationFixService.exists`, `WebSocketConfig.configureMessageBroker`
