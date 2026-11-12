# LLD-0125: 복약 알람 동기화에 유효 기간을 싣고 내일 시작하는 알람도 보내기

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #975 |
| 관련 ADR | - (동기화 구조는 [LLD-0037](LLD-0037-medication-alarm-sync.md)) |
| 작성자 | Claude |
| 작성일 | 2026-11-09 |

## 1. 목적 / 배경

시니어 앱은 `GET /alarm-sync`로 받은 스케줄마다 앞으로 30일 치 OS 로컬 알람을 건다(앱 `MedicationSchedule.groups`, 이슈 #975 분석 기준이며 앱 코드는 이 저장소에서 확인하지 않음). 그런데 응답은 "오늘 유효한" 스케줄만 담고 유효 기간이 없다. 서버는 스케줄 버전마다 `effectiveFrom`, `effectiveTo`로 유효 기간을 정확히 갖고 있지만 앱에 전하지 않는다.

그래서 날짜 경계로 바뀌는 변경이 앱에서 틀어진다.

| 보호자 동작 (오늘 인증이 끝난 뒤) | 서버 기록 | 앱 동작 (변경 전) |
| --- | --- | --- |
| 알람 삭제 (M09-NEXT) | 기존 버전 `effectiveTo = 오늘` | 오늘 목록에 있어 내일부터 30일 다시 건다 |
| 알람 시각 변경 (M05-NEXT) | 기존 버전 `effectiveTo = 오늘`, 새 버전 `effectiveFrom = 내일` | 옛 시각은 내일도 울리고, 새 시각은 목록에 없어 걸리지 않는다 |
| 시간이 지난 새 알람 추가 (M08-NEXT) | 새 버전 `effectiveFrom = 내일` | 목록에 없어 앱을 다시 열 때까지 걸리지 않는다 |

2026-11-05 밤 실제 기기에서 알람 2개를 지우자 다음 날 아침 두 알람이 울렸고, 촬영 인증은 "오늘 유효하지 않은 약 복용 스케줄입니다."로 거절됐다. 서버 정시 푸시와 재알림은 이미 `findByAlarmTimeAndStatusEffectiveOn`으로 그날 유효한 버전만 고르므로, 울린 것은 앱 로컬 알람이다.

## 2. 범위

### In scope
- 변경 모듈: widyu-api
- 응답 스케줄마다 `effectiveFrom`, `effectiveTo`를 싣는다.
- 목록 범위를 "오늘 유효"에서 "오늘 이후 하루라도 유효"로 넓힌다. 내일 시작하는 버전이 들어간다.
- 시작 전에 취소돼 하루도 유효하지 않은 버전(`effectiveFrom > effectiveTo`)은 뺀다.

### Out of scope
- 앱 로컬 알람 적용 로직. 앱이 날짜마다 기간을 확인해야 한다(8절).
- `completed`, `revision`, `timeZone`. 바꾸지 않는다.
- 홈, 일별, 월별 조회 API. 화면 표시용이라 그대로 둔다. 앱이 알람을 걸 때 이 API들을 쓰는지는 앱팀 확인 대상이다.

### 기각한 대안
- **오늘 목록과 내일 목록을 나눠 보냄**: 지금 변경 규칙(오늘 아니면 내일부터)에서는 맞지만, "내일 목록이 모레 이후에도 같다"는 가정에 기댄다. 나중에 며칠 뒤부터 바뀌는 기능이 생기면 다시 깨진다.
- **내일 기준 목록에서 오늘 이미 울린 알람만 뺌**: 같은 revision인데 시각마다 응답이 달라져, "revision이 같으면 같은 스냅샷"이라는 LLD-0037 전제가 깨진다. 서버가 판단한 "울림"과 기기 알람 시각이 어긋날 수도 있다.
- **내일 시작하는 버전을 새 필드(`upcoming`)로 분리**: 구버전 앱이 새 필드를 무시해 호환되지만, 사용자 결정으로 앱 업데이트와 함께 배포하고 목록을 하나로 둔다(8절).

## 3. 인터페이스 / API

`GET /api/v1/goals/medicine-schedules/alarm-sync` (`MEDICINE_2010`). 스케줄에 두 필드가 추가된다.

```json
{
  "code": "MEDICINE_2010",
  "message": "복약 알람 동기화 조회 성공",
  "data": {
    "revision": 45,
    "timeZone": "Asia/Seoul",
    "schedules": [
      {"scheduleId": 15, "alarmTime": "08:00", "doseCount": 1.0, "effectiveFrom": "2026-10-01", "effectiveTo": "2026-11-09"},
      {"scheduleId": 17, "alarmTime": "19:27", "doseCount": 2.0, "effectiveFrom": "2026-10-15", "effectiveTo": null},
      {"scheduleId": 21, "alarmTime": "20:00", "doseCount": 2.0, "effectiveFrom": "2026-11-10", "effectiveTo": null}
    ],
    "completed": ["15:2026-11-09"]
  }
}
```

| 필드 | 타입 | 뜻 |
| --- | --- | --- |
| `doseCount` | 소수 | 이 시각에 먹을 약 개수(정 수) 합. 0.5정 단위라 `2.5`가 올 수 있고 정수도 `2.0`으로 실린다 (#1001) |
| `effectiveFrom` | `yyyy-MM-dd` | 이 버전이 유효한 첫날(`timeZone` 기준). 서버 시작 시 비어 있는 행을 생성일로 채우므로(`MedicineScheduleEffectivePeriodInitializer`) 실제로는 비어 있지 않다 |
| `effectiveTo` | `yyyy-MM-dd` 또는 `null` | 유효한 마지막 날. `null`이면 끝나는 날이 없다. 항상 키가 실린다 |

앱 규칙: 날짜 `d`마다 `effectiveFrom ≤ d` 이고 (`effectiveTo`가 `null`이거나 `d ≤ effectiveTo`)인 스케줄만 로컬 알람으로 건다. `completed`에 있는 `scheduleId:d`는 지금처럼 건너뛴다.

## 4. 데이터 모델

변경 없음. 기존 `medicine_schedule.effective_from`, `effective_to`를 읽기만 한다.

- `MedicineScheduleRepository.findEffectiveFromDateByMemberWithDetails(member, status, date)` 추가: `effectiveTo IS NULL` 또는 (`effectiveTo ≥ date` 이고 `effectiveFrom ≤ effectiveTo`). 알람 시각, 시작일 순 정렬.
- `MedicationAlarmSyncResponse.Schedule`에 `LocalDate effectiveFrom`, `LocalDate effectiveTo` 추가.

## 5. 처리 흐름

1. 현재 회원과 `Asia/Seoul` 기준 오늘 날짜를 구한다.
2. 오늘 인증 완료 스케줄 ID로 `completed`를 만든다(변경 없음).
3. 오늘 이후 하루라도 유효한 ACTIVE 스케줄 버전을 카테고리와 함께 조회해 기간과 함께 응답한다.

읽기 전용 트랜잭션이며 revision, FCM은 바뀌지 않는다.

## 6. 예외 / 에러 처리

새 에러 없음.

## 7. 인수조건 (Acceptance Criteria)

- [ ] AC1: 오늘 인증이 끝난 뒤 지운 알람은 `effectiveTo = 오늘`로 응답에 실린다.
- [ ] AC2: 끝난 알람의 시각을 바꾸면 옛 버전(`effectiveTo = 오늘`)과 새 버전(`effectiveFrom = 내일`)이 함께 실린다.
- [ ] AC3: 시간이 지난 새 알람(`effectiveFrom = 내일`)이 실린다.
- [ ] AC4: 어제 끝난 버전, 시작 전에 취소된 버전, 다른 회원 스케줄은 실리지 않는다.
- [ ] AC5: 날짜는 `yyyy-MM-dd` 문자열이고, 끝나는 날이 없으면 `effectiveTo: null` 키가 실린다.
- [ ] `./gradlew :backend:widyu-api:test`가 통과한다.

## 8. 영향 범위 / 배포

- **앱 업데이트와 함께 배포해야 한다.** 기간을 모르는 구버전 앱은 내일 시작하는 버전을 오늘도 건다. 예를 들어 08시 알람이 끝난 뒤 20시로 바꾸면 구버전 앱은 오늘 20시에 울리고 인증은 거절된다. 이 서버 변경만 먼저 배포하면 M05-NEXT, M08-NEXT 상황에서 지금보다 나빠진다.
- 응답 필드 추가라 DB 마이그레이션은 없다.
- 응답이 날짜에 기대지 않게 돼, 자정이 지나도 같은 revision이면 앱이 다시 조회하지 않아도 된다.

## 9. 미결정 사항 (Open Questions)

- 앱 배포 일정: 서버 배포를 앱 업데이트에 맞출 날짜. 앱 강제 업데이트 여부.
- 앱이 알람을 걸 때 `alarm-sync` 외에 홈, 일별 API를 쓰는지 앱팀 확인.
