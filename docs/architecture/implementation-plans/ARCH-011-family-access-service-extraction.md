# ARCH-011 FamilyAccessService 추출 — 가족 접근 정책 단일화

## 1. 문제 정의

### 현재 확인된 동작

- `FamilyAccessAspect.validateFamilyAccess()`는 다음 4단계 검증을 직접 수행한다.
  1. currentMember가 GUARDIAN인지 확인
  2. targetMember 존재 조회(`MemberRepository.findById`)
  3. targetMember가 SENIOR인지 확인
  4. SeniorProfile 존재 확인 + 가족 관계 확인(`FamilyMembershipRepository.existsByGuardianIdAndSeniorProfileId`)
- `FamilyAccessAspect`가 `MemberRepository`와 `FamilyMembershipRepository`를 직접 의존한다.
- `FamilyAccessAspect`의 로직이 서비스 계층으로 분리되지 않아 비-AOP 경로에서 동일 정책을 재사용할 단일 진실 공급원이 없다.

### 깨진 불변식

- 가족 접근 정책이 AOP 구현에 묶여 있으므로, AOP 경로와 직접 Service 호출 경로가 동일한 권한 판정을 내리는지 검증할 방법이 없다.

## 2. 목표

### 변경 후 보장할 불변식

- `FamilyAccessService.verifyFamilyAccess(guardianId, targetMemberId)` 하나가 가족 접근 정책의 유일한 구현이다.
- `FamilyAccessAspect`는 파라미터 추출 + `FamilyAccessService` 위임만 수행한다. Repository를 직접 의존하지 않는다.
- AOP 경로(`@ValidateFamilyAccess`)와 직접 호출 경로가 동일한 예외·결과를 생성함을 단위 테스트로 검증한다.

### 기존에 유지할 동작

- `@ValidateFamilyAccess` 어노테이션 인터페이스 불변 (변경 없음)
- 기존 `FamilyAccessAspectTest` 4개 테스트 모두 통과
- `FamilyAccessAspect`가 적용된 모든 Controller 동작 불변

## 3. 변경 설계

### 3.1 변경 파일

| 파일 | 변경 유형 |
| --- | --- |
| `backend/widyu-api/src/main/java/com/widyu/member/application/FamilyAccessService.java` | 신규 |
| `backend/widyu-api/src/main/java/com/widyu/global/aspect/FamilyAccessAspect.java` | 수정 (위임으로 변경) |
| `backend/widyu-api/src/test/java/com/widyu/member/application/FamilyAccessServiceTest.java` | 신규 |
| `backend/widyu-api/src/test/java/com/widyu/global/aspect/FamilyAccessAspectTest.java` | 수정 (mock 대상 변경) |

### 3.2 FamilyAccessService (신규)

```java
package com.widyu.member.application;

@Service
@RequiredArgsConstructor
public class FamilyAccessService {

    private final MemberRepository memberRepository;
    private final FamilyMembershipRepository familyMembershipRepository;

    public void verifyFamilyAccess(Long guardianId, Long targetMemberId) {
        Member targetMember = memberRepository.findById(targetMemberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST,
                        "존재하지 않는 사용자입니다."));

        if (targetMember.getType() != MemberType.SENIOR) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "시니어의 리소스만 접근할 수 있습니다.");
        }

        if (targetMember.getSeniorProfile() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "시니어 프로필이 없습니다.");
        }

        boolean isFamily = familyMembershipRepository.existsByGuardianIdAndSeniorProfileId(
                guardianId,
                targetMember.getSeniorProfile().getId()
        );

        if (!isFamily) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "가족으로 연결된 시니어만 접근할 수 있습니다.");
        }
    }
}
```

**포함하지 않는 검증:** `currentMember.getType() != GUARDIAN` 확인은 `FamilyAccessAspect`에 남긴다. 이 검증은 HTTP 요청자 컨텍스트에서만 의미 있는 웹 어댑터 책임이다.

### 3.3 FamilyAccessAspect 변경 후

```java
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class FamilyAccessAspect {

    private final MemberUtil memberUtil;
    private final FamilyAccessService familyAccessService;  // ← MemberRepository, FamilyMembershipRepository 제거

    @Before("@annotation(validateFamilyAccess)")
    public void validateFamilyAccess(JoinPoint joinPoint, ValidateFamilyAccess validateFamilyAccess) {
        Member currentMember = memberUtil.getCurrentMember();
        String memberIdParamName = validateFamilyAccess.memberIdParam();

        Long targetMemberId = extractMemberId(joinPoint, memberIdParamName);

        if (targetMemberId == null || targetMemberId.equals(currentMember.getId())) {
            return;
        }

        if (currentMember.getType() != MemberType.GUARDIAN) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "보호자만 다른 사용자의 리소스에 접근할 수 있습니다.");
        }

        familyAccessService.verifyFamilyAccess(currentMember.getId(), targetMemberId);  // ← 위임

        log.debug("가족 관계 검증 성공: guardianId={}, seniorId={}",
                currentMember.getId(), targetMemberId);
    }

    private Long extractMemberId(JoinPoint joinPoint, String paramName) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Parameter[] parameters = method.getParameters();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].getName().equals(paramName)) {
                Object value = args[i];
                return value instanceof Long ? (Long) value : null;
            }
        }

        throw new BusinessException(
                ErrorCode.BAD_REQUEST,
                String.format("파라미터 '%s'를 찾을 수 없습니다.", paramName)
        );
    }
}
```

