# ARCH-009 Senior 가입 행위자 타입 검증 구현 설계

## 1. 문제 정의

### 현재 확인된 동작

- `SeniorAuthService.seniorSignUpBulk()`는 `memberUtil.getCurrentMember()`로 현재 회원을 조회한다.
- 조회 직후 `member.getType()`을 확인하지 않고, FamilyMembership 존재 여부(`findByGuardianId`)만 검증한다.
- Spring Security는 GUARDIAN·SENIOR 모두 같은 `USER` role(`MemberRole`)로 처리한다.

### 보안상 문제

- 유효한 JWT를 가진 SENIOR 회원이 `/api/v1/auth/senior/signup` (또는 해당 경로)를 호출하면,
  Family 생성 → SeniorProfile 저장 → leader FamilyMembership 생성이 모두 수행된다.
- Family 생성과 leader 등록은 GUARDIAN만 수행해야 한다는 불변식이 서비스 경계에서 보장되지 않는다.

### 깨진 불변식

- Family 생성과 leader FamilyMembership 등록은 MemberType.GUARDIAN 회원만 수행해야 한다.

## 2. 목표

### 변경 후 보장할 불변식

- `seniorSignUpBulk()` 진입 시 현재 회원의 `type`이 `GUARDIAN`이 아니면 `BusinessException(ErrorCode.FORBIDDEN)`으로 거부한다.
- 거부 에러 코드는 HTTP 403(`FORBIDDEN`) 계열이고, 기존 `ErrorCode.FORBIDDEN`을 재사용한다.
- GUARDIAN 회원의 기존 정상 등록 흐름(FamilyMembership 중복 확인 → Family 생성 → Senior 저장 → 리더십 부여)은 유지된다.

### 기존에 유지할 동작

- GUARDIAN 회원의 seniorSignUpBulk() 정상 실행 흐름 전체.
- SENIOR 회원의 시니어 로그인(`seniorSignIn()`)은 이번 변경 범위 외.
- admin 역할의 직접 호출이 필요할 경우 별도 정책 확인 후 후속 PR에서 처리.

## 3. 변경 설계

### 3.1 변경 파일

| 파일 | 변경 유형 |
| --- | --- |
| `backend/widyu-api/src/main/java/com/widyu/auth/application/senior/SeniorAuthService.java` | 수정 |
| `backend/widyu-api/src/test/java/com/widyu/auth/application/senior/SeniorAuthServiceTest.java` | 수정 (테스트 추가) |

### 3.2 SeniorAuthService 변경

`seniorSignUpBulk()` 메서드에서 `getCurrentMember()` 직후 guardian 타입 검증을 삽입한다.

```java
@Transactional
public void seniorSignUpBulk(List<SeniorSignUpRequest> requests) {
    Member guardian = memberUtil.getCurrentMember();
    validateIsGuardian(guardian);           // ← 추가
    validateRequestsNotEmpty(requests);
    ...
}

private void validateIsGuardian(Member member) {
    if (member.getType() != MemberType.GUARDIAN) {
        throw new BusinessException(ErrorCode.FORBIDDEN);
    }
}
```

**검증 순서 결정**: guardian type 검증을 empty requests 검증보다 앞에 둔다.
- 이유: MemberType 위반은 요청 내용과 무관한 행위자 권한 문제이므로 가장 먼저 차단해야 한다.
- empty requests 검증이 앞에 있으면, SENIOR 회원의 빈 요청이 `SENIOR_SIGNUP_REQUEST_EMPTY`(400)로 반환되어 오류 의미가 모호해진다.

### 3.3 허용되는 부수 변경 없음

삼항 연산자, DTO 직접 생성, 기타 CLAUDE.md 위반 사항이 수정 대상 범위에 없다.

## 4. 테스트 시나리오 (TEST-002)

| 번호 | 시나리오 | 유형 | 기대 결과 |
| --- | --- | --- | --- |
| T1 | SENIOR 회원이 시니어 등록 API 호출 | 단위 | `BusinessException(FORBIDDEN)` |
| T2 | GUARDIAN 회원이 정상 등록 (기존 테스트) | 단위 | 정상 저장 완료 |
| T3 | GUARDIAN 회원이 이미 가족 소속일 때 (기존 테스트) | 단위 | `ALREADY_CONNECTED_TO_FAMILY` |

T2·T3는 기존 테스트에 이미 존재한다. T1만 신규 추가한다.

### T1 상세

```java
@Test
@DisplayName("시니어 타입 회원이 시니어 등록을 시도하면 FORBIDDEN 예외가 발생한다")
void 시니어_타입_회원이_시니어_등록을_시도하면_FORBIDDEN_예외가_발생한다() {
    // given — SENIOR 타입 Member
    Member senior = Member.createMember(MemberType.SENIOR, "시니어", "01011112222");
    given(memberUtil.getCurrentMember()).willReturn(senior);

    List<SeniorSignUpRequest> requests = List.of(
            new SeniorSignUpRequest("부모님", LocalDate.of(1950, 1, 1), "01011112222", "서울", "1234567")
    );

    // when & then
    assertThatThrownBy(() -> seniorAuthService.seniorSignUpBulk(requests))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
}
```

### strict stub 주의

기존 테스트 중 `given(memberUtil.getCurrentMember()).willReturn(guardian)`을 선언한 뒤
`familyMembershipRepository.findByGuardianId`를 stub하지 않은 케이스가 있다면 순서 변경에 주의한다.
T1에서는 `validateIsGuardian`이 던지므로 `familyMembershipRepository` stub이 불필요하다.

## 5. 구현 순서

1. `SeniorAuthServiceTest.java`에 T1 테스트 추가 → 실패 확인 (RED)
2. `SeniorAuthService.java`에 `validateIsGuardian` 추가 → 테스트 통과 (GREEN)
3. 전체 테스트 실행: `./gradlew :backend:widyu-api:test`

## 6. 완료 기준

- SENIOR 타입으로 `seniorSignUpBulk()` 호출 시 `FORBIDDEN` 예외 발생
- 기존 `SeniorAuthServiceTest` 9개 테스트 전부 통과
- `./gradlew build` BUILD SUCCESSFUL

## 7. 제외 범위

- `seniorSignIn()` 흐름: 시니어 로그인은 행위자 타입 검증 대상이 아님
- admin 직접 호출 허용 여부: 별도 정책 결정 후 후속 PR
- SecurityConfig URL 패턴 변경: 이번 PR에서 수정하지 않음

## 8. 위험·롤백

- **위험**: 현재 SENIOR 토큰으로 seniorSignUpBulk를 호출하던 클라이언트가 있다면 차단됨 — 의도된 보안 강화
- **롤백 가능성**: 높음. `validateIsGuardian` 한 줄 제거로 이전 동작 복원 가능

## 9. Fable 자가 검증

- [x] 변경 대상 파일 목록 확정 (2개 파일만)
- [x] 기존 테스트 stub 순서 충돌 없음 확인 (SENIOR stub은 familyMembershipRepository 불필요)
- [x] `ErrorCode.FORBIDDEN` 재사용 (새 에러 코드 추가 없음)
- [x] `member.getType()`은 `@Getter` Lombok으로 자동 생성됨 확인
- [x] 삼항 연산자 없음, early return 패턴 사용
- [x] 검증 순서: guardian type → empty requests → membership exists (가장 먼저 행위자 차단)
