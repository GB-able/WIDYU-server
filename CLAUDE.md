# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> 도메인 상세 로직, 비즈니스 플로우, DB·외부 연동·보안 설계는 [backend/CLAUDE.md](backend/CLAUDE.md)를 참조하세요.
>
> 이슈·커밋·PR·리뷰·구현 작업은 항상 [.agents/skills](.agents/skills) 아래 해당 스킬(`issue`, `commit`, `pr`, `review`, `implement`)의 절차·컨벤션을 먼저 확인하고 그대로 따르세요. 예: 이슈 생성 → [.agents/skills/issue/SKILL.md](.agents/skills/issue/SKILL.md), 커밋 → [.agents/skills/commit/SKILL.md](.agents/skills/commit/SKILL.md), PR → [.agents/skills/pr/SKILL.md](.agents/skills/pr/SKILL.md).

## Project Overview

WIDYU는 시니어(부모)와 보호자(자녀·가족)가 사진·영상을 공유하는 Spring Boot 플랫폼입니다.
포인트 기반 프리미엄 콘텐츠, WebSocket 실시간 위치 추적, 건강 목표 관리 기능을 포함합니다.

**Tech Stack**: Java 21, Spring Boot 3.3.5, MySQL, Redis, WebSocket (STOMP), QueryDSL, JPA/Hibernate

## Development Commands

### Build & Run
```bash
./gradlew build
./gradlew bootRun
./gradlew bootRun --args='--spring.profiles.active=local'
./gradlew :backend:widyu-api:build
```

### Testing
```bash
./gradlew test
./gradlew :backend:widyu-api:test
./gradlew :backend:widyu-domain:test
./gradlew test --tests "com.widyu.global.util.PhoneNumberUtilTest"
./gradlew test --tests "*integration*"
```

### Development Tools
```bash
./gradlew clean
./gradlew compileJava   # QueryDSL Q-class 재생성 (엔티티 변경 후 필수)
./gradlew dependencies
```

### Docker Deployment
```bash
# 개발 환경
./scripts/docker/dev-up.sh
./scripts/docker/dev-down.sh
./scripts/docker/logs.sh dev
./scripts/docker/logs.sh dev widyu-api

# 운영 환경
./scripts/docker/prod-up.sh
./scripts/docker/prod-down.sh
./scripts/docker/logs.sh prod

# 수동 실행
docker compose up
docker compose -f docker-compose.yml -f docker-compose.dev.yml up
docker compose -f docker-compose.yml -f docker-compose.prod.yml up

# AI 심박수 서비스
docker pull ryuchanghoon/widyu-ai-ver7:latest
docker run -p 5000:5000 ryuchanghoon/widyu-ai-ver7:latest
```

### CI/CD Pipeline (GitHub Actions)
- **CI** (`.github/workflows/ci.yml`): PR to develop, push to feature/fix/refactor 브랜치에서 트리거 — `widyu-api` 빌드·테스트
- **CD** (`.github/workflows/deploy-dev.yml`): develop 브랜치 push 시 트리거
  - Docker Hub 레지스트리 캐시(`cache-from`/`cache-to`)로 빌드 후 `latest`·`sha` 태그 push
  - EC2에 compose 파일 `rsync` 후 `--no-deps --force-recreate widyu-api` 배포 (DB·Redis 유지)

## Admin Dashboard (admin/)

`admin/` 디렉터리는 React + TypeScript 기반 운영 모니터링 대시보드입니다. Spring Boot 백엔드와 별개로 동작하는 SPA입니다.

**Tech Stack**: React 19, TypeScript, Vite, Tailwind CSS, React Query, React Router, Recharts, Zustand

**Pages**: Dashboard (KPI·경고 카드·주간 추이 차트), Members, Families, Albums, Payments, Notifications, Logs, DevTools (개발 환경 전용)

**Dev**:
```bash
cd admin
npm install
npm run dev   # localhost:5173
npm run build # dist/ 생성
```

**인증**: authStore(Zustand)에 JWT 저장, PrivateRoute로 보호. 백엔드 `/api/admin/login` 연동.

## Application Architecture

### Multi-Module Structure
- **`widyu-api`**: 실행 가능한 JAR — 컨트롤러, 서비스, 리포지토리, 설정. 진입점: `com.widyu.WidyuApiApplication`
- **`widyu-domain`**: 라이브러리 JAR (`bootJar` 비활성) — JPA 엔티티(`@Entity`, `@RedisHash`), QueryDSL Q-클래스 생성 위치. 리포지토리 없음

**핵심 규칙**: 엔티티는 `widyu-domain`, 리포지토리는 `widyu-api`.

