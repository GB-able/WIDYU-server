# ADR-0044: QueryDSL을 제거하고 조회를 Spring Data JPA로 통일한다

> Architecture Decision Record. 하나의 중요한 의사결정과 그 이유를 기록한다.

| 항목 | 값 |
| --- | --- |
| 상태 | Accepted |
| 날짜 | 2026-11-12 |
| 관련 | #1036, ADR-0001(멀티모듈), ADR-0003(DB 엔티티 설계) |

## 맥락 (Context)

QueryDSL은 `widyu-api`와 `widyu-domain` 두 모듈에 의존성(`querydsl-jpa 5.0.0`)과 어노테이션 프로세서(`querydsl-apt`, jakarta annotation, persistence API)로 설정돼 있었다. ADR-0001은 Q 클래스 생성 위치를 모듈 분리 근거 중 하나로 들었다.

2026-11-12 기준 실제 사용처는 소셜 로그인 회원 조회 한 곳(`MemberRepositoryImpl`)뿐이었다. 이 클래스의 세 메서드는 모두 조건이 고정된 조회다.

| 메서드 | 조회 |
| --- | --- |
| `findByProviderAndOauthId` | 제공자와 oauthId로 회원 조회, 일치한 소셜 계정을 fetch join, distinct |
| `findMemberIdByProviderAndOauthId` | 같은 조건으로 회원 id만 조회 |
| `findWithAllAccountsById` | id로 회원 조회, 모든 소셜 계정을 left fetch join, distinct |

나머지 저장소는 Spring Data 메서드 이름 쿼리와 `@Query` JPQL(main 코드 39개 파일)을 쓰고, 조건을 조합하는 동적 쿼리는 없다.

한 곳을 위해 QueryDSL을 유지하는 비용은 다음과 같았다.

- 엔티티를 바꾸면 `./gradlew compileJava`로 Q 클래스를 다시 만들어야 한다는 규칙이 지침과 스킬 여러 곳에 퍼져 있었다.
- 두 모듈이 어노테이션 프로세서를 따로 설정했다.
- `JPAQueryFactory` 빈 때문에 `@DataJpaTest` 40여 개가 `@MockBean JPAQueryFactory`를 선언해야 했다.
- 5.0.0은 원본 저장소의 유지보수가 거의 멈춘 버전이다.
- 조회 방식이 둘이면 사람과 AI 에이전트가 새 조회를 어느 방식으로 쓸지 매번 판단해야 한다. 저장소에서 지배적인 방식 하나만 있으면 기존 코드를 보고 그대로 따를 수 있다.

## 결정 (Decision)

QueryDSL을 제거하고 저장소 조회를 Spring Data JPA(메서드 이름 쿼리, `@Query` JPQL)로 통일한다.

- 세 조회를 `MemberRepository`의 `@Query` JPQL로 옮긴다. 메서드 이름, 시그니처, 반환 타입과 fetch join, left join, distinct 동작을 유지한다.
- `MemberRepositoryImpl`, `MemberRepositoryCustom`, `QuerydslConfig`를 삭제하고 두 모듈의 QueryDSL 의존성과 어노테이션 프로세서를 뺀다.
- 테스트의 `@MockBean JPAQueryFactory`를 지운다.
- 지침에서 Q 클래스 재생성 규칙을 지운다. 하네스의 `compileJava` 실행은 일반 컴파일 검사로 유지한다.

새 조회는 메서드 이름 쿼리 → `@Query` JPQL → 네이티브 쿼리 순으로 고른다. 조건 조합이 많은 동적 쿼리가 실제로 필요해지면 그때 Spring Data `Specification`이나 Criteria API, 또는 QueryDSL 재도입을 별도 ADR로 결정한다.

## 고려한 대안 (Considered Options)

1. **QueryDSL 유지** — 나중에 동적 쿼리가 생기면 바로 쓸 수 있다 / 쓰지 않는 기능을 위해 위 비용을 계속 낸다. 동적 쿼리 요구는 아직 없다.
2. **OpenFeign 포크(`io.github.openfeign.querydsl`)로 교체** — 유지보수 문제는 줄어든다 / 사용처가 한 곳이라 Q 클래스 규칙과 이중 조회 방식 문제는 그대로 남는다.
3. **제거하고 `@Query` JPQL로 통일 (채택)** — 의존성, 어노테이션 프로세서, 지침 규칙이 함께 사라지고 조회 방식이 하나가 된다 / JPQL 문자열은 컴파일 시점 타입 검사가 없다. Spring Data가 애플리케이션 시작 시 `@Query`를 파싱하므로 오타는 컨텍스트 로딩 단계(저장소 테스트 포함)에서 드러난다.

## 결과 (Consequences)

### 긍정
- 엔티티 변경 후 Q 클래스 재생성을 잊어 생기는 빌드 불일치가 사라진다.
- 두 모듈의 어노테이션 프로세서가 Lombok 하나로 줄어든다.
- `@DataJpaTest`가 `JPAQueryFactory` 목 없이 뜬다.
- 새 조회를 쓸 때 따를 방식이 하나다.

### 부정 / 트레이드오프
- 동적 쿼리가 필요해지면 도구를 다시 골라야 한다.
- JPQL 오타는 컴파일이 아니라 컨텍스트 로딩에서 잡힌다. 새 `@Query`는 저장소 테스트로 확인한다.
- 이 변경 이전에 `JPAQueryFactory` 목을 추가한 브랜치는 머지 후 컴파일되지 않으므로 해당 줄을 지워야 한다.

## 후속 / 미결정
- ADR-0001의 "Q 클래스 생성 위치 분리" 근거는 더 이상 유효하지 않다. 모듈 분리의 나머지 근거(엔티티와 API 계층 경계)는 그대로다.