## 4. 테스트 시나리오 (TEST-004)

### FamilyAccessServiceTest (신규) — T1~T4

| 번호 | 시나리오 | 기대 결과 |
| --- | --- | --- |
| T1 | 가족으로 연결된 시니어에게 접근 | 예외 없음 (정상 통과) |
| T2 | 존재하지 않는 targetMemberId | `BAD_REQUEST` — "존재하지 않는 사용자입니다." |
| T3 | targetMember가 SENIOR가 아닌 경우 | `BAD_REQUEST` — "시니어의 리소스만 접근할 수 있습니다." |
| T4 | 가족 관계가 없는 시니어 접근 | `FORBIDDEN` — "가족으로 연결된 시니어만 접근할 수 있습니다." |

### FamilyAccessAspectTest 변경 — 기존 4개 테스트 유지

기존 테스트의 `@Mock MemberRepository` · `@Mock FamilyMembershipRepository` →
`@Mock FamilyAccessService`로 변경 (Aspect가 Service에 위임하므로).

기존 4개 테스트 시나리오:
- T5 (기존): 검증 대상 파라미터 없으면 `BAD_REQUEST`
- T6 (기존): SENIOR가 다른 회원 접근 시 `FORBIDDEN`
- T7 (기존): 대상 회원 없으면 `BAD_REQUEST` → `FamilyAccessService` stub 방식으로 재작성
- T8 (기존): 가족 연결 없으면 `FORBIDDEN` → `FamilyAccessService` stub 방식으로 재작성

**FamilyAccessAspectTest 변경 포인트:**
- T7, T8은 기존에 Repository를 stub했으나, 변경 후 `familyAccessService.verifyFamilyAccess()`가 예외를 throw하도록 stub한다.
- T5, T6은 `FamilyAccessService` 호출 전에 예외를 던지므로 stub 불필요.

## 5. 구현 순서

1. `FamilyAccessServiceTest.java` 작성 → RED 확인
2. `FamilyAccessAspectTest.java` 수정 (mock 대상 변경) → 기존 4개 테스트 RED 확인
3. `FamilyAccessService.java` 신규 작성
4. `FamilyAccessAspect.java` 수정 (Service 위임)
5. `./gradlew :backend:widyu-api:test` → GREEN 확인
6. `./gradlew build` → BUILD SUCCESSFUL

## 6. 완료 기준

- `FamilyAccessServiceTest` T1~T4 통과
- `FamilyAccessAspectTest` 4개(T5~T8) 통과
- `FamilyAccessAspect`에 `MemberRepository`, `FamilyMembershipRepository` 직접 의존 없음
- `./gradlew build` BUILD SUCCESSFUL

## 7. 제외 범위

- `MemberUtil` 패키지 이동 (ARCH-004): 별도 PR
- `GuardianHomeService.resolveSenior()` 검증 로직 교체 (ARCH-005 스코프): 별도 PR
- `FamilyConnectionService` GUARDIAN 타입 검증 통합: 별도 PR (행위자 가드는 각 서비스 책임)

## 8. 위험·롤백

- **위험:** FamilyAccessAspect 동작 변경으로 AOP 적용 Controller에 회귀 가능. 기존 테스트가 방어선.
- **롤백 가능성:** 높음. `FamilyAccessService` 추출을 되돌리고 Repository 의존을 복원하는 1단계 롤백.

## 9. Fable 자가 검증

- [x] 삼항 연산자 없음 (`hasLeader` 조건은 이번 PR 범위 외)
- [x] `FamilyAccessService`는 `member.application` 패키지 — widyu-api에 위치
- [x] `FamilyAccessAspect`에서 Repository 직접 의존 제거 확인
- [x] 기존 `FamilyAccessAspectTest` 4개 테스트 시나리오 유지 (mock 대상만 변경)
- [x] `verifyFamilyAccess` 파라미터: `(Long guardianId, Long targetMemberId)` — DTO 생성 없음
- [x] GUARDIAN 타입 검증은 Aspect에 남김 (HTTP 컨텍스트 책임)
