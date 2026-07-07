# LLD-0007: 약 복용 홈 일자별 조회와 복용 상태

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Approved |
| Issue | #357 |
| 관련 ADR | - |
| 작성자 | dongkyunKim |
| 작성일 | 2026-07-06 |

## 1. 목적 / 배경

목표 탭의 약 복용 홈에서 하단 카드가 항상 "오늘" 기준으로만 표시되어, 사용자가 날짜를 눌러도 현황이 바뀌지 않았다.
기존 `GET /today`는 날짜 필터 없이 활성 스케줄만 반환하고 복용 상태를 포함하지 않았다.
선택한 날짜의 스케줄과 각 스케줄의 복용 상태(완료/미복용/놓침)를 반환해, 날짜별로 카드가 바뀌도록 한다.

## 2. 범위

### In scope
- 변경 모듈: widyu-api (goal/medicineschedule)
- `GET /today` → `GET /daily?date=yyyy-MM-dd` 교체
- 선택 날짜 기준 각 스케줄의 복용 상태(`status`) 반환
- `MedicationStatus` enum (DONE / UPCOMING / MISSED)
- 인증 허용창(알람 ±30분) 상수를 `MedicationStatus`에 단일 정의, 인증 검증도 참조
- Swagger 문서 갱신

### Out of scope
- 복용 인증 제출(`verifyMedication`) 로직 변경 — 시간창·중복 방지 규칙은 기존 유지
- 월별 통계(`/monthly`), 시니어 홈(`/home`) 응답 변경
- 시니어 인증 버튼 활성화 등 클라이언트 UI 로직
- 미복용 스케줄에 대한 리마인드/보호자 알림 정책

## 3. 인터페이스 / API

```http
GET /api/v1/goals/medicine-schedules/daily?date=2026-07-06&memberId=1
```

- `date`: 필수, `yyyy-MM-dd`
- `memberId`: 선택. null이면 본인, 값이 있으면 보호자가 가족으로 연결된 시니어 조회 (`@ValidateFamilyAccess`)

Response:
```json
{
  "isSuccess": true,
  "code": "MEDICINE_2001",
  "message": "일자별 약 복용 현황 조회 성공",
  "result": {
    "medicineSchedules": [
      {
        "medicineScheduleId": 1,
        "totalCount": 3,
        "alarmTime": "08:00",
        "status": "DONE",
        "medicines": [
          { "name": "타이레놀", "count": 1 },
          { "name": "비타민C", "count": 2 }
        ]
      },
      {
        "medicineScheduleId": 2,
        "totalCount": 1,
        "alarmTime": "20:00",
        "status": "UPCOMING",
        "medicines": [
          { "name": "오메가3", "count": 1 }
        ]
      }
    ]
  }
}
```

`status`: `DONE`(복용 인증 완료) / `UPCOMING`(미인증, 인증 마감 전) / `MISSED`(미인증, 인증 마감 후)

## 4. 데이터 모델

신규 엔티티·테이블·컬럼 없음.

- `MedicationStatus` (widyu-api, `goal/medicineschedule/dto/response`): 응답 계산용 enum. **영속 대상 아님** (DB ENUM 컬럼 아님).
  - 상수 `ALLOWED_WINDOW_MINUTES = 30` 를 단일 정의. `MedicationProofService`의 인증 허용창도 이 상수를 참조.
- `MedicineScheduleDailyResponse` (widyu-api, dto/response): 기존 `MedicineScheduleTodayResponse`를 대체. 정적 팩토리 `of()` / `ScheduleItem.from(schedule, status)` 사용.
- 재사용 엔티티: `MedicineSchedule`(알람시간·카테고리·약품), `MedicationProof`(`verifiedAt`).

## 5. 처리 흐름

`MedicineScheduleService.getDailySchedules(memberId, date)` — `@Transactional(readOnly = true)`

```
1. @ValidateFamilyAccess(memberIdParam="memberId")로 보호자-시니어 접근 검증
2. getMember(memberId): null이면 memberUtil.getCurrentMember(), 아니면 memberRepository.findById
3. medicineScheduleRepository.findByMemberAndStatusWithDetails(member, ACTIVE)
   → 활성 스케줄 + 카테고리/약품 fetch join
4. 인증된 스케줄 id 집합 조회 (findVerifiedScheduleIds)
   - 스케줄 id 목록이 비면 조회 생략 후 빈 Set 반환
   - MedicationProofRepository.findVerifiedScheduleIds(ids, date 00:00:00 ~ date 23:59:59.999999999)
5. now = LocalDateTime.now()
6. 스케줄별로 verified = 집합 포함 여부 → MedicationStatus.of(verified, date, alarmTime, now)
7. MedicineScheduleDailyResponse.of(items) 반환
```

