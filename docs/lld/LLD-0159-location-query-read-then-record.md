# LLD-0159: 보호자 위치 조회의 열람 기록을 읽기 트랜잭션이 끝난 뒤 남기기

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #1079 |
| 관련 ADR | - (같은 구조의 선례는 [LLD-0152](LLD-0152-read-then-record-single-connection.md), [LLD-0114](LLD-0114-location-burst-single-connection.md), 위치 열람 기록은 [LLD-0056](LLD-0056-location-access-log.md)) |
| 작성자 | Claude |
| 작성일 | 2026-11-15 |

## 1. 목적 / 배경

보호자의 위치 REST 조회 세 개(`GET /api/v1/location/realtime/seniors`, `/senior/{memberId}`, `/senior/{memberId}/trail`)는 `LocationQueryService`의 클래스 전체 읽기 트랜잭션(`readOnly`) 안에서 위치 열람 기록(`LocationAccessLogService.record`, `REQUIRES_NEW`)을 불렀다. 읽기 트랜잭션이 커넥션 하나를 쥔 채 기록이 커넥션을 하나 더 요청하므로, 동시 조회가 풀 크기를 넘으면 모두 두 번째 커넥션을 기다리다 대기 한도(3000ms) 뒤 실패한다. 기록 실패는 예외를 삼키므로 조회가 성공한 요청에서도 열람 기록이 WARN만 남기고 빠진다. 위치 열람 기록은 위치정보법상 남겨야 하는 기록이다.

