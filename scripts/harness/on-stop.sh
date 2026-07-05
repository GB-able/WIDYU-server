#!/bin/bash
# Stop hook: Claude 응답 종료 시 Java 변경을 감지하면 Codex 자동 검수 → 피드백 루프.
#
# 흐름:
#   1) uncommitted main-source Java 변경 없음 → 정지 허용 (exit 0)
#   2) 변경 있음 → codex-review.sh 실행
#        - APPROVE            → 정지 허용 (exit 0)
#        - REQUEST_CHANGES    → 리포트를 stderr로 출력하고 exit 2
#                               (Stop 훅 exit 2 = 정지 차단 + stderr를 Claude에 전달 → 수정 계속)
#        - Codex 실행 실패     → 사용자에게만 경고, 정지 허용 (fail-open)
#
# 무한 루프 방지: .claude/.codex-review-round 카운터로 최대 MAX_ROUNDS회까지만 자동 재수정.
# 초과 시 사용자에게 수동 검토를 넘기고 정지 허용.
set -uo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
STATE_FILE="$ROOT_DIR/.claude/.codex-review-round"
MAX_ROUNDS=3

# Stop 훅 stdin(JSON)을 소비해 하위 codex 호출이 삼키지 않도록 한다.
cat >/dev/null 2>&1 || true

CHANGED_JAVA=$(git -C "$ROOT_DIR" status --porcelain --untracked-files=all 2>/dev/null \
  | sed -E 's/^...//; s/^.* -> //' \
  | grep -E '\.java$' \
  | grep -E '/main/java/' \
  | grep -vE '/generated/' || true)

if [[ -z "$CHANGED_JAVA" ]]; then
  # 검수할 Java 변경 없음 → 라운드 초기화 후 정지 허용
  rm -f "$STATE_FILE"
  exit 0
fi

echo "🔍 Codex 자동 검수 실행 중... (uncommitted Java 변경 감지)" >&2

REPORT="$(bash "$ROOT_DIR/scripts/harness/codex-review.sh")"
RC=$?

# Codex 실행 실패 → fail-open (세션을 막지 않는다)
if [[ $RC -eq 3 ]] || printf '%s' "$REPORT" | head -1 | grep -q "CODEX_FAILED"; then
  echo "⚠️  Codex 자동 검수를 실행하지 못했습니다 (네트워크/타임아웃/인증 등). 수동 검수가 필요합니다." >&2
  exit 0
fi

# APPROVE → 라운드 초기화, 정지 허용
if [[ $RC -eq 0 ]]; then
  rm -f "$STATE_FILE"
  echo "✅ Codex 검수 통과 (APPROVE)." >&2
  exit 0
fi

# RC == 1 → REQUEST_CHANGES: 라운드 카운트
ROUND=0
[[ -f "$STATE_FILE" ]] && ROUND=$(cat "$STATE_FILE" 2>/dev/null || echo 0)
ROUND=$((ROUND + 1))
echo "$ROUND" > "$STATE_FILE"

if [[ $ROUND -gt $MAX_ROUNDS ]]; then
  rm -f "$STATE_FILE"
  {
    echo "⚠️  Codex 검수를 ${MAX_ROUNDS}회 반복했으나 여전히 REQUEST_CHANGES입니다."
    echo "    자동 수정 루프를 중단합니다. 아래 리포트를 수동으로 확인하세요."
    echo "----- Codex Review -----"
    printf '%s\n' "$REPORT"
  } >&2
  exit 0
fi

# 정지 차단 + Claude에게 피드백 전달 (exit 2 + stderr)
{
  echo "🔁 Codex 자동 검수 결과: REQUEST_CHANGES (라운드 ${ROUND}/${MAX_ROUNDS})"
  echo "아래 지적 사항을 반영해 코드를 수정하세요. 수정 후 정지 시 다시 자동 검수됩니다."
  echo "수정할 항목이 없다고 판단되면 사용자에게 근거와 함께 확인을 요청하세요."
  echo "----- Codex Review -----"
  printf '%s\n' "$REPORT"
} >&2
exit 2
