---
name: pr
description: WIDYU-server에서 GitHub PR을 생성하거나 갱신한다.
---

# pr

WIDYU-server의 Pull Request를 생성하거나 갱신한다.
PR 설계 설명은 LLD를 오라클로 삼는다. diff와 실행한 검증 결과도 사실대로 기록한다. LLD N/A는 docs/lld/README.md의 기준과 사유를 따른다.

## 핵심 원칙

- 사용자 요청에 PR 생성·갱신이 포함되면 수행하며, 이미 받은 권한을 다시 묻지 않는다.
- base는 `GB-able/WIDYU-server`의 `develop`, head는 확인된 작업 fork/브랜치다. gh 명령에는 `--repo GB-able/WIDYU-server`를 명시한다.
- LLD가 있으면 반드시 링크하고 인수조건 기준으로 작성한다.
- LLD에 있는 내용(조건표, 측정 상세, 미결정 사항 전체)은 본문에 다시 쓰지 않고 링크로 대신한다. Open Questions는 이 PR 리뷰어가 답해야 하는 것만 옮긴다.
- PR 제목과 본문은 한글 존댓말로 작성한다.
- 문안은 [write 스킬](../write/SKILL.md)을 적용해 문제·변경 동작·검증 근거를 연결한다.
- 기본 Assignee: `dongkyun0713`.
- 변경 성격에 맞는 label을 확인하고 가능하면 함께 지정한다.

## PR 범위 원칙

- **한 PR = 한 목적** — 기능 추가·리팩토링·버그 수정을 한 PR에 섞지 않는다.
- **리뷰 질문 1개** — "이 설계가 맞나?"처럼 질문이 하나로 좁혀지면 범위가 적절한 것.
- **파일 3~8개 / 200~400 lines** — 넘으면 분리를 먼저 검토한다 (파일 15개 초과는 필수 분리 검토).
- **리팩토링 먼저, 기능 추가는 그 다음** — 두 PR로 나눈다.
- **작업 지시 시 범위 규정** — 목적 / 건드릴 파일 / 건드리지 말 것 / 리팩토링 금지 여부를 먼저 명시한다.
- **본문 필수 항목**: 해결하는 문제·최종 변경 동작·실제 검증 결과·리뷰어 집중 포인트(1개). 제외 범위는 오해 가능성이 있을 때만 적는다.

## 본문 작성 규칙

PR 본문은 변경 기록이 아니라 리뷰어가 판단하는 데 쓰는 문서다.

- **이유 먼저, 구현은 나중** — 무엇이 문제였고 어떤 동작으로 바뀌는지를 쓴 뒤에 구현을 쓴다.
- **동작 단위로 묶는다** — 파일, 클래스, 메서드별로 나열하지 않는다. "이전 → 이후 동작, 이유"로 쓰고 클래스와 설정 키는 리뷰어가 코드를 찾는 데 필요할 때만 괄호로 붙인다.
- **diff와 LLD가 이미 보여주는 것은 반복하지 않는다** — 본문에는 diff만 봐서는 알 수 없는 배경, 선택 이유, 위험을 쓴다.
- **한 화면 기준** — `한눈에 보기`부터 `리뷰 포인트`까지 스크롤 한두 번 안에 읽히게 쓴다. 측정표, 보수적으로 처리한 항목, 폐기한 설계처럼 일부 리뷰어만 필요한 내용은 `<details>`로 접는다.
- **검증은 실행한 것만** — 로컬 테스트, 개발 서버 확인, 운영 미확인을 구분하고 미실행은 미실행으로 쓴다. 수치, 동기, 결과를 지어내지 않는다.
- **해당할 때만 쓰는 절** — 배포 영향, 클라이언트 영향, Open Questions는 해당 사항이 있을 때만 쓴다. "없음"을 채우려고 절을 남기지 않는다.

## 사전 확인

- 현재 브랜치가 `develop` 또는 `main`이면 새 브랜치 필요 여부를 확인한다.
- 엔티티 변경이 있으면 `./gradlew compileJava` 여부 확인.
- MySQL ENUM 변경이 있으면 PR 비고에 ALTER TABLE 명령 명시.

## 절차

