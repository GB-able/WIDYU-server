# Repository Guidelines

## Project Overview

WIDYU는 시니어(부모)와 보호자(자녀·가족)가 사진·영상을 공유하는 Spring Boot 플랫폼.
포인트 기반 프리미엄 콘텐츠, WebSocket 실시간 위치 추적, 건강 목표 관리 포함.

## 하네스 엔지니어링 워크플로

이 프로젝트는 하네스 엔지니어링 방식으로 개발한다.
공통 상세 규칙은 `CLAUDE.md`와 `backend/CLAUDE.md`를 기준으로 삼고, 이 문서는 Codex가 따라야 할 추가 워크플로만 정리한다.

```
Team Discussion → ADR → LLD → Test Scenario → Code Generation → Human Review → PR/CI/Deploy
```

- 기술적 선택은 ADR(`docs/adr/`)에 남긴다. 선택한 방법 + 대안 + 트레이드오프 + 후속 리스크 포함.
- ADR이 방향을 정하면 LLD(`docs/lld/`)에서 구현 단위를 설계한다.
- LLD의 인수조건이 테스트 시나리오가 된다. 이 시나리오 기준으로 코드를 생성한다.
- LLD는 PR 본문의 오라클이다. LLD에 없는 내용은 PR 본문에 쓰지 않는다.

## Multi-Module Structure

- `widyu-api`: 컨트롤러, 서비스, 리포지토리, 설정 (`com.widyu.WidyuApiApplication`)
- `widyu-domain`: JPA 엔티티, QueryDSL Q-클래스 (bootJar 비활성)

핵심 규칙: 엔티티 → `widyu-domain`, 리포지토리/서비스/컨트롤러 → `widyu-api`

## Build & Test Commands

- `./gradlew :backend:widyu-api:test`: API 모듈 테스트
- `./gradlew :backend:widyu-domain:test`: 도메인 모듈 테스트
- `./gradlew compileJava`: QueryDSL Q-클래스 재생성 (엔티티 변경 후 필수)
- `./gradlew bootRun --args='--spring.profiles.active=local'`: 로컬 실행

## Coding Conventions

- 삼항 연산자 금지 — if/else 또는 early return
- Service/Facade에서 `new XxxResponse(` 직접 생성 금지 → `from()`/`of()` 팩토리 사용
- Controller에서 Repository 직접 import 금지
- `@Async` 메서드에 `@Transactional` 필요
- Facade 패턴: 여러 서비스 조합 시 사용

## Testing

JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`), H2 in-memory.
BDDMockito(`given/willReturn`), AAA(given/when/then) 패턴.
테스트 메서드명은 한글 언더스코어(예: `앨범_생성_시_FCM_전송`).
상태 검증을 우선한다. 외부 호출, 이벤트 발행, 삭제 같은 side effect는 `verify`로 검증할 수 있다.

## Agent-Specific Instructions

Codex 워크플로 스킬은 `.agents/skills`에 있다.
새 작업 시작 → issue 스킬, 코드 완료 → commit 스킬, 머지 준비 → pr 스킬.
Co-author 트레일러: `Co-Authored-By: Codex <codex@openai.com>`.

## Security

시크릿 커밋 금지.
환경변수: `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `FCM_CREDENTIALS_PATH`.
