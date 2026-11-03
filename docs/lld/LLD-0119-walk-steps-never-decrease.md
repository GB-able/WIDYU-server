# LLD-0119: 걸음 수 연동이 저장값보다 작은 값으로 덮어쓰지 않기

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #950 |
| 관련 ADR | - |
| 작성자 | Claude |
| 작성일 | 2026-11-02 |

## 1. 목적 / 배경

걸음 수 연동(`POST /api/v1/goals/walks/sync`, `WalkService.updateSteps`)은 받은 값을 그대로 저장했다(`Walk.updateActualSteps`는 음수만 막았다). 저장값보다 작은 값을 보내도 저장되어 그날 걸음 수가 줄었다 늘었다 할 수 있다.

포인트는 중복 지급되지 않는다. 지급하면 `walk.rewarded`가 저장되고, 포인트 이력 `operation_key`(`WALK_REWARD:{walkId}`)가 unique다. 대신 달성 여부를 현재 걸음 수로 다시 계산하는 곳이 틀어진다.

- 월간 달성 통계(`WalkRepository.countAchievedGoals`, `actualSteps >= goalSteps`): 포인트를 받은 날이 미달성으로 집계될 수 있다.
- 저녁 7시 미달성 알림(`findUnachievedWalksByDate`): 이미 달성한 시니어가 미달성 알림을 받을 수 있다.
- 시니어·보호자 목표 홈의 걸음 수가 줄어 보인다.

앱 재시도나 포그라운드·백그라운드 동시 동기화로 늦게 도착한 옛 값도 같은 결과를 낸다. `Walk`에는 잠금이 없어 겹친 요청은 나중 커밋이 이겼다.

## 2. 범위

### In scope
- 변경 모듈: widyu-domain(`Walk`), widyu-api(`WalkRepository`, `WalkService`)
- 그날 걸음 수는 받은 값과 저장값 중 큰 값만 저장한다.
- 걸음 수 연동은 그날 걸음 기록 행을 비관적 쓰기 잠금으로 읽는다.

### Out of scope
- 그날 첫 동기화가 겹쳐 걸음 기록을 동시에 만드는 경우. `(member_id, walk_date)` unique 제약으로 한쪽이 실패하고(기존 동작), 다음 동기화에서 큰 값이 반영된다.
- 걸음 수를 정상적으로 낮추는 보정(건강 앱의 중복 걸음 정리). 포인트가 걸린 기록이라 줄어드는 값은 반영하지 않는다.
- 이미 줄어든 채 저장된 과거 기록 복구.

## 3. 인터페이스 / API

변경 없음. 작은 값을 보내도 200이며 응답 `achieved`는 저장된(큰) 값 기준이다.

## 4. 데이터 모델

변경 없음.

## 5. 처리 흐름

1. `WalkRepository.findByMemberAndWalkDateForUpdate(member, date)`: `@Lock(PESSIMISTIC_WRITE)`로 그날 행을 `SELECT … FOR UPDATE`한다. 같은 날 동기화가 겹치면 뒤 요청은 앞 요청 커밋까지 기다린 뒤 최신 값을 읽는다.
2. 행이 없으면 기존처럼 기본 목표로 만든다.
3. `Walk.updateActualSteps(steps)`: `steps > actualSteps`일 때만 바꾼다. 음수·null 검사는 그대로다.
4. 달성·포인트 지급 판정은 기존과 같다.

잠금 대신 `UPDATE … SET actual_steps = GREATEST(…)`도 검토했다. 이 방식은 갱신 뒤 엔티티를 다시 읽어야 한다. 읽지 않으면 `markRewarded` flush가 메모리의 옛 걸음 수를 다시 써 버린다. 이미 쓰는 잠금 패턴(`HealthScheduleRepository.findByIdForUpdate`)과 엔티티 규칙 한 줄이 더 단순해 잠금을 택했다. 사용자 한 명의 하루 행이라 잠금 경합은 같은 회원의 동시 동기화에서만 생긴다.

## 6. 예외 / 에러 처리

변경 없음.

## 7. 인수조건 (Acceptance Criteria)

- [x] 저장값보다 작은 걸음 수를 연동하면 저장값이 줄지 않고 `achieved`는 저장값 기준이다.
- [x] 9,000 → 3,000 → 9,000으로 연동해도 목표 달성 포인트는 한 번만 지급된다.
- [x] 실제 MySQL에서 앞 동기화가 커밋 전에 9,000보를 쓰는 동안 3,000보 동기화가 겹치면 9,000보가 남고 포인트는 한 번 지급된다. 수정 전 코드에서는 3,000보가 남는다.
- [x] `./gradlew compileJava`, goal/home 테스트 통과.

## 8. 영향 범위 / 마이그레이션

DB 변경 없음. 앱 변경 없음.

## 9. 미결정 사항 (Open Questions)

- 없음.

## 10. 참고
- `HealthScheduleRepository.findByIdForUpdate`(비관적 잠금 선례)
- `FcmTokenConcurrentRegisterMySqlTest`(잠금으로 겹침을 만드는 MySQL 테스트 방식)