### DDD Layered Structure
```
{domain}/
├── controller/         # REST 엔드포인트
│   └── docs/          # Swagger 문서 인터페이스 (컨트롤러와 분리)
├── application/       # 비즈니스 로직
│   ├── *Service.java
│   ├── *Facade.java   # 여러 서비스를 조합하는 경우
│   └── strategy/
├── repository/
├── dto/
│   ├── request/
│   └── response/
└── validator/
```

### Key Architectural Patterns
- **Facade** — 여러 서비스를 조합하는 복잡한 오퍼레이션 (`AlbumFacade`, `HealthScheduleFacade`)
- **Strategy + Factory** — OAuth 제공자별 로그인 처리 (`SocialLoginStrategyFactory`)
- **AOP** — `@ValidateFamilyAccess`로 가디언-시니어 접근 권한 자동 검증
- **Event-Driven** — `@EventListener`로 앨범 이벤트 → FCM 알림 (도메인 간 결합 제거)
- **QueryDSL** — 복잡한 조건 쿼리에 사용, `./gradlew compileJava`로 Q-클래스 재생성
- **WebSocket STOMP** — JWT 핸드셰이크 인터셉터로 인증, `SimpMessagingTemplate`으로 브로드캐스트

## AI 하네스 워크플로우

Java 파일을 수정할 때마다 `on-file-edit.sh` 훅이 자동 실행되어 규칙을 검사합니다.
작업 완료 전 다음 순서를 따르세요:

```
1. 관련 도메인 문서 확인 (backend/CLAUDE.md → 해당 도메인 섹션)
2. 변경 계획 작성 (어떤 파일, 어떤 이유)
3. 코드 수정 (훅이 자동으로 규칙 검사)
4. 테스트 실행: bash scripts/harness/run-module-tests.sh
5. 엔티티 변경 시 QueryDSL 재생성: ./gradlew compileJava
```

**자동 검사 규칙** (`scripts/harness/validate-java-rules.sh`):
- 삼항 연산자 사용 → if/else로 수정
- Service/Facade에서 DTO 직접 생성(`new XxxResponse(`) → `from()`/`of()` 사용
- Controller에서 Repository 직접 import → Service 계층 거쳐야 함
- widyu-api에 `@Entity` → widyu-domain으로 이동
- widyu-domain에 Repository → widyu-api로 이동
- `@Async` 메서드에 `@Transactional` 누락 경고

## 코드 작성 원칙

1. **삼항 연산자 금지** — 조건 분기는 `if/else` 또는 early return으로 작성한다
2. **DTO 팩토리 메서드** — 서비스에서 `new DTO(...)` 직접 생성 금지. DTO에 `from()` / `of()` 정적 팩토리 메서드를 두고 서비스에서 호출한다
3. **Early return** — 중첩을 줄이기 위해 조건이 맞지 않으면 일찍 반환한다
4. **단일 책임 메서드** — 한 메서드는 한 가지 일만 한다. 메서드가 길어지면 책임을 분리할 신호다
5. **의미 있는 이름** — 축약어 없이 의도가 드러나는 이름을 사용한다

## PR 작성 규칙

### 기본 원칙
- **한 PR = 한 목적** — 기능 추가, 리팩토링, 버그 수정을 하나의 PR에 섞지 않는다
- **리뷰 질문이 1개** — "이 설계가 맞나?" 처럼 질문이 하나여야 범위가 적절한 것
- **파일 3~8개** — 15개 넘어가면 쪼개는 것을 먼저 검토한다
- **200~400 lines changed** — 이를 넘기면 분리를 고려한다

### 리팩토링과 기능 추가 분리
1. 리팩토링 PR 먼저
2. 기능 추가 PR 그 다음

### AI에게 작업 지시할 때 범위 명시 방법
작업 시작 전 아래 형식으로 범위를 먼저 규정한다.
```
이번 PR 범위:
- 목적: [한 줄 설명]
- 건드릴 파일: [명시]
- 건드리지 말 것: [명시]
- 리팩토링 금지 / 기능 추가만 등
```

### PR 본문 필수 항목
- **왜 바꾸는지** — 변경의 배경과 이유
- **무엇만 바꿨는지** — 이번 PR의 범위
- **의도적으로 안 한 것** — 범위 밖으로 뺀 항목 명시
- **리뷰어가 집중할 포인트** — 1개로 좁힌다

## 테스트 코드 작성 원칙

