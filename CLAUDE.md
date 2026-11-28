# CLAUDE.md

WIDYU는 시니어(부모)와 보호자(자녀·가족)가 사진·영상을 공유하는 Spring Boot 플랫폼입니다. 포인트, 실시간 위치(WebSocket STOMP), 건강 목표를 포함합니다. Java 21, Spring Boot 3.3.5, MySQL, Redis.

- 도메인 불변식, 테스트 작성 규칙은 [backend/CLAUDE.md](backend/CLAUDE.md), Admin은 [admin/CLAUDE.md](admin/CLAUDE.md)를 봅니다. 설계 근거는 `docs/lld`, `docs/adr`에 있습니다.
- 이슈, 커밋, PR, 리뷰는 [.agents/skills](.agents/skills)의 해당 `SKILL.md` 절차를 따릅니다.

## 명령

```bash
./gradlew compileJava                                    # 컴파일만
./gradlew :backend:widyu-api:test --tests "<클래스>"      # 단일 테스트
bash scripts/harness/run-module-tests.sh                 # 모듈 테스트
./gradlew bootRun --args='--spring.profiles.active=local'
```

## 구조

- `backend/widyu-domain`: JPA 엔티티(`@Entity`, `@RedisHash`)만 둡니다. 리포지토리는 `backend/widyu-api`에 둡니다.
- 도메인 패키지는 `controller`(Swagger 문서는 `controller/docs`), `application`, `repository`, `dto/request`, `dto/response`, `validator`로 나눕니다.
- 보호자가 시니어 데이터에 접근하는 엔드포인트는 `@ValidateFamilyAccess`로 가족 관계를 검증합니다(ADR-0002).
- 조회는 Spring Data JPA 메서드 이름 쿼리, 안 되면 `@Query` JPQL을 씁니다. QueryDSL은 쓰지 않습니다(ADR-0044).

## 코드 규칙

CI가 변경된 줄을 `scripts/harness/validate-java-rules.sh`로 검사합니다.

1. 삼항 연산자 금지. `if/else` 또는 early return으로 씁니다.
2. 서비스에서 DTO를 `new`로 만들지 않고 DTO의 `from()`/`of()` 정적 팩토리를 씁니다. 중첩 타입(`new XResponse.Item(...)`)도 같습니다.
3. Controller는 Repository를 직접 쓰지 않고 Service를 거칩니다.
4. `@Async` 메서드에는 `@Transactional`을 붙입니다. 새 스레드는 호출자 트랜잭션을 받지 못합니다.

## Codex 검수

자동 검수 훅과 push·PR 게이트는 없습니다(LLD-0193). 인증, 결제, 가족 접근처럼 실패 비용이 큰 변경은 커밋 뒤 `bash scripts/harness/codex-review.sh --base "$(git merge-base upstream/develop HEAD)"`로 직접 검수를 받습니다.

# Compact instructions

압축할 때 현재 작업의 LLD, ADR 인수조건, 미해결 Codex BLOCKER, 확정한 설계와 근거, 변경한 파일과 남은 작업을 보존합니다. 압축 후 백엔드 코드를 계속 다루면 `backend/CLAUDE.md`를 다시 읽습니다.
