# LLD-0024: 목표 홈 주간 통계 조회 파이프라인 최적화

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Approved |
| Issue | #489, #491, #492 |
| 관련 ADR | ADR-0019, ADR-0020 |
| 작성자 | dongkyunKim |
| 작성일 | 2026-08-20 |

## 1. 목적 / 배경

목표 홈 주간 통계의 날짜별·복약 스케줄별 반복 SQL과 이번 주 통계 중복 계산을 제거한다. 동일 API 응답을 유지하면서 기간 단위 3회 조회와 메모리 집계로 요청당 DB 접근을 고정하고, Before/After 실측으로 개선 효과를 검증한다.

## 2. 범위

### In scope

- 변경 모듈: `widyu-api`
- `GET /api/v1/goals/home/senior/weekly-status` 기간 벌크 조회 전환
- `GET /api/v1/goals/home/guardian/stats` 기간 벌크 조회 전환
- 기존 Repository 기간 조회 메서드 재사용
- 날짜별 복약 스케줄·복약 인증·걸음 기록 메모리 집계
- 이번 주 일별 달성률 계산 결과 재사용
- 기존 응답을 보존하는 단위 테스트
- 동일 조건의 After SQL·행 수·Buffer Pool·p50/p95 측정
- 기간 복약 인증 조회의 조인 제거와 `(member_id, verified_at)` 복합 인덱스 추가 (#492, ADR-0019 후속 조치)
- 보호자 API 가족 접근 검증과 누락 방지 정적 테스트 (#491, ADR-0020)

### Out of scope

- Redis/Spring Cache 도입
- DB 쿼리 병렬 실행
- 신규 테이블·컬럼 추가 (인덱스는 재측정 근거를 확보해 In scope로 옮겼다 — ADR-0019 후속 조치)
- 목표 홈의 오늘 상세 카드와 가족 목록 조회 변경
- API 요청·응답 필드 변경
- cold-cache 전용 MySQL 벤치마크

## 3. 인터페이스 / API

기존 API 계약을 변경하지 않는다.

```http
GET /api/v1/goals/home/senior/weekly-status
GET /api/v1/goals/home/guardian/stats?memberId={seniorMemberId}
```

성공 코드와 응답 필드도 각각 `GOAL_HOME_2003`, `GOAL_HOME_2004`를 유지한다.

## 4. 데이터 모델

신규 엔티티·테이블·컬럼·인덱스가 없다. 다음 기존 모델과 Repository 메서드를 재사용한다.

- `MedicineSchedule.isEffectiveOn(LocalDate)`: 날짜별 스케줄 버전 유효성 판정
- `MedicineScheduleRepository.findEffectiveByMemberAndDateRange(...)`
- `MedicationProofRepository.findByMemberIdAndDateRange(...)`
- `WalkRepository.findByMemberAndWalkDateBetweenOrderByWalkDateAsc(...)`

## 5. 처리 흐름

두 API는 기존 `GoalHomeService`의 `@Transactional(readOnly = true)` 경계를 유지한다.

### 공통 기간 데이터 조회

1. 조회 시작일과 종료일을 계산한다.
2. 기간과 겹치는 ACTIVE 복약 스케줄 버전을 1회 조회한다.
3. 시작일 00:00부터 종료일 23:59:59.999999999까지의 복약 인증을 1회 조회한다.
4. 시작일~종료일 걸음 기록을 1회 조회한다.
5. 날짜별 유효 복약 스케줄, 인증된 스케줄 ID, 걸음 기록을 Map으로 구성한다.
6. 날짜별 총 목표 수와 완료 목표 수를 메모리에서 계산한다.

복약 완료는 해당 날짜의 인증 Map에 스케줄 ID가 존재할 때로 판정한다. 걸음 완료는 기존 `Walk.isGoalAchieved()`를 사용한다.

### 시니어 주간 상태

1. 이번 주 일요일~토요일 7일 데이터를 한 번 조회한다.
2. 미래 날짜는 기존과 동일하게 `NOT_STARTED`로 반환한다.
3. 과거·오늘은 날짜별 총 목표 수와 완료 목표 수로 `COMPLETED`, `FAILED`, `IN_PROGRESS`, `NOT_STARTED`를 결정한다.

### 보호자 주간 통계

1. 지난주 일요일~이번 주 토요일 14일 데이터를 한 번 조회한다.
2. 날짜별 달성률을 한 번 계산한다.
3. 지난주 전체 달성률은 지난주 7일의 완료 일수 비율로 계산한다.
4. 이번 주 전체 달성률은 오늘까지의 일별 달성률 중 `1.0`인 날짜의 비율로 계산한다.
5. 이번 주 일별 배열은 2단계 결과를 그대로 재사용하며 미래 날짜도 기존과 동일한 `0.0`을 반환한다.

예상 SQL 수:

| API | Before 전체/통계 | After 전체/통계 |
| --- | ---: | ---: |
| 시니어 주간 상태 | 21 / 20 | 4 / 3 |
| 보호자 주간 통계 | 77 / 76 | 4 / 3 |

## 6. 예외 / 에러 처리

신규 예외와 에러 코드는 없다. 존재하지 않는 `memberId`, 연결된 시니어가 없는 보호자 처리 등 기존 정책을 유지한다.

## 7. 인수조건 (Acceptance Criteria)

- [x] 두 API의 요청·응답 계약과 성공 코드가 변경되지 않는다.
- [x] 시니어 주간 상태 API가 미래 날짜를 `NOT_STARTED`로 반환한다.
- [x] 보호자 이번 주 일별 배열의 미래 날짜가 기존과 동일하게 `0.0`이다.
- [x] 복약 스케줄 버전의 `effectiveFrom`·`effectiveTo` 경계를 날짜별로 보존한다.
- [x] 지난주·이번 주 경계와 오늘 제외/포함 규칙을 보존한다.
- [x] 통계 데이터 조회가 API당 복약 스케줄·복약 인증·걸음 기록 각 1회로 고정된다.
- [x] 보호자 이번 주 일별 달성률 결과를 이번 주 전체 달성률 계산에 재사용한다.
- [x] Before 응답 SHA-256과 After 응답 SHA-256이 동일하다.
- [x] 동일 조건에서 SQL 수, 검사 행, Buffer Pool 읽기, p50/p95를 After 측정하고 개인 벤치마크 문서에 기록한다.
- [x] 기간 복약 인증 조회의 검사 행이 전체 인증 이력이 아니라 조회 창에 비례한다.
- [x] 보호자 API가 가족이 아닌 시니어의 `memberId`에 대해 `FORBIDDEN`을 반환한다.
- [x] 가족 접근 검증을 거치지 않는 핸들러가 있으면 테스트가 실패한다.
- [x] `./gradlew :backend:widyu-api:test`가 통과한다.

## 8. 영향 범위 / 마이그레이션

- `GoalHomeService` 내부 조회·집계 순서만 변경한다.
- 기존 Repository 메서드를 재사용한다. `medication_proof`에 인덱스 1개가 추가되며 `ddl-auto: update`로 다음 기동 시 생성된다. 운영에 별도 마이그레이션 절차가 있으면 `CREATE INDEX idx_medication_proof_member_verified ON medication_proof (member_id, verified_at)`를 수동 실행한다.
- API·Swagger·클라이언트 요청·응답 필드 변경은 없다. 다만 다른 가족의 `memberId`로 보호자 API를 호출하던 클라이언트가 있었다면 이제 `FORBIDDEN`을 받는다.
- 개인 벤치마크 문서는 `.gitignore` 대상이며 PR에 포함하지 않는다.

## 9. 미결정 사항 (Open Questions)

없음.

## 10. 참고

- Issue #489, #491, #492
- ADR-0019, ADR-0020, ADR-0002
- LLD-0007 약 복용 홈 일자별 조회와 복용 상태
- LLD-0008 약 복용 스케줄 버전링
- `apiDocs/engineering/performance/goal-home-query-pipeline-benchmark.md` (개인 로컬 문서)