**프레임워크**: JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`), H2 in-memory DB (`application-test.yml`)

### 왜 테스트 코드를 작성하는가

1. **디버깅 비용 절감** — 문제 범위를 unit/integration/e2e로 빠르게 좁혀 디버깅 시간을 줄인다
2. **회귀 버그 관리** — 기능 수정·리팩터링 후 기존 기능이 정상 동작하는지 검증한다
3. **살아있는 문서** — 테스트 코드는 코드와 함께 존재하므로 가장 신뢰할 수 있는 문서다
4. **설계 품질 지표** — 테스트하기 어려운 코드는 결합도가 높은 코드다. 테스트 난이도로 설계를 판단한다
5. **배포 자동화** — CI 단계에서 테스트를 수행해 문제 있는 코드의 배포를 차단한다

### 테스트 작성 규칙

**1. DRY보다 DAMP하게 작성한다**

- `@BeforeEach`로 상태를 공유하지 않는다 — 각 테스트는 독립적으로 동작해야 한다
- 반복되는 객체 생성은 Fixture 클래스로 분리한다

```java
// ❌ beforeEach로 상태 공유
@BeforeEach
void setUp() {
    jobApplicant = new JobApplicant("haru", Status.IN_PROGRESS);
}

// ✅ Fixture로 독립성 유지
JobApplicant applicant = JobApplicantFixture.create(Status.IN_PROGRESS);
```

**2. 구현이 아닌 결과를 검증한다**

- 내부 메서드 호출 여부(`verify`)가 아니라 상태 변화를 검증한다

```java
// ❌ 구현 검증
verify(applicant).validateIsNotFail();

// ✅ 결과 검증
assertEquals(Status.PASS, applicant.getStatus());
```

**3. AAA(given/when/then) 패턴으로 작성한다**

```java
@Test
@DisplayName("좋아요 수를 업데이트하면 기존 수에 더해진다")
void 좋아요수_업데이트() {
    // given
    Post post = new Post("title", 3);

    // when
    post.updateLike(300);

    // then
    assertEquals(303, post.getLike());
}
```

**4. 테스트 명세에 비즈니스 행위를 담는다**

- 메서드명: 한글 언더스코어 구분 (예: `이름_수정`, `프로필_이미지_수정_기존이미지_삭제`)
- `@DisplayName`: `<행위>하면/할 때 <결과>한다/반환한다/예외가 발생한다` 형식
  - 피할 것: "성공", "실패", "테스트" 접미사

```java
// ❌
void 관리자생성후조회()

// ✅
void 관리자_정보로_가입한다()
```

**5. BDDMockito를 사용한다**

```java
// ✅
given(repository.findById(1L)).willReturn(Optional.of(entity));

// ❌
when(repository.findById(1L)).thenReturn(Optional.of(entity));
```

**6. 예외 테스트**

```java
assertThatThrownBy(() -> service.someMethod(invalidArg))
        .isInstanceOf(BusinessException.class);
```

### 테스트 구분

| 종류 | 대상 |
|------|------|
| Unit | 도메인 모델, 비즈니스 로직 |
| Integration | 주요 흐름, 외부 의존성(DB 등) |
| E2E | 사용자 흐름 전체 |

## Development Guidelines

1. **Module Placement**: 엔티티 → `widyu-domain`, 리포지토리·서비스·컨트롤러 → `widyu-api`

2. **QueryDSL**: 엔티티 변경 후 `./gradlew compileJava`로 Q-클래스 재생성

3. **Swagger**: `controller/docs/`에 별도 `Docs` 인터페이스 작성 — 컨트롤러는 깔끔하게 유지

4. **복잡한 오퍼레이션**: 여러 서비스를 조합할 때 Facade 패턴 사용

5. **크로스 도메인 로직**: `@EventListener` 이벤트 방식으로 도메인 간 결합 제거

6. **인가**: `@ValidateFamilyAccess`로 가디언-시니어 접근 제어 (FamilyMembership ↔ SeniorProfile.family 크로스 조인으로 자동 검증)

7. **임시 데이터**: Redis `@RedisHash` + `@TimeToLive` 사용 (인증코드, 임시토큰, OAuth state 등)

8. **Strategy 패턴**: 플러그인 가능한 구현에 적용, Factory로 런타임 선택

9. **WebSocket 실시간 기능**: JWT 핸드셰이크 인터셉터로 인증, `SimpMessagingTemplate`으로 브로드캐스트, 위치 데이터는 Redis 저장

10. **Async 비동기 처리**:
    - `@Async` 메서드는 **별도 빈**에 위치 (self-invocation 프록시 우회 방지)
    - `MultipartFile`은 요청 종료 후 삭제되므로 `File`로 변환 후 async 스레드에 전달
    - `@Async` 메서드에 `@Transactional` 필요 (새 스레드 = 새 트랜잭션)
    - 임시 파일은 `finally`에서 삭제

11. **MySQL ENUM**: `ddl-auto: update`는 기존 ENUM 컬럼에 새 값을 추가하지 않음
    → 수동 실행 필요: `ALTER TABLE <table> MODIFY COLUMN <col> ENUM('A','B','NEW') NOT NULL`
