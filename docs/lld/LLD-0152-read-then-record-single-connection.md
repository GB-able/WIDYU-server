# LLD-0152: 앨범 상세와 보호자 홈의 열람 기록을 읽기 트랜잭션이 끝난 뒤 남기기

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #1056 |
| 관련 ADR | - (같은 구조의 선례는 [LLD-0114](LLD-0114-location-burst-single-connection.md), 위치 열람 기록은 [LLD-0056](LLD-0056-location-access-log.md)) |
| 작성자 | Claude |
| 작성일 | 2026-11-14 |

## 1. 목적 / 배경

앨범 상세(`GET /api/v1/albums/{albumId}`)와 보호자 홈 카드(`GET /api/v1/home/guardian/cards`)는 읽기 트랜잭션(`readOnly`) 안에서 열람 기록 메서드(`REQUIRES_NEW`)를 불렀다. 읽기 트랜잭션은 시작할 때 커넥션을 잡으므로, 기록이 커넥션을 하나 더 요청해 요청 하나가 커넥션 두 개를 쥔다. 동시 요청이 풀 크기(운영 기본 10개, 대기 3000ms)를 넘으면 모두 첫 커넥션을 쥔 채 두 번째를 기다리다 함께 실패한다. 2026-10-29 위치 몰림 교착(#926, LLD-0114)과 같은 구조다.

풀 4개, 동시 16개 스레드로 32건을 보낸 실제 MySQL 재현에서 수정 전 앨범 상세는 32건이 모두 실패했고, 보호자 홈은 32건 중 24건이 실패했으며 위치 열람 기록은 한 건도 남지 않았다(9절 측정). 위치 열람 기록은 위치정보법상 남겨야 하는 기록이다.

같은 회원이 같은 앨범을 처음 두 번 동시에 열면 두 번째 요청의 상세 조회가 실패했다. 재현에서는 유일 키 위반이 아니라 MySQL 교착(`CannotAcquireLockException`)으로 20번 중 20번 실패했다. 조회 기록 INSERT가 앨범 행에 외래키 공유 잠금을 잡고, 이어서 조회수 UPDATE가 같은 행의 배타 잠금을 요청하기 때문이다.

## 2. 범위

### In scope
- 변경 모듈: widyu-api
- 앨범 상세: 기록 호출을 `AlbumService.getAlbumDetail`(읽기)에서 빼고 `AlbumFacadeImpl.getAlbumDetail`(트랜잭션 없음)이 읽기가 끝난 뒤 부른다.
- 보호자 홈: 기록 호출을 `GuardianHomeService`(클래스 전체 읽기)에서 빼고 새 `GuardianHomeFacade`(트랜잭션 없음)가 읽기가 끝난 뒤 부른다.
- 같은 회원의 앨범 첫 조회 동시 요청이 상세 조회 실패로 이어지지 않게 한다.
- 풀을 줄인 실제 MySQL 재현 테스트(`ReadThenRecordConnectionPoolMySqlTest`).

### Out of scope
- 위치 REST 조회 경로(`RealtimeLocationService.recordAccessQuietly`): 같은 구조지만 #1045~#1048이 이 클래스를 크게 바꾸고 있어 충돌한다. #1046 이후 후속으로 고친다.
- `HeartRateEmergencyNotificationService`의 커밋 후 리스너(`AFTER_COMMIT`) + `REQUIRES_NEW`: 커밋 후 콜백 시점에 바깥 커넥션이 아직 반환 전일 수 있다는 추정이며 측정 전이다. 후속 이슈로 확인한다.
- 커넥션 풀 크기 변경. 요청당 커넥션이 둘이면 동시 요청이 풀을 넘을 때 다시 막히므로 구조로 고친다(LLD-0114와 같은 판단).
- `GuardianHomeService`의 복약 카드 집계(#1059가 작업 중).

## 3. 인터페이스 / API

HTTP 요청과 응답 본문은 바뀌지 않는다.

- 앨범 상세 첫 조회 응답의 `viewCount`, `viewers`는 지금처럼 이번 조회를 포함한다.
- 보호자 홈 카드 응답은 그대로다.

## 4. 데이터 모델

DB 스키마 변경 없음. 서비스 내부 반환 형식만 바뀐다.

- `GuardianHomeService.getHomeCards(Long)`: `GuardianHomeCardsResponse` 대신 `GuardianHomeService.GuardianHomeCards(guardianId, seniorId, response)`를 돌려준다. 열람 기록에 쓸 보호자와, 외출 상태를 읽은 시니어(`memberId`가 없으면 가족의 첫 시니어)를 파사드에 넘기기 위해서다.
- `AlbumViewService.recordView(Album, Member)` → `recordView(Long albumId, Member)`, 새로 기록했으면 `true`. 전파는 `REQUIRES_NEW`에서 기본(`REQUIRED`)으로 바꾼다. 바깥 트랜잭션 없이 불리고, 나중에 누가 트랜잭션 안에서 부르더라도 커넥션을 하나 더 잡지 않게 한다.

## 5. 처리 흐름

### 5.1 앨범 상세 — `AlbumFacadeImpl.getAlbumDetail(albumId)` (트랜잭션 없음)
1. `AlbumService.getAlbumDetail`(읽기 트랜잭션): 앨범, 가족 접근 권한, 해금 여부, 댓글, 최근 조회자를 읽고 커밋한다. 기록은 하지 않는다.
2. 응답이 잠김(`isUnlocked=false`)이거나 본인 앨범(`canEdit=true`)이면 그대로 반환한다(기존 조건과 같다).
3. `AlbumViewService.recordView(albumId, 현재 회원)`(자기 트랜잭션, 커넥션 1개)
   1. 기존 기록이 있으면 `false`.
   2. 조회수를 원자 UPDATE로 먼저 올려 앨범 행을 배타 잠금한다.
   3. 조회 기록을 INSERT하고 `AlbumViewedEvent`를 발행한다(동기 리스너가 같은 트랜잭션에 참여, 기존과 같다). `true`.
4. 3이 `false`면 1의 응답을 반환한다.
5. 3이 `true`면 상세를 한 번 더 읽어(1과 같은 읽기 트랜잭션) 늘어난 조회수와 조회자 목록을 담아 반환한다. 회원당 앨범마다 첫 조회 한 번만 두 번 읽는다.

같은 회원의 첫 조회 두 건이 겹치면, 3-2의 UPDATE 순서 때문에 뒤 요청은 앞 요청 커밋을 기다린 뒤 3-3에서 유일 키(`uk_album_views_album_member`) 위반으로 끝나고 자기 조회수 증가도 롤백된다. 파사드는 `DataIntegrityViolationException`을 잡아 「다른 요청이 기록했다」로 보고 5로 간다. INSERT를 먼저 하면 외래키 공유 잠금과 조회수 배타 잠금이 서로를 기다려 교착된다.

### 5.2 보호자 홈 — `GuardianHomeFacade.getHomeCards(memberId)` (트랜잭션 없음)
1. `GuardianHomeService.getHomeCards`(읽기 트랜잭션): 보호자 확인, 시니어 결정과 가족 권한 확인, 카드 읽기 후 커밋. `GuardianHomeCards`를 돌려준다.
2. `LocationAccessLogService.record(guardianId, seniorId, HOME_OUTING)`(`REQUIRES_NEW`, 바깥 트랜잭션이 없어 커넥션 1개). `try/catch(Exception)`으로 감싸 WARN(경로, 예외 클래스명)만 남기고 응답을 막지 않는다(LLD-0056 5.1 유지).
3. 카드 응답을 반환한다.

`HomeController.getGuardianHomeCards`는 서비스 대신 파사드를 부른다.

## 6. 예외 / 에러 처리

새 에러 코드 없음.

- 앨범 상세: 앨범 없음 404, 가족 아님 403 등은 1단계 읽기에서 기존과 같이 난다. 조회 기록의 유일 키 위반은 잡아서 정상 응답한다. 그 밖의 기록 실패는 기존처럼 상세 조회 실패로 전파한다.
- 보호자 홈: 권한 오류는 1단계에서 나며 기록하지 않는다(기존과 같다). 기록 실패는 WARN만 남기고 카드는 응답한다(기존과 같다).
- 동작 차이 하나: 기존에는 시니어 결정 직후, 카드를 읽기 전에 기록했다. 이제 카드 읽기가 끝난 뒤 기록한다. 카드 읽기가 기술 오류로 실패하면 응답을 받지 못했으므로 기록하지 않는다.

## 7. 인수조건 (Acceptance Criteria)

- [x] 앨범 상세와 보호자 홈 요청이 커넥션을 동시에 두 개 쥐지 않는다. 기록은 읽기 트랜잭션이 커밋된 뒤 호출된다(단위 테스트로 읽기 → 기록 순서 확인: `AlbumFacadeImplTest`, `GuardianHomeFacadeTest`).
- [x] 풀 4개, 대기 3000ms, 16개 스레드에서 보호자 32명이 같은 앨범 상세를 처음 동시에 열어도 실패 0건이고 조회 기록 32행, 조회수 32다(`ReadThenRecordConnectionPoolMySqlTest`).
- [x] 같은 조건에서 보호자 32명이 같은 시니어의 홈 카드를 동시에 열어도 실패 0건이고 위치 열람 기록이 32행 남는다.
- [x] 같은 보호자가 같은 앨범을 처음 두 번 동시에 여는 경우를 20번 반복해도 40건 모두 성공하고 조회 기록 20행, 조회수 합 20이다.
- [x] 앨범 첫 조회 응답의 `viewCount`에 이번 조회가 반영된다(`AlbumViewCountPersistenceTest`).
- [x] 열람 기록이 실패해도 홈 카드는 응답하고, 권한 오류로 막힌 홈 조회는 기록하지 않는다.
- [x] `bash scripts/harness/verify.sh`가 통과한다.

## 8. 영향 범위 / 마이그레이션

- DDL 없음, 설정 변경 없음, 클라이언트 영향 없음.
- 앨범 첫 조회는 상세를 두 번 읽는다(회원, 앨범마다 한 번). 이미 본 앨범을 다시 여는 요청은 기존처럼 기록 확인용 트랜잭션 하나가 더 돈다. 기존에는 이 확인이 읽기 트랜잭션 안에서 두 번째 커넥션으로 돌았고 이제 읽기가 끝난 뒤 같은 커넥션 수 하나로 돈다.
- `GuardianHomeService` 생성자에서 `LocationAccessLogService`가 빠지고 `getHomeCards` 반환 형식이 바뀐다. 이 서비스를 생성자로 직접 만들거나 `getHomeCards`의 반환을 `GuardianHomeCardsResponse`로 받는 테스트는 맞춰야 한다(#1059 브랜치의 `GuardianHomeServiceMedicineTest` 포함).

## 9. 미결정 사항 (Open Questions)

- [ ] 위치 열람 기록 저장이 실패했을 때 조회를 실패로 돌려줄지(이슈 #1056 「정책 확인 필요」). 기본값으로 지금처럼 조회는 성공시키고 WARN을 남긴다. 이번 수정으로 커넥션 대기 때문에 기록이 빠지는 경로는 없앴고, 그 밖의 저장 실패 정책은 확인이 필요하다.

### 측정 (2026-11-14, 로컬 Docker MySQL 8.0.44, Redis 7)

같은 테스트(`ReadThenRecordConnectionPoolMySqlTest`, 풀 4개, 대기 3000ms, 16개 스레드)를 수정 전 코드(`513f5396`)와 수정 후 코드에서 돌렸다.

| 시나리오 | 수정 전 | 수정 후 |
| --- | --- | --- |
| 보호자 32명 앨범 상세 첫 조회 | 실패 32건(`CannotCreateTransactionException`), 조회 기록 0행 | 실패 0건, 조회 기록 32행, 조회수 32 |
| 보호자 32명 홈 카드 | 실패 24건(`CannotCreateTransactionException` 17, `DataAccessResourceFailureException` 7), 위치 열람 기록 0행(누락 32) | 실패 0건, 위치 열람 기록 32행 |
| 같은 보호자 첫 조회 2건 동시 × 20회 | 실패 20건(`CannotAcquireLockException`, MySQL 교착) | 실패 0건, 조회 기록 20행 |

## 10. 참고
- [LLD-0114](LLD-0114-location-burst-single-connection.md): 위치 수신의 같은 구조(실시간 트랜잭션 안의 `REQUIRES_NEW`)를 커밋 뒤 호출로 고친 선례
- [LLD-0056](LLD-0056-location-access-log.md): 위치 열람 기록과 실패 시 조회 유지 원칙