1. `git status --short --branch`, `git log --oneline -5` 확인.
2. 관련 이슈 `gh issue view <N> --repo GB-able/WIDYU-server` 읽기.
3. 관련 LLD를 읽는다. 없으면 작성 기준에 따른 N/A 사유를 기록한다.
4. 최신 base와의 merge-base 기준으로 범위를 파악하고, PR 생성에 필요한 head 게시가 요청 범위에 포함되면 작업 fork에 게시한다.
   - `git remote -v`에서 `GB-able/WIDYU-server`를 가리키는 원본 remote를 찾는다.
   - `git fetch <upstream-remote> develop`로 base를 갱신하고 `git merge-base <upstream-remote>/develop HEAD` 기준 diff를 확인한다.
   - 원본 remote가 없거나 fetch가 실패하면 오래된 로컬 ref로 대신하지 말고 실패 사실을 보고한다.
   - `git remote -v`에서 `dongkyun0713/WIDYU-server`를 가리키는 remote를 찾고, `git remote get-url --push <fork-remote>`로 push URL도 같은 저장소인지 확인한다.
   - 해당 remote가 없으면 기존 remote를 덮어쓰지 말고 `git remote add fork https://github.com/dongkyun0713/WIDYU-server.git`로 추가한다.
   - 확인한 `<fork-remote>`에 `git push -u <fork-remote> HEAD`를 실행한다. 원본 저장소를 가리키는 remote에는 작업 브랜치를 push하지 않는다.
5. 아래 템플릿으로 본문 작성. 본문은 존댓말 종결형(`합니다`, `습니다`, `확인했습니다`)을 사용한다.
6. 게시 전에 초안을 다음 세 가지로 점검하고, 아니면 고친다.
   - LLD와 이슈를 열지 않아도 문제, 변경 동작, 선택 이유가 이해되는가.
   - 실행하지 않은 검증이나 확인하지 않은 수치를 통과·결과로 쓰지 않았는가.
   - 리뷰 포인트가 리뷰어가 답할 수 있는 질문 하나인가.
7. 먼저 `dongkyun0713:<branch>`와 동일한 head의 PR을 조회해 기존 PR이면 갱신한다. 신규면 `gh pr create --repo GB-able/WIDYU-server --base develop --head dongkyun0713:<branch> --title "<title>" --body-file <tmpfile> --assignee dongkyun0713`로 생성한다. 4단계에서 확인한 push 저장소와 `--head` 저장소는 항상 같아야 한다.
8. `gh label list --repo GB-able/WIDYU-server --limit 50`로 사용 가능한 label을 확인한다.
9. 변경 성격에 맞는 label이 있으면 `gh pr edit <PR> --repo GB-able/WIDYU-server --add-label "<label>"`로 추가한다.
10. `gh pr view <PR> --repo GB-able/WIDYU-server --json assignees,labels`로 assignee와 label 반영을 확인한다.

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

본문은 존댓말 종결형으로 작성한다. `(있을 때만)` 절은 해당 사항이 없으면 절째 지운다.

```markdown
## 한눈에 보기
<이 PR이 해결하는 사용자·운영 문제와 최종 동작을 3~4문장으로 작성합니다.>

## 변경 동작
<동작 단위 bullet. 이전에는 어땠고 이제 어떻게 되는지, 왜 그렇게 했는지를 작성합니다.>

## 검증
- `<실제로 실행한 명령>`: 통과/실패/미실행 및 사유
<개발 서버 확인, 운영 미확인 여부와 확인하지 못한 LLD 인수조건을 구분합니다.>

## 리뷰 포인트
<리뷰어가 답할 수 있는 질문 1개로 좁혀 작성합니다.>

## 관련 문서와 이슈
- LLD: docs/lld/<경로> (예외면 N/A - 사유)
- ADR: docs/adr/<경로> (있을 때만)
- Closes #<N>

## Open Questions (있을 때만)
<LLD 미결정 사항 중 이 PR 리뷰에서 답이 필요한 것만 옮깁니다. 나머지는 「관련 문서와 이슈」의 LLD 링크에 절 번호와 개수(예: "미결정 사항 9개는 9절")를 적어 대신합니다.>

## 배포 영향 (있을 때만)
<ENUM ALTER TABLE, 엔티티 변경과 `compileJava`, 새 설정 키와 환경변수, 클라이언트 영향, 후속 PR>

<details>
<summary>상세 (있을 때만)</summary>

<측정표, 보수적으로 처리한 항목, 폐기한 설계 등 일부 리뷰어만 필요한 내용>

</details>
```

## 하지 말 것

- LLD에 없는 설계를 본문에 서술하기.
- LLD의 조건표, 측정 상세, Open Questions 전체를 본문에 복제하기.
- 해당 없는 체크리스트나 "없습니다"만 담은 절 남기기.
- develop 대신 main으로 PR 올리기.
- 영어 제목으로 PR 만들기.
