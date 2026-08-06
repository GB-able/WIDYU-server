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
- PR 제목과 본문은 한글 존댓말로 작성한다.
- 기본 Assignee: `dongkyun0713`.
- 변경 성격에 맞는 label을 확인하고 가능하면 함께 지정한다.

## PR 범위 원칙

- **한 PR = 한 목적** — 기능 추가·리팩토링·버그 수정을 한 PR에 섞지 않는다.
- **리뷰 질문 1개** — "이 설계가 맞나?"처럼 질문이 하나로 좁혀지면 범위가 적절한 것.
- **파일 3~8개 / 200~400 lines** — 넘으면 분리를 먼저 검토한다 (파일 15개 초과는 필수 분리 검토).
- **리팩토링 먼저, 기능 추가는 그 다음** — 두 PR로 나눈다.
- **작업 지시 시 범위 규정** — 목적 / 건드릴 파일 / 건드리지 말 것 / 리팩토링 금지 여부를 먼저 명시한다.
- **본문 필수 항목**: 왜 바꾸는지 · 무엇만 바꿨는지 · 의도적으로 안 한 것 · 리뷰어 집중 포인트(1개).

## 사전 확인

- 현재 브랜치가 `develop` 또는 `main`이면 새 브랜치 필요 여부를 확인한다.
- 엔티티 변경이 있으면 `./gradlew compileJava` 여부 확인.
- MySQL ENUM 변경이 있으면 PR 비고에 ALTER TABLE 명령 명시.

## 절차

1. `git status --short --branch`, `git log --oneline -5` 확인.
2. 관련 이슈 `gh issue view <N>` 읽기.
3. 관련 LLD `docs/lld/LLD-XXXX.md` 읽기.
4. `git diff develop...HEAD --stat`으로 변경 범위 파악.
5. 아래 템플릿으로 본문 작성. 본문은 존댓말 종결형(`합니다`, `습니다`, `확인했습니다`)을 사용한다.
6. `gh pr create --base develop --head <branch> --title "<title>" --body-file <tmpfile> --assignee dongkyun0713`.
7. `gh label list --limit 50`로 사용 가능한 label을 확인한다.
8. 변경 성격에 맞는 label이 있으면 `gh pr edit <PR> --add-label "<label>"`로 추가한다.
9. `gh pr view <PR> --json assignees,labels`로 assignee와 label 반영을 확인한다.

### Label 선택 기준

| 변경 성격 | label |
| --- | --- |
| 문서/가이드/스킬 문서 | `Docs` |
| 개발 환경, CI, 설정, 하네스 | `Setting` |
| 테스트 코드/검증 보강 | `Test` |
| 기능 구현 | `Feature` |
| 버그 수정 | `bug` |
| 구조 개선 | `Refactor` |
| 배포/인프라 | `Devops` |

## PR 본문 템플릿

본문은 존댓말 종결형으로 작성한다.

```markdown
## 개요
<LLD 기준으로 이 PR이 해결하는 문제와 범위를 존댓말로 작성합니다.>

## 관련 문서
- LLD: docs/lld/<경로> (없으면 -)
- ADR: docs/adr/<경로> (없으면 -)

## 관련 이슈
Closes #<N>

## 변경 사항
- 변경 모듈: widyu-api / widyu-domain
<diff 기반 사실만 존댓말 bullet로 작성합니다.>

## 테스트
- `./gradlew :backend:widyu-api:test`: 통과/실패
- `./gradlew :backend:widyu-domain:test`: 통과/실패 (domain 변경 시)

## 체크리스트
- [ ] 엔티티 변경 시 `./gradlew compileJava` 실행
- [ ] MySQL ENUM 변경 시 ALTER TABLE 명시
- [ ] `@Async` 메서드에 `@Transactional` 확인
- [ ] LLD 인수조건 전체 충족

## 리뷰 포인트
<리뷰어가 집중할 질문 1개로 좁혀 작성합니다.>

## Open Questions (LLD 미결정 사항)
<LLD의 Open Questions를 그대로 옮깁니다. 없으면 "없습니다.">

## 비고
<배포 영향, 후속 작업>
```

## 하지 말 것

- LLD에 없는 설계를 본문에 서술하기.
- develop 대신 main으로 PR 올리기.
- 영어 제목으로 PR 만들기.
