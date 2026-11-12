# LLD-0135: 목표 달성률 항목별 평균과 월 비교

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #1013 |
| 관련 ADR | - |
| 작성자 | dongkyunKim |
| 작성일 | 2026-11-11 |

## 1. 목적 / 배경

Figma 「목표 홈_주간 달성률」, 「약복용 홈」, 「걷기 홈」 노트가 정한 달성률 계산법과 서버 계산이 다르다. 서버는 하루 복약 일정과 걷기를 한데 모아 비율을 내고 건강방문을 빼며, 지난주·월 달성은 모든 목표를 끝낸 날 수로 센다. 월 비교 박스는 요청 달과 그 전 달을 비교한다. 같은 계산을 한 곳에 두고 Figma 기준으로 맞춘다.

## 2. 범위

### In scope
- 변경 모듈: widyu-api
- 항목별 평균 계산기 `goal/rate/GoalRateCalculator`, 항목 집계 `GoalTally`
- `GET /api/v1/goals/home/guardian/stats`: `lastWeekGoalRate`, `todayGoalRate`, `thisWeekGoalRates`
- `GET /api/v1/goals/home/senior/weekly-status`: 날짜별 상태
- 약 복용 월별 조회: `comparison` 추가, `monthlyGoalRates` 반올림 기준
- 걷기 월별 조회: `comparison` 추가

### Out of scope
- 걷기 `dailyData`에 날짜별 달성률 필드를 더하지 않는다. 앱은 `goal`, `actual`로 달성 여부를 그린다.
- 기존 필드(`lastMonthAchieveCount`, `currentMonthAchieveCount`, `summary`) 삭제. 앱이 `comparison`으로 옮긴 뒤 지운다.
- 건강 일정 월별 조회, 시니어·보호자 홈(`/api/v1/home/...`) 카드.

## 3. 인터페이스 / API

달성률 계산법(모든 대상 API 공통):

- 기간 안에 목표가 있는 항목마다 `완수 ÷ 목표`를 구해 평균하고 정수 퍼센트로 반올림(HALF_UP)한다.
- 항목: 건강방문(완료 처리된 방문 수 ÷ 일정 수), 의약품(인증한 알람 수 ÷ 그날 유효한 알람 수), 걷기(목표를 채운 날 수 ÷ 목표가 있던 날 수).
- 목표가 없는 항목은 평균에서 뺀다. 모든 항목에 목표가 없으면 달성률은 없음(null)이다.
- 예: 건강방문 2/3, 의약품 20/30, 걷기 15/30 → (66.7 + 66.7 + 50.0) ÷ 3 = 61%.

기존 0.0~1.0 필드(`lastWeekGoalRate`, `todayGoalRate`, `thisWeekGoalRates`, `monthlyGoalRates`)는 반올림한 퍼센트 ÷ 100이다. 달성률이 없으면 0.0이다.

월 비교 `comparison` (약 복용, 걷기 월별 응답에 추가):

```json
"comparison": {
  "left":  {"year": 2026, "month": 10, "achieved": 20, "total": 31, "rate": 65},
  "right": {"year": 2026, "month": 11, "achieved": 5,  "total": 11, "rate": 45}
}
```

- `right`는 항상 이번 달이다. `left`는 보는 달이 이번 달이면 지난달, 아니면 보는 달이다.
- 약 복용: `achieved` 복용 횟수, `total` 예정 횟수.
- 걷기: `achieved` 하루 목표까지만 센 걸음 합계, `total` 목표 걸음 합계, 그리고 `achievedDays`, `totalDays`.
- `rate`는 `achieved ÷ total` 정수 퍼센트, `total`이 0이면 null.

## 4. 데이터 모델

변경 없음. DTO만 바뀐다: `MedicineMonthlyResponse.MonthComparison/MonthProgress`, `WalkMonthlyResponse.MonthComparison/MonthProgress`.

## 5. 처리 흐름

