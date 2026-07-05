---
name: pr
description: WIDYU-server에서 GitHub PR을 생성하거나 갱신한다.
---

# pr

WIDYU-server의 Pull Request를 생성하거나 갱신한다.
PR 본문은 LLD를 오라클로 삼아 작성한다. LLD에 없는 내용은 쓰지 않는다.

## 핵심 원칙

- base 브랜치는 `develop`.
- LLD가 있으면 반드시 링크하고 인수조건 기준으로 작성한다.
- Open Questions는 LLD의 미결정 사항을 그대로 옮긴다.
- 기본 Assignee: `dongkyun0713`.

## 사전 확인

- 현재 브랜치가 `develop` 또는 `main`이면 새 브랜치 필요 여부를 확인한다.
- 엔티티 변경이 있으면 `./gradlew compileJava` 여부 확인.
- MySQL ENUM 변경이 있으면 PR 비고에 ALTER TABLE 명령 명시.

## 절차

1. `git status --short --branch`, `git log --oneline -5` 확인.
2. 관련 이슈 `gh issue view <N>` 읽기.
3. 관련 LLD `docs/lld/LLD-XXXX.md` 읽기.
4. `git diff develop...HEAD --stat`으로 변경 범위 파악.
5. 아래 템플릿으로 본문 작성.
6. `gh pr create --base develop --head <branch> --title "<title>" --body-file <tmpfile> --assignee dongkyun0713`.
7. 필요한 라벨이 정해져 있으면 `gh pr edit <PR> --add-label "<label>"`로 추가한다.
8. `gh pr view <PR> --json assignees,labels`로 반영 확인.

## PR 본문 템플릿

```markdown
## 개요
<LLD 기준으로 이 PR이 해결하는 문제와 범위>

## 관련 문서
- LLD: docs/lld/<경로> (없으면 -)
- ADR: docs/adr/<경로> (없으면 -)

## 관련 이슈
Closes #<N>

## 변경 사항
- 변경 모듈: widyu-api / widyu-domain
<diff 기반 사실만 bullet>

## 테스트
- `./gradlew :backend:widyu-api:test`: 통과/실패
- `./gradlew :backend:widyu-domain:test`: 통과/실패 (domain 변경 시)

## 체크리스트
- [ ] 엔티티 변경 시 `./gradlew compileJava` 실행
- [ ] MySQL ENUM 변경 시 ALTER TABLE 명시
- [ ] `@Async` 메서드에 `@Transactional` 확인
- [ ] LLD 인수조건 전체 충족

## Open Questions (LLD 미결정 사항)
<LLD의 Open Questions를 그대로 옮김. 없으면 "없음">

## 비고
<배포 영향, 후속 작업>
```

## 하지 말 것

- LLD에 없는 설계를 본문에 서술하기.
- develop 대신 main으로 PR 올리기.
- 영어 제목으로 PR 만들기.