상태 계산 `MedicationStatus.of(verified, date, alarmTime, now)`:
```
verified == true                                   → DONE
now > date.atTime(alarmTime) + 30분(인증 마감)     → MISSED
그 외                                               → UPCOMING
```
- 인증은 항상 "오늘" 기준으로만 제출 가능하므로(지난 날짜 소급 인증 불가), 과거 날짜의 미인증은 항상 MISSED로 일관된다.
- 인증 마감(알람+30분)은 `verifyMedication`의 인증 가능 상한과 동일한 경계다.

## 6. 예외 / 에러 처리

| 상황 | 처리 |
|------|------|
| `date` 형식 오류 | 400 Bad Request (Spring `@DateTimeFormat(ISO.DATE)` 파싱 실패) |
| 존재하지 않는 `memberId` | `BusinessException(BAD_REQUEST)` "존재하지 않는 사용자입니다." |
| 보호자-시니어 가족 연결 없음 | 403 Forbidden (`@ValidateFamilyAccess`) |
| 활성 스케줄 없음 | 빈 `medicineSchedules` 목록 반환 (인증 조회 생략) |

신규 에러 코드 없음. 성공 코드 `MEDICINE_2001` 유지(메시지만 "일자별 …"로 변경).

## 7. 인수조건 (Acceptance Criteria)

- [x] `GET /today`를 제거하고 `GET /daily?date=`로 조회한다.
- [x] 각 스케줄에 선택 날짜 기준 `status`(DONE/UPCOMING/MISSED)를 반환한다.
- [x] 복용 인증이 있으면 `DONE`을 반환한다.
- [x] 미인증이고 인증 마감(알람+30분) 전이면 `UPCOMING`을 반환한다.
- [x] 미인증이고 인증 마감이 지났으면 `MISSED`를 반환한다.
- [x] 활성 스케줄이 없으면 빈 목록을 반환하고 인증 조회를 생략한다.
- [x] 인증 허용창(30분)은 `MedicationStatus`의 단일 상수이며 `verifyMedication`도 이를 참조한다.
- [x] Swagger에 `status` 설명과 일자별 조회 응답이 반영된다.
- [x] 상태 계산 3케이스와 서비스 매핑 단위 테스트가 존재한다.
- [x] `./gradlew :backend:widyu-api:test`가 통과한다.

## 8. 영향 범위 / 마이그레이션

- DB 스키마 변경 없음. `MedicationStatus`는 응답 전용 enum이라 MySQL ENUM ALTER 불필요.
- **API 계약 변경(Breaking)**: `/today`가 제거되고 `/daily?date=`로 대체된다. 응답에서 기존 `taken`(boolean, 실제로는 미포함) 대신 `status`가 추가된다.
  - 앱 클라이언트가 `date` 파라미터 전달 + `status` 분기를 구현해야 하며, 배포 타이밍을 서버와 맞춰야 한다.
- `MedicationProofService`의 인증 허용창 상수 출처가 `MedicationStatus.ALLOWED_WINDOW_MINUTES`로 변경(값 30분 동일, 동작 변화 없음).
- 응답 리스트 키를 `medicineSchedule`(단수) → `medicineSchedules`(복수)로 정정(#366). 약복용 홈·목표 홈 응답과 필드명·`status` 표현을 통일하는 작업의 일부이며, 단수 키 파싱 클라이언트는 수정이 필요하다.

## 9. 미결정 사항 (Open Questions)

없음. (구현 완료, PR #358 기준 백필)

후속 개선 후보: 미복용/놓침 상태와 보호자 리마인드 알림 연계 여부, 시니어 화면의 인증 버튼 활성화(창 열림) 상태를 서버가 별도 필드로 내려줄지 여부.

## 10. 참고

- `MedicineScheduleController.java`: `GET /daily` 엔드포인트
- `MedicineScheduleService.getDailySchedules`: 일자별 조회·상태 매핑
- `MedicationStatus.java`: 복용 상태 enum + 계산, 인증 허용창 상수
- `MedicineScheduleDailyResponse.java`: 응답 DTO (팩토리 메서드)
- `MedicationProofRepository.findVerifiedScheduleIds`: 날짜별 인증 스케줄 조회
- `MedicationProofService.java`: 인증 허용창 상수 공유, 인증 제출 규칙
- Issue #357, PR #358
