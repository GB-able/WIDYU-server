# 코드 리뷰 결과 — feature/#234 마이페이지 구현

> 리뷰 일자: 2026-04-16
> 리뷰 범위: 마이페이지 전체 구현 (시니어 / 보호자)
> 브랜치: `feature/#234`

---

## 구현 범위 요약

| 도메인 | 기능 |
|--------|------|
| SeniorMyPageService | 내 정보 조회, 가족코드 조회, 프로필 설정 조회/수정, 포인트 내역 조회, 비상연락처 조회/변경 |
| GuardianMyPageService | 내 정보 조회, 프로필 설정 조회/수정, 연결된 시니어 목록 조회, 시니어 프로필 조회/수정, 가족 멤버 관리(방장 변경, 멤버 삭제) |
| 엔티티 신규/변경 | `PointHistory`, `PointHistoryType` 신규 / `FamilyConnection(isRepresentative, isLeader)`, `SeniorProfile(familyCode)`, `Member(birthDate)` 변경 |

---

## 발견된 문제 및 수정 내용

### 🔴 버그 (즉시 수정)

#### 1. `getFamilyMembers()` — 중복 DB 쿼리

**위치:** `GuardianMyPageService.java:113-124`

`getSeniorProfileWithAccessCheck()`의 반환값을 버리고 동일한 `findByMemberId()` 쿼리를 한 번 더 실행.

```java
// Before — 동일 쿼리 2회
getSeniorProfileWithAccessCheck(seniorId, guardian.getId()); // 결과 버림
SeniorProfile seniorProfile = seniorProfileRepository.findByMemberId(seniorId) // 중복
        .orElseThrow(...);

// After — 반환값 재사용
SeniorProfile seniorProfile = getSeniorProfileWithAccessCheck(seniorId, guardian.getId());
```

---

#### 2. `changeLeader()` — 검증 전 상태 변경

**위치:** `GuardianMyPageService.java:139-151`

`targetGuardianId`가 가족 구성원에 없는 경우에도 루프가 먼저 실행되어 기존 방장의 `isLeader`가 `false`로 변경된 뒤 예외가 던져짐. `@Transactional` 롤백으로 DB는 보호되지만 코드 의도가 불명확.

```java
// Before — 변경 먼저, 검증 나중
for (FamilyConnection c : connections) {
    if (...) { c.setLeader(true); targetFound = true; }
    else      { c.setLeader(false); }  // 기존 리더 해제가 먼저 발생
}
if (!targetFound) throw new BusinessException(...);

// After — 검증 먼저, 변경 나중
boolean targetFound = connections.stream()
        .anyMatch(c -> c.getGuardian().getId().equals(targetGuardianId));
if (!targetFound) throw new BusinessException(...);

connections.forEach(c -> c.setLeader(c.getGuardian().getId().equals(targetGuardianId)));
```

---

#### 3. `updateRepresentativeContact()` — 동일 패턴 버그

**위치:** `SeniorMyPageService.java:119-131`

`changeLeader()`와 동일한 구조적 문제. 동일한 방식으로 수정.

---

### 🟡 잠재적 버그 / 개선 사항

#### 4. `AlbumUnlockService` — 타입 검증 순서

시니어 타입 검증이 중복 해금 체크 이후에 위치. 보호자 계정이 이미 해금된 앨범에 요청하면 `ALBUM_ALREADY_UNLOCKED` 에러가 먼저 반환됨. 검증 순서를 앞으로 이동 권장.

#### 5. `generateUniqueFamilyCode()` — 레이스 컨디션

`existsByFamilyCode` 체크와 코드 저장 사이에 동시 요청 시 DB unique constraint 위반으로 `DataIntegrityViolationException` 발생 가능. 36^6 ≈ 22억 가지 조합이라 실제 발생 가능성은 매우 낮지만, 최대 재시도 횟수 제한 추가 권장.

```java
private String generateUniqueFamilyCode() {
    for (int attempt = 0; attempt < 5; attempt++) {
        String code = generateCode();
        if (!seniorProfileRepository.existsByFamilyCode(code)) return code;
    }
    throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "가족코드 생성에 실패했습니다.");
}
```

#### 6. `updateSeniorPhone()` — 방장 권한 미검증

모든 보호자가 시니어의 전화번호를 수정할 수 있음. 기획에서 방장만 가능하다면 권한 체크 추가 필요.

#### 7. `static` 상수 선언 위치

`SeniorAuthService`에서 상수들이 메서드 선언 아래에 위치. 프로젝트 컨벤션상 클래스 상단으로 이동 권장.

---

### 🟢 잘 구현된 부분

- `@Transactional(readOnly = true)` 클래스 레벨 + mutating 메서드만 재선언 — 정석 패턴
- `PointHistory.earn()` / `use()` 정적 팩토리 메서드로 생성 의도 명확화
- `getSeniorProfileWithAccessCheck()` private 메서드로 접근 제어 공통화 (DRY)
- `@Query JOIN FETCH`로 N+1 문제 사전 방지 (`findAllBySeniorIdWithGuardian`)
- Docs 인터페이스 / Controller / Service 계층 분리 일관 유지
- DTO records `from()` / `of()` 정적 팩토리 패턴

---

## 테스트 현황

### 기존 테스트 (버그 수정 후 전체 통과)

| 클래스 | 테스트 수 |
|--------|----------|
| SeniorMyPageServiceTest | 12건 |
| GuardianMyPageServiceTest | 기존 14건 |

### 이번 리뷰에서 추가된 테스트 (5건)

| 테스트명 | 검증 내용 |
|---------|----------|
| `시니어_전화번호_수정` | 보호자가 시니어 전화번호 수정 시 Member.updatePhoneNumber 호출 확인 |
| `시니어_프로필_이미지_수정_기존이미지_삭제` | 기존 이미지 S3 삭제 + 새 이미지 업로드 |
| `시니어_프로필_이미지_수정_기존이미지_없음` | S3 삭제 없이 업로드만 진행 |
| `가족_멤버_목록_조회_방장이_아닌_경우` | isCurrentUserLeader = false 반환 확인 |
| `방장_변경_가족_구성원이_아닌_경우` | 가족에 없는 guardianId 지정 시 BusinessException 확인 |

**총 32개 테스트 전체 PASS**

---

## 미해결 항목 (후속 검토)

- [ ] `updateSeniorPhone()` 방장 권한 검증 여부 — 기획 확인 필요
- [x] `AlbumUnlockService` 타입 검증 순서 수정 — 시니어 타입 검증을 중복 해금 체크 앞으로 이동
- [x] `generateUniqueFamilyCode()` 재시도 로직 추가 — 최대 5회 시도 후 `INTERNAL_SERVER_ERROR`, static 상수 클래스 상단으로 이동

## 추가 개선 적용 (2026-04-17)

- [x] `GuardianMyPageDocs` — `seniorId`, `guardianId` 파라미터에 `@Parameter` 어노테이션 추가
- [x] `가족_멤버_목록_조회_방장이_아닌_경우` 테스트 — `getName()` 스터빙 추가 및 name 필드 검증 강화