앨범 상세와 보호자 홈은 LLD-0152(#1056)에서 고쳤고, 이 경로는 그때 다른 작업과의 충돌로 후속으로 남겼다. #1078의 공유 풀 부하 비교에서 풀 4개, 위치 입력과 보호자 조회 혼합 조건의 획득 timeout과 열람 기록 부족이 이 경로에서 나왔다.

## 2. 범위

### In scope
- 변경 모듈: widyu-api
- 기록 호출을 `LocationQueryService`(읽기)에서 빼고 새 `LocationQueryFacade`(트랜잭션 없음)가 읽기가 끝난 뒤 부른다. `RealtimeLocationRestController`는 파사드를 부른다.
- 풀을 줄인 실제 MySQL 재현 테스트(`ReadThenRecordConnectionPoolMySqlTest`)에 위치 조회 경우를 추가한다.

### Out of scope
- 공유 풀 크기(#1078, LLD-0158). 이 수정 뒤 풀 크기를 다시 비교하는 것은 #1078 쪽에서 판단한다.
- `LocationAccessLogService.record`의 `REQUIRES_NEW` 전파 자체. 바깥 트랜잭션 없이 불리면 커넥션은 하나다.
- 다른 `REQUIRES_NEW` 호출부 점검.

## 3. 인터페이스 / API

HTTP 요청과 응답 본문은 바뀌지 않는다.

## 4. 데이터 모델

DB 스키마 변경 없음. `LocationQueryService`의 공개 메서드 시그니처도 그대로이며 `LocationAccessLogService` 의존만 빠진다.

## 5. 처리 흐름

`LocationQueryFacade`(트랜잭션 없음)

1. `LocationQueryService`의 같은 이름 메서드(읽기 트랜잭션)로 가족 권한 확인과 위치 읽기를 마치고 커밋한다. 커넥션을 돌려준다.
2. `LocationAccessLogService.record(guardianId, seniorMemberId, path)`(`REQUIRES_NEW`, 바깥 트랜잭션이 없어 커넥션 1개). `try/catch(Exception)`으로 감싸 WARN(경로, 예외 클래스명)만 남기고 응답을 막지 않는다(LLD-0056 5.1 유지).
   - 목록 조회: 응답에 담긴 시니어마다 `REST_FAMILY`. 기존 기록 대상(가족의 시니어 전원)과 응답 목록은 같다.
   - 마지막 위치: `REST_LAST`. 이동 경로: `REST_TRAIL`.
3. 1의 응답을 반환한다.

## 6. 예외 / 에러 처리

새 에러 코드 없음.

- 권한 오류(403), 없는 시니어(400)는 1단계에서 나며 기록하지 않는다(기존과 같다).
- 기록 실패는 WARN만 남기고 위치는 응답한다(기존과 같다).
- 동작 차이 하나: 기존에는 권한 확인 직후, 위치를 읽기 전에 기록했다. 이제 위치 읽기가 끝난 뒤 기록한다. 마지막 위치 조회가 「최근 위치 정보가 없습니다」(404)로 끝나거나 읽기가 기술 오류로 실패하면 위치를 보여 주지 않았으므로 기록하지 않는다(9절).

## 7. 인수조건 (Acceptance Criteria)

- [x] 위치 조회 세 경로에서 기록은 읽기가 끝난 뒤 호출된다(`LocationQueryFacadeTest`의 읽기 → 기록 순서 검증).
- [x] 풀 4개, 대기 3000ms, 16개 스레드에서 보호자 32명이 같은 시니어의 목록과 이동 경로를 동시에 조회해도 실패 0건이고 경로마다 위치 열람 기록이 32행 남는다(`ReadThenRecordConnectionPoolMySqlTest`).
- [x] 열람 기록이 실패해도 위치는 응답하고, 권한 오류로 막힌 조회는 기록하지 않는다.
- [x] `bash scripts/harness/verify.sh`가 통과한다(API 2,153건, 실패 0, 제외 48).

## 8. 영향 범위 / 마이그레이션

- DDL 없음, 설정 변경 없음, 클라이언트 영향 없음.
- `LocationQueryService`를 직접 부르는 다른 곳은 `GuardianHomeService.getOutingStatus`뿐이며 기록하지 않는 메서드라 영향이 없다.

## 9. 미결정 사항 (Open Questions)

- [ ] 위치를 응답하지 못한 조회(마지막 위치 404)를 열람으로 셀지. 기본값은 응답한 경우만 기록한다. 위치를 보여 주지 않았으므로 열람이 아니라고 보았고, LLD-0152의 보호자 홈도 같은 판단이다.

### 측정 (2026-11-15, 로컬 Docker MySQL 8.0.44, Redis 7)

같은 테스트(`ReadThenRecordConnectionPoolMySqlTest`, 풀 4개, 대기 3000ms, 16개 스레드, 보호자 32명)를 수정 전 코드(기준 `8f789fb4`의 `LocationQueryService`, 컨트롤러)와 수정 후 코드에서 돌렸다. 목록 조회 32건을 모두 끝낸 뒤 이동 경로 조회 32건을 보냈다.

| 시나리오 | 수정 전 | 수정 후 |
| --- | --- | --- |
| 목록 조회 32건 | 실패 24건(`CannotCreateTransactionException`), 성공 8건, `REST_FAMILY` 기록 0행 | 실패 0건, 기록 32행 |
| 이동 경로 조회 32건 | 실패 24건(`CannotCreateTransactionException`), 성공 8건, `REST_TRAIL` 기록 0행 | 실패 0건, 기록 32행 |
| 32건 처리 시간 | 약 6.0초(대기 한도 3초 × 2) | 0.16~0.20초 |

수정 전에는 성공한 8건도 열람 기록이 남지 않았다. 수정 후 결과는 두 번 실행해 같았다. 시니어 1명, 단일 인스턴스, 로컬 측정이며 운영 부하나 다른 업무 혼합은 검증하지 않았다.

## 10. 참고
- [LLD-0152](LLD-0152-read-then-record-single-connection.md): 앨범 상세와 보호자 홈의 같은 수정. 이 LLD의 파사드 구조와 재현 테스트를 따른다.
- [LLD-0114](LLD-0114-location-burst-single-connection.md): 위치 수신의 같은 구조를 고친 선례
- [LLD-0056](LLD-0056-location-access-log.md): 위치 열람 기록과 실패 시 조회 유지 원칙
