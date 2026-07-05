# ADR-0001: widyu-api + widyu-domain 멀티모듈 구조 채택

> Architecture Decision Record. 하나의 중요한 의사결정과 그 이유를 기록한다.

| 항목 | 값 |
| --- | --- |
| 상태 | Accepted |
| 날짜 | 2025-08-01 |
| 관련 | - |

## 맥락 (Context)

JPA 엔티티와 비즈니스 로직(서비스·리포지토리·컨트롤러)을 같은 모듈에 두면, 도메인 모델 변경이 API 계층 컴파일에 영향을 주고 경계가 흐려진다. 또한 QueryDSL Q-클래스 생성 위치를 분리하기 위해 모듈 분리가 필요하다.

## 결정 (Decision)

프로젝트를 두 Gradle 서브모듈로 분리한다.

- `widyu-domain`: JPA 엔티티(`@Entity`, `@RedisHash`), QueryDSL Q-클래스 생성 대상. `bootJar` 비활성.
- `widyu-api`: 컨트롤러, 서비스, 리포지토리, 설정. 실행 가능한 JAR. `widyu-domain`에 의존.

핵심 규칙: 엔티티는 `widyu-domain`에만, 리포지토리·서비스·컨트롤러는 `widyu-api`에만 위치한다.

## 고려한 대안 (Considered Options)

1. **단일 모듈** — 구조 단순 / 도메인·API 경계 없음, 엔티티가 어디서나 접근 가능
2. **멀티모듈 (채택)** — 경계 명확, QueryDSL 분리 / 초기 설정 복잡도 증가

## 결과 (Consequences)

### 긍정
- 엔티티 변경 영향 범위가 `widyu-domain`으로 한정됨
- `validate-java-rules.sh`로 모듈 배치 규칙을 자동 검증 가능
- QueryDSL Q-클래스 재생성이 `widyu-domain` 컴파일에만 해당

### 부정 / 트레이드오프
- 엔티티 변경 후 `./gradlew compileJava` 실행을 빠뜨리면 Q-클래스가 구버전으로 남음
- 모듈 간 의존 방향 실수 시 컴파일 오류 발생

## 후속 / 미결정
- 도메인이 더 늘어날 경우 도메인별 서브모듈 분리 여부는 팀 논의 후 별도 ADR로 결정
