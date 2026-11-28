# LLD-0193: Claude 하네스를 줄이고 자동 검수 훅과 push·PR 게이트를 없앤다

| 항목 | 값 |
| --- | --- |
| 상태 | Approved (사용자가 #1173 실험 결과로 채택 지시, 2026-11-28) |
| Issue | #1173 |
| 관련 ADR | ADR-0018, ADR-0021 (하네스 가드) |
| 관련 LLD | LLD-0093 (push·PR 게이트 부분을 대체한다) |
| 작성자 | Claude |
| 작성일 | 2026-11-28 |

## 1. 목적 / 배경

Claude Code 하네스는 이전 모델의 약점을 보완하려고 쌓았다. 모델이 바뀐 뒤에도 필요한지 확인하려고, 머지된 이슈 두 건(#1169, #1163)을 머지 직전 코드에서 다시 구현하게 했다. 지금 하네스(current), 축소안(slim), 지침과 훅을 모두 뺀 상태(none)를 각 3회씩, 같은 모델(Opus 5.5)로 비교했다.

- 품질 차이가 없었다. 작은 과제는 PR 테스트를 숨겨 채점해 9/9 통과했다. 중간 과제는 조건을 가린 판정자가 완료 조건으로 채점해 9/9 통과했다.
- 훅이 결과를 바꾼 실행이 없었다. 편집 훅의 정적 검사 차단은 0건이었다. 종료 훅의 Codex 검수는 3회 모두 1라운드 APPROVE였다. 지침 없이 실행한 none도 규칙 위반이 0이었다.
- 비용은 하네스가 클수록 컸다. 중간 과제의 평균 Claude 비용은 current $1.98, slim $1.32, none $0.94였다.

push·PR 게이트(LLD-0093)는 서브에이전트가 만든 PR 4건이 검수 없이 올라가 2건이 BLOCKER였던 사고(2026-10)에서 나왔다. 그 뒤로 게이트는 보호 비용(push·PR마다 수 분의 Codex 실행)만 남겼다. 사용자는 이 비용이 더 크다고 판단해 게이트 제거를 결정했다. CI의 정적 규칙 검사와 전체 테스트는 그대로 PR을 검사한다.

## 2. 범위

### In scope
- 변경 모듈: 하네스(`scripts/harness/`, `.claude/settings.json`), 지침(`CLAUDE.md`), 문서(`docs/harness/codex.md`)
- `.claude/settings.json`: 편집 후 정적 검사 훅(`on-file-edit.sh`)과 종료 훅(`on-stop.sh`)을 뺀다. `ask`에서 `git push`, `gh pr create`를 뺀다. `git commit`, `gh pr merge` 확인과 `deny`(시크릿 읽기, 강제 push, `rm -rf` 등)는 유지한다
- `scripts/harness/on-file-edit.sh`, `on-stop.sh`: 부르는 곳이 없어져 지운다. 필요하면 Git 이력에서 되살린다
- `scripts/harness/pre-bash-guard.sh`: push·PR 게이트 코드를 지운다. 위험 명령 차단은 그대로다. Codex 훅도 이 가드를 쓰므로 Codex 쪽 게이트도 함께 사라진다
- `scripts/harness/audit-log.py`: 보호 브랜치 경고를 원본 체크아웃이 아니라 **편집한 파일이 속한 작업 트리**의 브랜치로 판정한다. Git 제외 파일과 저장소 밖 파일은 경고하지 않는다
- `CLAUDE.md`: 107줄에서 약 30줄로 줄인다. 코드에서 알 수 있는 패턴 목록, 훅 내부 설명, 일반적인 작업 순서를 뺀다. 명령, 모듈 배치, 가족 접근 검증, QueryDSL 금지, 코드 규칙, 압축 지침은 남긴다
- 게이트 테스트(`test_codex_review_gate.py`)를 `test_codex_review.py`로 바꾼다. 수동 검수(`codex-review.sh --base`)와 force push 차단은 유지하고, 게이트 테스트는 "검수 기록 없이 push·PR 통과"로 바꾼다

### Out of scope
- `backend/CLAUDE.md`(도메인 불변식) 축소. 불변식이 걸린 과제로 따로 측정한 뒤 정한다
- `AGENTS.md`, Codex 훅(`codex-hooks.py`의 Stop 정적 검사), 스킬 문서
- 개인 지침(Git 제외 파일) 정리. 사용자가 로컬에서 한다
- `codex-review.sh` 자체. 수동 검수 도구로 남긴다

## 3. 인터페이스 / API

없음. 개발 도구 동작만 바뀐다.

## 4. 데이터 모델

없음. 감사 로그 `workflow_risk`, `operational_code_on_protected_branch` 태그의 판정 기준만 바뀐다. `codex_gate` 이벤트는 더 생기지 않는다.

## 5. 처리 흐름

- Claude 편집: `pre-edit-branch-guard.sh`(보호 브랜치 차단) → 편집 → `audit-log.sh`
- Claude Bash: `pre-bash-guard.sh`(위험 명령만 차단) → 실행 → `audit-log.sh`
- Claude 종료: `audit-log.sh`만
- 규칙 검사: PR과 feature push에서 CI가 `verify.sh --static-only --base`로 변경 줄을 검사한다(기존 그대로)
- 의미 검수: 필요할 때 `codex-review.sh --base <merge-base>`를 직접 실행한다

## 6. 예외 / 에러 처리

- `git push --force`, `rm -rf` 등은 계속 `pre-bash-guard.sh`가 exit 2로 막는다
- 메인 체크아웃의 `.claude/settings.json`은 로컬 수정이 있고 skip-worktree 상태다. 머지 뒤 develop을 받을 때 이 파일은 자동으로 갱신되지 않는다. skip-worktree를 풀고 새 내용을 받은 뒤 로컬 차이를 다시 적용해야 한다

## 7. 인수조건 (Acceptance Criteria)

- [x] Claude 설정에 `on-file-edit.sh`, `on-stop.sh` 훅이 없고, `ask`에 `git push`, `gh pr create`가 없다
- [x] 검수 기록 없는 브랜치에서 `git push`, `gh pr create`, `git -C <worktree> push`가 가드를 통과한다(`test_push_and_pr_pass_without_review_record`)
- [x] `git push --force`와 `rm -rf`는 계속 막힌다(`test_force_push_still_blocked`, `test-pre-bash-guard.py`)
- [x] 다른 worktree 파일 편집에는 보호 브랜치 경고가 붙지 않고, 원본 체크아웃 develop의 추적 파일 편집에는 붙는다
- [x] `python3 -m unittest discover -s scripts/harness -p 'test_*.py'`, `test-pre-bash-guard.py`, `test-pre-edit-branch-guard.py`가 통과한다

## 8. 테스트 시나리오

- 단위: 위 인수조건의 하네스 회귀 테스트
- 실험: [#1173](https://github.com/GB-able/WIDYU-server/issues/1173) 이슈 본문과 PR 본문에 조건별 결과 표를 남긴다

## 미결정 사항(Open Questions)

- backend/CLAUDE.md의 불변식이 실제로 실수를 막는지. 불변식이 걸린 과제(카운터 원자 UPDATE, 로그 금지 값 등)로 다시 잰다
- 서브에이전트가 만든 PR의 검수 누락이 다시 문제가 되는지. 생기면 게이트 대신 PR에 대한 CI Codex 리뷰를 검토한다