### 주간·일별 (`GoalHomeService`)
1. 기존 기간 조회(복약 스케줄, 인증, 걸음)에 같은 기간 건강 일정 조회 1회를 더한다.
2. 날짜별로 세 항목의 `GoalTally`를 만든다. 걷기는 기록이 있거나 기본 목표 시작일 이후면 목표 1일이다.
3. 일별 달성률 = 세 항목 평균. 오늘 이후 날짜는 0.0이다.
4. 지난주 달성률 = 지난주 7일 항목별 합계의 평균.
5. 시니어 상태: 목표 없음 또는 미래 → `NOT_STARTED`, 모든 항목 완수 → `COMPLETED`, 지난 날 → `FAILED`, 오늘 하나라도 완수 → `IN_PROGRESS`. 반올림 전 값으로 판정한다.

오늘은 아직 알람·일정 시각이 오지 않은 복용·방문도 분모에 넣는다. 오늘 달성률은 하루 진행률이고, 지난 시각만 넣으면 아침 약 한 번으로 100%가 됐다가 점심에 50%로 내려가며 시니어 상태도 `COMPLETED`에서 `IN_PROGRESS`로 되돌아간다. 이번 주는 오늘까지만 센다.

### 월별 (`MedicineScheduleService`, `WalkService`)
1. 요청 달, 전달(기존 필드), 왼쪽 달, 이번 달의 날짜별 집계를 만든다. 같은 달은 한 번만 계산한다.
2. 기존 필드는 그대로 계산한다(모두 먹은 날 수, 달성 일수).
3. `comparison`은 1일부터 오늘까지(지난 달은 말일까지) 합계다. 이번 달 이후 달은 0이다.
4. 걷기의 날짜별 목표는 `dailyData`와 같은 규칙(기록, 미수신 날의 다음 기록 목표, 기본 목표)을 쓴다.

## 6. 예외 / 에러 처리

신규 예외 없음.

## 7. 인수조건 (Acceptance Criteria)

- [x] 건강방문 2/3, 의약품 20/30, 걷기 15/30이면 61을 반환한다.
- [x] 목표가 없는 항목은 평균에서 빠지고, 모두 없으면 null(기존 필드 0.0)이다.
- [x] 정수 퍼센트로 반올림한다(14.5 → 15).
- [x] 지난주 달성률은 7일 합계의 항목별 평균이다.
- [x] 오늘 약을 다 먹어도 건강방문이 남았으면 시니어 상태는 `IN_PROGRESS`다.
- [x] 걷기 월 진행률은 하루 목표를 넘은 걸음을 세지 않는다.
- [x] 보는 달이 이번 달이면 지난달과 이번 달, 아니면 보는 달과 이번 달을 비교한다.
- [x] 이번 달 `comparison`은 1일~오늘만 센다.
- [x] 기존 응답 필드는 그대로 내려가고 Swagger에 폐기 예정으로 표시한다.
- [x] `./gradlew :backend:widyu-api:test`가 통과한다.

## 8. 영향 범위 / 마이그레이션

- 응답 필드 추가만 있다. 기존 필드 값은 계산법 변경으로 달라진다: 주간 비율에 건강방문이 들어가고, 일별 비율은 소수 둘째 자리(반올림 퍼센트 ÷ 100)가 된다.
- 목표 홈 주간 API는 건강 일정 조회 1회가 늘어난다.
- 걷기 월별 조회는 비교 달이 요청 달과 다르면 그 달 일별 데이터를 한 번 더 만든다(조회 3회).

## 9. 미결정 사항 (Open Questions)

- 이번 달 `comparison.total`을 1일~오늘로 둘지, 그 달 전체 예정으로 둘지. 이번 구현은 오늘까지다(전체로 두면 월초 진행률이 항상 낮다).
- 목표가 없는 달의 `rate`를 null로 둘지 0으로 둘지. 이번 구현은 null이다.
