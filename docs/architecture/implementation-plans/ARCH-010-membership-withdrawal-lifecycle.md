# ARCH-010 탈퇴 시 FamilyMembership 삭제 — 비활성 leader 잔존 제거

## 1. 문제 정의

### 현재 확인된 동작

- `MemberWithdrawService.withdrawMember()`는 다음 순서로 탈퇴를 처리한다:
  1. RefreshToken 삭제
  2. 소셜 계정 revoke
  3. 개인정보 마스킹 (`member.maskPersonalInfo()`)
  4. 탈퇴 처리 (`member.withdraw()`)
  5. 회원 저장
- `FamilyMembership`은 위 어떤 단계에서도 삭제 또는 비활성화되지 않는다.

### 깨진 불변식

- 유효한 guardian만 Family 구성원·leader여야 한다. 탈퇴한 guardian이 `is_leader = true` 상태의 FamilyMembership을 보유한 채 남을 수 있다.

## 2. 목표

### 변경 후 보장할 불변식

- `withdrawMember()` 완료 후 해당 guardian의 `FamilyMembership`은 존재하지 않는다.
- `FamilyMembership`이 없는 guardian 탈퇴 시에도 예외 없이 정상 완료된다.

### 이번 PR에서 보류하는 정책

- **leader 승계**: 탈퇴 guardian이 leader였을 경우 다른 guardian을 자동 승계 → 후속 PR
- **Family 삭제**: 마지막 guardian 탈퇴 시 Family Aggregate 처리 → 후속 PR

## 3. 변경 설계

### 3.1 변경 파일

| 파일 | 변경 유형 |
| --- | --- |
| `backend/widyu-api/src/main/java/com/widyu/member/repository/FamilyMembershipRepository.java` | 수정 (메서드 추가) |
| `backend/widyu-api/src/main/java/com/widyu/auth/application/guardian/MemberWithdrawService.java` | 수정 (삭제 단계 추가) |
| `backend/widyu-api/src/test/java/com/widyu/auth/application/guardian/MemberWithdrawServiceTest.java` | 수정 (테스트 추가) |

### 3.2 FamilyMembershipRepository 변경

```java
// 기존 메서드 유지, 아래 1개 추가
void deleteByGuardianId(Long guardianId);
```

Spring Data JPA 파생 메서드. `guardian_id` 컬럼 기준 삭제.

### 3.3 MemberWithdrawService 변경

```java
@Service
@RequiredArgsConstructor
public class MemberWithdrawService {

    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final FamilyMembershipRepository familyMembershipRepository;  // ← 추가
    private final SocialLoginStrategyFactory strategyFactory;
    private final MemberUtil memberUtil;

    @Transactional
    public void withdrawMember(MemberWithdrawRequest request) {
        Member member = memberUtil.getCurrentMember();

        log.info("회원 탈퇴 시작: memberId={}, reason={}", member.getId(), request.reason());

        // 1. 리프레시 토큰 삭제
        refreshTokenRepository.deleteById(member.getId());

        // 2. 연동된 모든 소셜 계정 탈퇴
        withdrawAllSocialAccounts(member);

        // 3. FamilyMembership 삭제  ← 추가
        familyMembershipRepository.deleteByGuardianId(member.getId());

        // 4. 개인정보 마스킹 (GDPR 준수)
        member.maskPersonalInfo();

        // 5. 로컬 계정 삭제
        member.withdraw();

        // 6. 회원 데이터 저장
        memberRepository.save(member);

        log.info("회원 탈퇴 완료: memberId={}", member.getId());
    }
    // ... 나머지 private 메서드 유지
}
```

**삽입 위치**: 소셜 계정 revoke(2) 후, 개인정보 마스킹(4) 전. 이유: Membership 삭제는 DB 관계 정리이므로 개인정보 처리보다 먼저 수행해도 무방하고, 소셜 revoke와 독립적이다.

## 4. 테스트 시나리오 (TEST-003)

| 번호 | 시나리오 | 기대 결과 |
| --- | --- | --- |
| T1 | FamilyMembership 있는 guardian 탈퇴 | `deleteByGuardianId(memberId)` 호출됨 |
| T2 | FamilyMembership 없는 guardian 탈퇴 | 예외 없이 정상 완료 (`deleteByGuardianId` 호출 시 no-op) |

### T1 상세

```java
@Test
@DisplayName("탈퇴 시 해당 guardian의 FamilyMembership이 삭제된다")
void 탈퇴_시_FamilyMembership이_삭제된다() {
    // given
    Member member = Member.createMember(MemberType.GUARDIAN, "홍길동", "01012345678");
    ReflectionTestUtils.setField(member, "id", 1L);
    ReflectionTestUtils.setField(member, "socialAccounts", new ArrayList<>());
    given(memberUtil.getCurrentMember()).willReturn(member);

    // when
    memberWithdrawService.withdrawMember(new MemberWithdrawRequest("탈퇴 사유"));

    // then
    verify(familyMembershipRepository).deleteByGuardianId(1L);
}
```

### T2 상세

`deleteByGuardianId`는 파생 메서드로 대상이 없을 때 no-op. 별도 예외 처리 불필요.
기존 "소셜계정 없는 회원 탈퇴" 테스트(T0)에 `verify(familyMembershipRepository).deleteByGuardianId(1L)` assertion을 추가해도 됨.

## 5. 구현 순서

이번 PR은 변경 파일 3개가 서로 독립적이므로 병렬 작성 가능:

1. `FamilyMembershipRepository`에 `deleteByGuardianId` 추가
2. `MemberWithdrawService`에 `familyMembershipRepository` 의존 추가 + 삭제 호출 삽입
3. `MemberWithdrawServiceTest`에 T1, T2 추가 + 기존 테스트에 membership 삭제 assert 추가
4. (1, 2, 3 동시 작성 가능 — 파일 비중복)
5. `./gradlew :backend:widyu-api:test` → 전체 통과 확인
6. `./gradlew build` → BUILD SUCCESSFUL 확인

## 6. 완료 기준

- 기존 `MemberWithdrawServiceTest` 5개 + 신규 T1, T2 테스트 전부 통과
- `FamilyMembership` 없는 경우에도 탈퇴 정상 완료
- `MemberWithdrawService`에 `FamilyMembershipRepository` 의존 추가, `deleteByGuardianId` 호출 확인
- `./gradlew build` BUILD SUCCESSFUL

## 7. 제외 범위

- leader 승계 정책 (별도 PR)
- Family 자체 삭제 (별도 PR)
- SeniorProfile의 FamilyMembership 관계 변경 없음

## 8. Fable 자가 검증

- [x] 삼항 연산자 없음
- [x] DTO 직접 생성 없음
- [x] `deleteByGuardianId`는 Spring Data JPA 파생 메서드 — interface 선언만으로 구현됨
- [x] 변경 파일 3개가 서로 비중복 — 병렬 작성 가능
- [x] 기존 5개 테스트 시나리오 유지 (신규 의존 `familyMembershipRepository` mock 추가 필요)
- [x] `deleteByGuardianId`가 no-op일 때 예외 없음 확인 (JPA 파생 delete는 대상 없으면 조용히 완료)
