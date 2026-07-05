#!/bin/bash
# Codex 자동 검수 스크립트.
#
# 작업 트리의 uncommitted Java 변경을 .agents/skills/review/SKILL.md 기준으로
# Codex(codex exec review)에게 검수시킨다. 코드를 직접 수정하지 않고 보고만 한다.
#
# 종료 코드:
#   0 = APPROVE (또는 검수할 Java 변경 없음)
#   1 = REQUEST_CHANGES (수정 필요)
#   3 = codex 실행 실패 (네트워크/타임아웃/인증 등 → 호출측에서 fail-open 처리)
#
# stdout: 검수 리포트 전문 (첫 줄에 상태 토큰: NO_JAVA_CHANGES / CODEX_FAILED 가능)
#
# 수동으로도 실행 가능: bash scripts/harness/codex-review.sh
set -uo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT_DIR" || exit 3

# uncommitted 상태의 main-source Java 변경만 대상 (test/generated 제외)
CHANGED=$(git status --porcelain --untracked-files=all 2>/dev/null \
  | sed -E 's/^...//; s/^.* -> //' \
  | grep -E '\.java$' \
  | grep -E '/main/java/' \
  | grep -vE '/generated/' || true)

if [[ -z "$CHANGED" ]]; then
  echo "NO_JAVA_CHANGES"
  exit 0
fi

read -r -d '' PROMPT <<'EOF'
너는 이 저장소의 코드 리뷰어다. 코드를 절대 수정하지 말 것. 읽고 보고만 한다.

먼저 `git status --porcelain --untracked-files=all` 와 `git diff` 및 신규/스테이지 파일 내용을
직접 확인해 uncommitted(작업 트리) 변경 전체를 파악한다. 문서(.md)·스크립트 변경은 참고만 하고,
main-source Java 변경을 중심으로 검수한다.

검수 기준은 .agents/skills/review/SKILL.md 의 체크리스트를 그대로 따른다:
- 관련 LLD(docs/lld/)의 "## 7. 인수조건" 충족 여부
- 코딩 규칙: 삼항 연산자 금지, Service/Facade에서 new XxxResponse( 직접 생성 금지(from()/of() 사용),
  Controller에서 Repository 직접 import 금지, @Async 메서드 @Transactional 여부
- 모듈 배치: 엔티티는 widyu-domain, Repository/Service/Controller는 widyu-api
- 테스트: 인수조건에 대응하는 테스트 존재 여부, BDDMockito + 한글 언더스코어 명명
- 엔티티 변경 시 docs/erd/ 동기화 및 MySQL ENUM ALTER TABLE 명시 여부
- 신규/변경 API 시 controller/docs/ 의 *Docs 인터페이스 업데이트 여부

코드를 절대 수정하지 말 것. 보고만 한다.
문제 항목은 각 줄을 [파일:라인] 문제 설명 → 수정 방향 형식으로 나열한다.
출력의 마지막 줄에 반드시 아래 중 하나만 단독으로 출력한다:
VERDICT: APPROVE
VERDICT: REQUEST_CHANGES
EOF

REPORT_FILE="$(mktemp -t codex-review.XXXXXX)"
CODEX_BIN="$(command -v codex)"
TIMEOUT_BIN="$(command -v timeout || command -v gtimeout || true)"

run_codex() {
  if [[ -n "$TIMEOUT_BIN" ]]; then
    "$TIMEOUT_BIN" 480 "$CODEX_BIN" exec --skip-git-repo-check "$PROMPT" </dev/null
  else
    "$CODEX_BIN" exec --skip-git-repo-check "$PROMPT" </dev/null
  fi
}

if ! run_codex >"$REPORT_FILE" 2>&1; then
  echo "CODEX_FAILED"
  cat "$REPORT_FILE"
  rm -f "$REPORT_FILE"
  exit 3
fi

cat "$REPORT_FILE"

# 판정 파싱: REQUEST_CHANGES 우선, 없으면 APPROVE, 둘 다 없으면 보수적으로 통과(fail-open)
if grep -qE 'VERDICT:[[:space:]]*REQUEST_CHANGES|\bREQUEST_CHANGES\b' "$REPORT_FILE"; then
  rm -f "$REPORT_FILE"
  exit 1
fi

rm -f "$REPORT_FILE"
exit 0
