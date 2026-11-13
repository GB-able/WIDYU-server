# LLD-0140: 위치 TTL 갱신 경쟁, 방송 인가 커넥션 점유, 몰린 위치의 최신값 보완

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #1043 |
| 관련 ADR | ADR-0041(WebSocket 끊김 대응의 서버와 앱 책임) |
| 선행 LLD | [LLD-0139](LLD-0139-location-order-and-raw-isolation.md)(#1039, PR #1040), [LLD-0114](LLD-0114-location-burst-single-connection.md), [LLD-0123](LLD-0123-location-ack-storage-confirmation.md) |
| 작성자 | Claude (Opus 5.5) |
| 작성일 | 2026-11-13 |

## 1. 목적 / 배경

PR #1040 뒤 실제 인증, STOMP, 보호자 구독, MySQL, Redis 경로로 한 k6 재현(전후 69회, 2026-11-13)에서 세 문제가 남았다.

1. **TTL 갱신이 최신 위치를 과거로 덮는다.** `refreshLocationTtl()`은 4분마다 위치를 읽어 `save()`로 다시 저장해 TTL을 늘린다. 읽은 뒤 위치 수신이 새 위치를 저장하면, 갱신이 읽어 둔 옛 위치로 덮는다. 위치 수신의 회원별 잠금(LLD-0139)을 거치지 않는 쓰기 경로다. 전후 버전 모두 3회 재현(최종 값 5초 과거).
2. **보호자 구독이 있으면 원본 저장이 커넥션을 못 얻는다.** 회원 20명이 1건씩 동시에 보낼 때 원본 15/20(1회)이 남았다. 스레드 덤프에서 수신 스레드 16개가 모두 커넥션을 기다렸고, 10개는 원본 저장(`LocationFixService.store`, REQUIRES_NEW)에서, 나머지는 방송 인가(`FamilyTopicOutboundInterceptor.preSend` → `FamilyAccessService`)에서 기다렸다. 원인: 방송은 위치 처리 메서드(`updateAndBroadcast(byte[])`, NOT_SUPPORTED) 안에서 보내고, 응답 채널 인터셉터는 보내는 스레드에서 돈다. `FamilyAccessService.verifyActiveFamilyAccess`에는 트랜잭션이 없고, 그 안의 선언 쿼리(`existsByGuardianIdAndSeniorProfileId`)도 기본 트랜잭션이 없다. 트랜잭션 동기화가 켜진 NOT_SUPPORTED 구간에서 이런 조회를 하면 스레드에 묶인 EntityManager가 메서드가 끝날 때까지 커넥션을 쥔다. 그 스레드가 이어서 원본 저장에서 두 번째 커넥션을 기다린다(LLD-0114와 같은 교착). 풀 10개보다 많은 스레드가 동시에 이 상태가 되면 모두 막힌다. 커넥션 1개 풀 테스트로 결정적으로 재현했다(10절).
3. **같은 회원 위치가 몰리면 가장 최근 위치가 실시간에 빠지고 ACK가 보류된다.** 대기 상한(처리 중 1 + 대기 4)을 넘은 위치는 원본만 저장하고 ACK를 보내지 않는다(LLD-0139 계약). 오래된 것부터 몰려오면 가장 최근 위치도 상한에 걸려 실시간에 빠진다. 800건 몰림 ACK 미수신 137~141, 한 회원 640건 339~473, Redis 복구 뒤 8건 일괄 재전송 중 3건 보류였다.

## 2. 범위

### In scope

- 변경 모듈: widyu-api, widyu-domain(`SeniorLocation` 상수만)
- TTL 갱신은 위치 본문을 다시 저장하지 않고 `senior_location:{id}` 키의 만료만 300초로 연장한다.
- `FamilyAccessService.verifyActiveFamilyAccess`를 짧은 읽기 트랜잭션(`@Transactional(readOnly = true)`)으로 감싼다. 인가 규칙은 바꾸지 않는다.
- 회원별 순서 잠금에 "더 최근 위치에 밀림"을 더한다(5.3절).
- 회귀 테스트, 같은 조건 k6 재측정(기준 `f372a25d`와 수정본), 비교 그림.

### Out of scope

- ACK 계약 변경(원본 저장 확인과 실시간 반영 결과를 따로 싣기). 선택안만 9절에 둔다.
- 무제한 대기열, 풀 크기 증설, 보호자 인가 제거.
- 앱 코드(최신 우선, 제한 전송, ACK 기준 재전송은 앱팀 요청 사항, LLD-0139 8절).
- 안전구역 판정 정책, 원본 배치 저장 API, 다중 인스턴스 보장.

## 3. 인터페이스 / API

STOMP 주소와 본문 형식은 바뀌지 않는다. 바뀌는 것은 같은 회원 위치가 겹칠 때의 ACK다.

| 상황 | 이전(LLD-0139) | 이후 |
| --- | --- | --- |
| 같은 회원의 더 늦게 잰 위치가 처리 중이거나 기다리는 중 | 차례를 기다리거나, 상한을 넘으면 원본만 저장하고 ACK 없음 | 기다리지 않고 실시간 처리 생략, 원본 저장 결과로 `STORED`/`DUPLICATE` |
| 지금까지 들어온 것보다 늦게 잰 위치가 상한을 넘어 도착 | 원본만 저장, ACK 없음, 실시간 반영 없음 | 들어와 차례를 기다림(3초 상한). 먼저 잰 대기자는 10ms 안에 밀려 빠짐 |
| 같은 측정 시각(재전송)이 처리 중 | 차례 대기 | 같음. 같은 시각은 밀리지 않는다 |
| 위 경우가 아닌 상한 초과, 3초 대기 초과 | 원본만 저장, ACK 없음 | 같음 |

ACK `STORED`, `DUPLICATE`의 뜻은 그대로다: "원본이 저장돼 있고 이 위치의 실시간 처리가 끝났거나 할 필요가 없다". 밀린 위치는 곧 더 최근 위치로 바뀌므로 "할 필요가 없다"에 넣는다. 더 최근 위치가 실패하면 그 위치는 ACK를 받지 못해 앱이 다시 보내고, 최신 위치는 그 재전송에서 맞춰진다.

## 4. 데이터 모델

변경 없음. `SeniorLocation`에 `KEYSPACE = "senior_location"`, `TTL_SECONDS = 300` 상수를 두고 `@RedisHash`가 이를 쓴다(값 동일, 키 모양 동일).

## 5. 처리 흐름

### 5.1 TTL 갱신 (`RealtimeLocationService.refreshLocationTtl`)

1. `findAll()`로 위치를 읽고 활성 회원을 조회한다(기존).
2. 비활성 회원은 위치를 지운다(기존).
3. 활성 회원은 `redisTemplate.expire("senior_location:{id}", 300초)`, 체류 키도 만료 연장(기존).

본문을 쓰지 않으므로 읽은 뒤 저장된 새 위치를 덮지 않는다. 키가 그새 지워졌거나 만료됐으면 연장은 아무것도 하지 않는다(이전에는 `save()`가 지운 위치를 되살릴 수 있었다). 저장소 색인 집합(`senior_location`)은 TTL이 없고 기존처럼 남는다.

### 5.2 방송 인가 (`FamilyAccessService.verifyActiveFamilyAccess`)

`@Transactional(readOnly = true)`. NOT_SUPPORTED 구간 안에서 불려도 새 읽기 트랜잭션을 열고 끝나면 EntityManager를 닫아 커넥션을 돌려준다. 원본 저장의 REQUIRES_NEW는 그 뒤에 커넥션을 얻는다. 요청 하나가 동시에 쥐는 커넥션은 다시 1개다(LLD-0114 불변식).

### 5.3 몰린 위치의 밀어내기 (`MemberLocationSequencer.run(memberId, measuredAtMs, work, superseded)`)

회원 항목에 "들어온(밀리지 않은) 위치 중 가장 늦게 잰 시각"을 둔다.

1. 들어올 때 그 시각보다 먼저 잰 위치면 인원에 넣지 않고 바로 `superseded`(실시간 생략).
2. 인원이 상한을 넘었어도 그 시각보다 늦게 잰 위치면 들어오고 시각을 갱신한다. 아니면 과부하 예외(기존).
3. 기다리는 위치는 10ms마다 밀렸는지 확인하고, 밀렸으면 빠져 `superseded`. 차례를 얻은 직후에도 한 번 더 확인한다.
4. 잰 시각이 없는 옛 형식은 밀리지도 밀어내지도 않는다. 같은 시각도 밀리지 않는다(재전송 원본이 처리 중일 때 ACK를 먼저 내 앱이 지우면 실시간이 영영 빠지기 때문).
5. 마지막 사용자가 나가면 항목과 시각이 사라진다.

`RealtimeLocationService.applyRealtime`은 `superseded`일 때 받은 좌표만 응답에 담고(`received`) 원본 저장 결과로 ACK한다.

붙잡힌 스레드 수: 더 최근 위치가 상한을 넘어 들어오면 일시적으로 상한을 넘지만, 먼저 잰 대기자가 10ms 안에 빠지므로 상한 근처로 돌아온다. 같은 시각이나 옛 형식만 몰리는 경우의 상한은 기존과 같다.

## 6. 예외 / 에러 처리

새 오류 코드 없음. 밀린 위치는 INFO 로그(`위치 실시간 반영 생략(더 최근 위치 처리 중)`, memberId, seq). 좌표는 남기지 않는다.

## 7. 인수조건 (Acceptance Criteria)

- [ ] TTL 갱신이 위치를 읽은 뒤 새 위치가 저장돼도 최신 위치는 새 위치다. 수정 전 코드에서 실패한다(`RealtimeLocationTtlRaceTest`).
- [ ] TTL 갱신은 위치를 다시 저장하지 않고 활성 회원 키의 만료만 늘린다(`RealtimeLocationServiceTest`).
- [ ] 커넥션 1개 풀에서, NOT_SUPPORTED 구간 안의 보호자 인가 확인 뒤 REQUIRES_NEW가 커넥션을 얻는다. 수정 전 실패한다(`FamilyAccessConnectionHoldTest`).
- [ ] 더 늦게 잰 위치가 처리 중이면 먼저 잰 위치는 바로 밀리고, 상한이 차도 가장 최근 위치는 들어오며, 먼저 잰 대기자는 빠진다. 같은 시각은 밀리지 않는다(`MemberLocationSequencerTest`).
- [ ] 밀린 위치는 방송, 최신 위치 조회 없이 원본 저장 결과로 ACK한다(`RealtimeLocationRawIsolationTest`).
- [ ] k6 실제 경로(10절): TTL 경쟁 3회 모두 최종 최신 위치가 위치 2, 보호자 구독 포함 20명 단건 동시 전송 원본 20/20, 기존 개선(동시 중복 1행 STORED 1 + DUPLICATE 15, 순서 역전 없음, Redis 정지 원본 8/8 보존과 성공 ACK 없음, 권한 위반 REJECTED) 유지.
- [ ] `bash scripts/harness/verify.sh` 통과.

## 8. 영향 범위 / 마이그레이션

- 스키마, Redis 키 변경 없음. 배포 순서 제약 없음. PR #1040 위에 쌓인다.
- `FamilyAccessService.verifyActiveFamilyAccess`는 HTTP 구독 인가 등 다른 호출부에서도 읽기 트랜잭션으로 돈다. 쓰기 트랜잭션 안에서 불리면 참여한다(규칙 동일).
- 몰린 위치의 실시간 반영 수가 줄고 ACK 수가 는다. 중간 위치의 방송과 안전구역 판정이 생략될 수 있다. 이미 LLD-0114 이후 "최신보다 먼저 잰 위치는 생략"이 있었고, 이번에는 "더 최근 위치가 처리 중"까지 넓혔다. 짧은 시간 안에 안전구역을 나갔다 들어온 중간 점만 있는 경우 그 전이를 놓칠 수 있다(몰림 때만, 9절).

## 9. 미결정 사항 (Open Questions)

- [ ] **ACK 계약을 둘로 나눌지(사용자, 앱팀 결정).** 지금 ACK는 원본 저장과 실시간 처리 완료를 한 신호로 묶는다. 대기 초과나 실시간 장애에서는 원본이 저장돼도 ACK가 없어 앱이 다시 보낸다. 선택안: (a) 유지. 앱이 최신 우선, 동시 4건 이하, ACK 없으면 재전송(현재 계약, 서버 변경 없음). (b) ACK에 `realtime`(`APPLIED`, `SKIPPED`, `PENDING`)을 더해 원본 확인은 즉시 주고, 실시간 미완료는 앱이 최신 1건만 다시 보내게 한다. 앱 계약 변경이라 앱팀 합의가 필요하다. 권장은 (a)로 배포하고 앱 반영 뒤 개발 서버 지표로 판단.
- [ ] 몰림 중 생략된 중간 점의 안전구역 전이를 다시 판정할지. 지금은 하지 않는다(안전구역 정책 범위).

## 10. 참고

측정 결과와 재현 명령은 구현 뒤 이 절에 채운다.
