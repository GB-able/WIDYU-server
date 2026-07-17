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
# 무한 루프 방지: .claude/state/codex-round-<sid>.json 카운터로 최대 MAX_ROUNDS회까지만 자동 재수정.
# 세션별 상태 파일로 터미널 2개 동시 작업 시 라운드 카운터 간섭을 차단한다.
# 초과 시 사용자에게 수동 검토를 넘기고 정지 허용.
set -uo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
STATE_DIR="$ROOT_DIR/.claude/state"
AUDIT_DIR="$ROOT_DIR/.claude/audit"
MAX_ROUNDS=3

mkdir -p "$STATE_DIR" "$AUDIT_DIR"

# ---------------------------------------------------------------------------
# stdin 에서 session_id 추출 (audit-log.sh 와 달리 Stop 훅은 직접 소비해야 함)
# ---------------------------------------------------------------------------

STDIN_JSON=$(cat 2>/dev/null || true)
SESSION_ID=$(python3 -c "
import json, sys
try:
    print(json.loads(sys.argv[1]).get('session_id', ''))
except Exception:
    print('')
" "$STDIN_JSON" 2>/dev/null || true)

# session_id 부재 시 감사 기록만 남기고 세션별 상태 파일은 쓰지 않는다 (fallback)
if [[ -z "$SESSION_ID" ]]; then
  python3 -c "
import json, datetime
from pathlib import Path
month = datetime.datetime.now(datetime.timezone.utc).strftime('%Y-%m')
out = Path('$AUDIT_DIR') / ('audit-' + month + '.jsonl')
out.parent.mkdir(parents=True, exist_ok=True)
record = {'ts': datetime.datetime.now(datetime.timezone.utc).isoformat(timespec='milliseconds'),
          'session_id': '', 'event': 'session_id_missing', 'hook': 'Stop'}
with open(out, 'a') as f:
    f.write(json.dumps(record) + '\n')
" 2>/dev/null || true
  SESSION_ID="unknown-$(date +%s)"
fi

SID_PREFIX="${SESSION_ID:0:8}"
STATE_FILE="$STATE_DIR/codex-round-${SID_PREFIX}.json"

# ---------------------------------------------------------------------------
# audit 기록 헬퍼 — args: verdict round
# ---------------------------------------------------------------------------

_audit_codex() {
  local verdict="$1" round="$2"
  python3 -c "
import json, datetime, sys
from pathlib import Path
root, sid, verdict, rnd = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4])
month = datetime.datetime.now(datetime.timezone.utc).strftime('%Y-%m')
record = {
    'ts': datetime.datetime.now(datetime.timezone.utc).isoformat(timespec='milliseconds'),
    'session_id': sid, 'sid_prefix': sid[:8],
    'event': 'codex_review', 'verdict': verdict, 'round': rnd, 'tags': [],
}
out = Path(root) / '.claude' / 'audit' / ('audit-' + month + '.jsonl')
out.parent.mkdir(parents=True, exist_ok=True)
with open(out, 'a') as f:
    f.write(json.dumps(record, ensure_ascii=False) + '\n')
" "$ROOT_DIR" "$SESSION_ID" "$verdict" "$round" 2>/dev/null || true
}

# ---------------------------------------------------------------------------
# Java 변경 감지
# ---------------------------------------------------------------------------

CHANGED_JAVA=$(git -C "$ROOT_DIR" status --porcelain --untracked-files=all 2>/dev/null \
  | sed -E 's/^...//; s/^.* -> //' \
  | grep -E '\.java$' \
  | grep -E '/main/java/' \
  | grep -vE '/generated/' || true)

if [[ -z "$CHANGED_JAVA" ]]; then
  # 검수할 Java 변경 없음 → 라운드 파일 초기화 후 정지 허용
  rm -f "$STATE_FILE"
  exit 0
fi

echo "🔍 Codex 자동 검수 실행 중... (uncommitted Java 변경 감지)" >&2

REPORT="$(bash "$ROOT_DIR/scripts/harness/codex-review.sh")"
RC=$?

# Codex 실행 실패 → fail-open
if [[ $RC -eq 3 ]] || printf '%s' "$REPORT" | head -1 | grep -q "CODEX_FAILED"; then
  echo "⚠️  Codex 자동 검수를 실행하지 못했습니다 (네트워크/타임아웃/인증 등). 수동 검수가 필요합니다." >&2
  _audit_codex "FAILED" 0
  exit 0
fi

# APPROVE → 라운드 초기화, 감사 기록, 정지 허용
if [[ $RC -eq 0 ]]; then
  rm -f "$STATE_FILE"
  echo "✅ Codex 검수 통과 (APPROVE)." >&2
  _audit_codex "APPROVE" 0
  exit 0
fi

# RC == 1 → REQUEST_CHANGES: 라운드 카운트
ROUND=0
if [[ -f "$STATE_FILE" ]]; then
  ROUND=$(python3 -c "
import json, sys
try:
    print(json.load(open(sys.argv[1])).get('round', 0))
except Exception:
    print(0)
" "$STATE_FILE" 2>/dev/null || echo 0)
fi
ROUND=$((ROUND + 1))

python3 -c "
import json, sys
path, sid, rnd = sys.argv[1], sys.argv[2], int(sys.argv[3])
json.dump({'session_id': sid, 'round': rnd}, open(path, 'w'))
" "$STATE_FILE" "$SESSION_ID" "$ROUND" 2>/dev/null || true

_audit_codex "REQUEST_CHANGES" "$ROUND"

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
