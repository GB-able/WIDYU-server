#!/bin/bash
# Codex 자동 검수 스크립트.
#
# 작업 트리의 uncommitted Java 변경을 검수한다. 코드를 직접 수정하지 않고 보고만 한다.
#
# 종료 코드:
#   0 = APPROVE (또는 검수할 Java 변경 없음)
#   1 = BLOCKER (버그·보안·로직 결함 발견 → 수정 필요)
#   3 = Codex 실행 실패 (네트워크/타임아웃/인증 등 → 호출측에서 fail-open 처리)
#
# stdout: 검수 리포트 전문
#   VERDICT: APPROVE | BLOCKER
#   BLOCKERS:        (BLOCKER일 때만)
#   - <파일:라인> 문제 → 수정 방향
#   SUGGESTIONS:     (있을 때만, on-stop.sh 가 Claude에 전달하지 않고 저장만 함)
#   - <제안 내용>
#
# 수동으로도 실행 가능: bash scripts/harness/codex-review.sh
set -uo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT_DIR" || exit 3

# on-stop.sh 의 _changed_java 와 동일한 NUL 기반 감지. 줄 파싱을 남겨두면
# 공백이 든 경로에서 변경을 못 보고 NO_JAVA_CHANGES 로 그냥 승인해 버린다.
CHANGED=$(git status --porcelain -z --untracked-files=all 2>/dev/null \
  | python3 -c "
import sys
data = sys.stdin.buffer.read().split(b'\0')
i = 0
while i < len(data):
    entry = data[i]
    i += 1
    if len(entry) < 4:
        continue
    status, path = entry[:2], entry[3:]
    if b'R' in status or b'C' in status:
        i += 1
    sys.stdout.buffer.write(path + b'\n')
" 2>/dev/null \
  | grep -E '\.java$' \
  | grep -E '^backend/(widyu-api|widyu-domain)/src/main/java/' \
  | grep -vE '/generated/' || true)

if [[ -z "$CHANGED" ]]; then
  echo "NO_JAVA_CHANGES"
  exit 0
fi

read -r -d '' PROMPT <<'EOF'
너는 이 저장소의 코드 리뷰어다. 코드를 절대 수정하지 말 것. 읽고 보고만 한다.

먼저 `git status --porcelain --untracked-files=all` 와 `git diff` 및 신규 파일 내용을
직접 확인해 uncommitted(작업 트리) 변경 전체를 파악한다.
문서(.md)·스크립트 변경은 참고만 하고, main-source Java 변경을 중심으로 검수한다.

── 판정 기준 ───────────────────────────────────────────────────────────────────
BLOCKER (수정 요청 사유):
  - 버그: null 역참조, 잘못된 조건 분기, 데이터 손실 가능성
  - 보안 결함: 인증 우회, 민감 정보 노출, SQL/커맨드 인젝션
  - 빌드 실패 유발: 컴파일 오류, 잘못된 import
  - 명백한 로직 오류: 비즈니스 규칙 위반, 인수조건 미충족

SUGGESTION (선택적 개선, 판정에 반영하지 말 것):
  - 테스트 추가, Swagger/docs 갱신, 리팩터링, 네이밍 개선 등
  - "이 김에 ~도 하면 좋다"는 모두 SUGGESTION

── 검수 기준 ───────────────────────────────────────────────────────────────────
.agents/skills/review/SKILL.md 의 체크리스트를 따른다:
- 관련 LLD(docs/lld/)의 "## 7. 인수조건" 충족 여부
- 모듈 배치: 엔티티는 widyu-domain, Repository/Service/Controller는 widyu-api
- @Async 메서드 @Transactional 여부
- 엔티티 변경 시 MySQL ENUM ALTER TABLE 명시 여부

── 제약 ────────────────────────────────────────────────────────────────────────
- 현재 diff에 포함되지 않은 파일의 수정·생성을 요구하지 마라.
  (Swagger docs 업데이트, 테스트 추가 등은 SUGGESTION으로만 남긴다)
- diff 범위 밖의 지적은 전부 SUGGESTION이다.

── 출력 형식 ────────────────────────────────────────────────────────────────────
반드시 아래 형식으로만 출력한다. 형식 외 자유 서술 금지.

VERDICT: APPROVE
SUGGESTIONS:
- <제안 내용> (없으면 이 섹션 생략)

또는

VERDICT: BLOCKER
BLOCKERS:
- [파일:라인] 문제 설명 → 수정 방향
SUGGESTIONS:
- <제안 내용> (없으면 이 섹션 생략)
EOF

REPORT_FILE="$(mktemp -t codex-review.XXXXXX)"
CODEX_BIN="$(command -v codex || true)"
TIMEOUT_BIN="$(command -v timeout || command -v gtimeout || true)"

# codex 미설치 시 빈 문자열로 실행돼 "command not found" 로 흘러가면
# 원인이 네트워크·인증 실패와 구분되지 않는다. 명시적으로 알린다.
if [[ -z "$CODEX_BIN" ]]; then
  echo "CODEX_FAILED"
  echo "codex 실행 파일을 찾을 수 없습니다 (PATH 확인 필요)."
  rm -f "$REPORT_FILE"
  exit 3
fi

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

# 판정 파싱: 마지막 VERDICT 줄만 실제 판정으로 본다 (프롬프트 에코 오염 방지)
FINAL_VERDICT=$(grep -oE 'VERDICT:[[:space:]]*(APPROVE|BLOCKER)' "$REPORT_FILE" | tail -n 1)
rm -f "$REPORT_FILE"

if printf '%s' "$FINAL_VERDICT" | grep -q 'BLOCKER'; then
  exit 1
fi

exit 0
