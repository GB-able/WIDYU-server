#!/bin/bash
# Stop hook: Claude 응답 종료 시 Java 변경을 감지하면 3-Tier 검수 → 피드백 루프.
#
# 흐름:
#   Tier 0) uncommitted main-source Java 변경 없음 → 정지 허용 (exit 0)
#   Tier 1) 정적 규칙 검사 (validate-java-rules.sh, 무료·<1초)
#            위반 발견 → exit 2 + 위반 리포트 전달 (Codex 호출 없음)
#   Tier 2) Codex 필요성 게이트
#            diff<30 LOC & ≤2파일 & 신규 파일 0 & 비민감 경로 → Codex 생략, exit 0
#   Tier 3) Codex 시맨틱 리뷰
#            APPROVE  → 정지 허용 (exit 0). SUGGESTIONS는 .claude/suggestions/ 에 저장
#            BLOCKER  → BLOCKERS만 Claude에 전달 (exit 2). SUGGESTIONS는 저장만
#            실행 실패 → 경고 후 정지 허용 (fail-open)
#
# 무한 루프 방지:
#   static_round  — Tier 1 위반 카운터 (최대 MAX_ROUNDS)
#   round         — Tier 3 BLOCKER 카운터 (최대 MAX_ROUNDS)
#   두 카운터 모두 .claude/state/codex-round-<sid>.json 에 세션별 저장
set -uo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
STATE_DIR="$ROOT_DIR/.claude/state"
AUDIT_DIR="$ROOT_DIR/.claude/audit"
SUGG_DIR="$ROOT_DIR/.claude/suggestions"
MAX_ROUNDS=3

mkdir -p "$STATE_DIR" "$AUDIT_DIR" "$SUGG_DIR"

# ─── session_id 추출 ──────────────────────────────────────────────────────────

STDIN_JSON=$(cat 2>/dev/null || true)
SESSION_ID=$(python3 -c "
import json, sys
try:
    print(json.loads(sys.argv[1]).get('session_id', ''))
except Exception:
    print('')
" "$STDIN_JSON" 2>/dev/null || true)

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
  exit 0
fi

SID_PREFIX="${SESSION_ID:0:8}"
STATE_FILE="$STATE_DIR/codex-round-${SID_PREFIX}.json"

# ─── 헬퍼: 감사 로그 ─────────────────────────────────────────────────────────
#
# 이벤트 타입 분리 (codex_review 이벤트 수 = 실제 Codex 호출 수 불변식 유지):
#   codex_review   — Tier 3에서 Codex 실제 실행 시
#   static_check   — Tier 1 정적 검사 결과
#   review_skipped — Tier 2 게이트로 Codex 생략 시

# _audit_event event_name [key=value ...]
_audit_event() {
  local event="$1"; shift
  python3 -c "
import json, datetime, sys
from pathlib import Path
root, sid, event = sys.argv[1], sys.argv[2], sys.argv[3]
record = {
    'ts': datetime.datetime.now(datetime.timezone.utc).isoformat(timespec='milliseconds'),
    'session_id': sid, 'sid_prefix': sid[:8], 'event': event,
}
for kv in sys.argv[4:]:
    k, _, v = kv.partition('=')
    record[k] = int(v) if v.isdigit() else v
month = datetime.datetime.now(datetime.timezone.utc).strftime('%Y-%m')
out = Path(root) / '.claude' / 'audit' / ('audit-' + month + '.jsonl')
out.parent.mkdir(parents=True, exist_ok=True)
with open(out, 'a') as f:
    f.write(json.dumps(record, ensure_ascii=False) + '\n')
" "$ROOT_DIR" "$SESSION_ID" "$event" "$@" 2>/dev/null || true
}

# _audit_codex verdict round  (Tier 3 전용, codex_review 이벤트)
_audit_codex() {
  _audit_event "codex_review" "verdict=$1" "round=$2"
}

# ─── 헬퍼: 상태 파일 (field 단위 읽기·쓰기, 다른 필드 보존) ─────────────────

# _read_state_field field [default=0]
_read_state_field() {
  local field="$1" default="${2:-0}"
  [[ -f "$STATE_FILE" ]] || { echo "$default"; return; }
  python3 -c "
import json, sys
try:
    v = json.load(open(sys.argv[1])).get(sys.argv[2])
    print(v if v is not None else sys.argv[3])
except Exception:
    print(sys.argv[3])
" "$STATE_FILE" "$field" "$default" 2>/dev/null || echo "$default"
}

# _write_state_field field value  (기존 필드 유지하며 지정 필드만 갱신)
_write_state_field() {
  local field="$1" value="$2"
  python3 -c "
import json, sys
path, sid, field, value = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
try:
    state = json.load(open(path))
except Exception:
    state = {}
state['session_id'] = sid
try:
    state[field] = int(value)
except ValueError:
    state[field] = value
json.dump(state, open(path, 'w'))
" "$STATE_FILE" "$SESSION_ID" "$field" "$value" 2>/dev/null || true
}

# ─── Tier 0: Java 변경 감지 ───────────────────────────────────────────────────

CHANGED_JAVA=$(git -C "$ROOT_DIR" status --porcelain --untracked-files=all 2>/dev/null \
  | sed -E 's/^...//; s/^.* -> //' \
  | grep -E '\.java$' \
  | grep -E '^backend/(widyu-api|widyu-domain)/src/main/java/' \
  | grep -vE '/generated/' || true)

if [[ -z "$CHANGED_JAVA" ]]; then
  rm -f "$STATE_FILE"
  exit 0
fi

# ─── Tier 1: 정적 규칙 검사 ───────────────────────────────────────────────────

RULE_REPORT=""
while IFS= read -r f; do
  [ -n "$f" ] || continue
  OUT=$(bash "$ROOT_DIR/scripts/harness/validate-java-rules.sh" "$ROOT_DIR/$f" 2>&1)
  RC=$?
  if [[ $RC -ne 0 ]]; then RULE_REPORT+="$OUT"$'\n'; fi
done <<< "$CHANGED_JAVA"

if [[ -n "$RULE_REPORT" ]]; then
  STATIC_ROUND=$(_read_state_field "static_round")
  STATIC_ROUND=$((STATIC_ROUND + 1))
  _write_state_field "static_round" "$STATIC_ROUND"
  _audit_event "static_check" "result=violation" "round=$STATIC_ROUND"

  if [[ $STATIC_ROUND -gt $MAX_ROUNDS ]]; then
    rm -f "$STATE_FILE"
    {
      echo "⚠️  코드 규칙 위반이 ${MAX_ROUNDS}회 반복 미해결. 자동 수정 루프를 중단합니다."
      echo "    아래 항목을 수동으로 확인하세요."
      echo "----- 정적 규칙 위반 -----"
      printf '%s\n' "$RULE_REPORT"
    } >&2
    exit 0
  fi

  {
    echo "🔁 코드 규칙 위반 감지 (라운드 ${STATIC_ROUND}/${MAX_ROUNDS}) — 아래 항목을 수정하세요."
    echo "수정 후 정지 시 다시 자동 검사됩니다."
    echo "----- 정적 규칙 위반 -----"
    printf '%s\n' "$RULE_REPORT"
  } >&2
  exit 2
fi

_audit_event "static_check" "result=pass"
# 통과 시 정적 카운터 리셋 — 같은 세션의 다음 수정 건이 이전 카운트를 이어받지 않게
if [[ -f "$STATE_FILE" ]]; then
  _write_state_field "static_round" 0
fi

# ─── Tier 2: Codex 필요성 게이트 ─────────────────────────────────────────────

# diff HEAD: staged+unstaged 모두 포함 (git diff 단독은 staged 누락 → 게이트 우회 구멍)
# untracked 신규 파일은 numstat에 안 잡히지만 NEW_FILES=0 조건이 걸러줌
DIFF_LOC=$(git -C "$ROOT_DIR" diff HEAD --numstat -- '*src/main/java*.java' 2>/dev/null \
  | grep -v '/generated/' \
  | awk '{a+=$1+$2} END{print a+0}')
FILE_COUNT=$(echo "$CHANGED_JAVA" | grep -c . || true)
# 신규 파일: untracked(??)와 staged 추가(A) 모두 포착
NEW_FILES=$(git -C "$ROOT_DIR" status --porcelain --untracked-files=all 2>/dev/null \
  | grep -cE '^([?][?]|A.|.A) .*src/main/java/.*\.java$' || true)
SENSITIVE=$(echo "$CHANGED_JAVA" \
  | grep -cE '/(auth|security|payment|global/security)/' || true)

if [[ $DIFF_LOC -lt 30 && $FILE_COUNT -le 2 && $NEW_FILES -eq 0 && $SENSITIVE -eq 0 ]]; then
  echo "✅ 소규모 변경 (${DIFF_LOC} LOC, ${FILE_COUNT}개 파일) — 정적 검사 통과, Codex 생략." >&2
  _audit_event "review_skipped" "reason=small_diff" "diff_loc=$DIFF_LOC" "file_count=$FILE_COUNT"
  rm -f "$STATE_FILE"
  exit 0
fi

# ─── Tier 3: Codex 시맨틱 리뷰 ───────────────────────────────────────────────

echo "🔍 Codex 자동 검수 실행 중... (${DIFF_LOC} LOC, ${FILE_COUNT}개 파일)" >&2

REPORT="$(bash "$ROOT_DIR/scripts/harness/codex-review.sh")"
RC=$?

# Codex 실행 실패 → fail-open
if [[ $RC -eq 3 ]] || printf '%s' "$REPORT" | head -1 | grep -q "CODEX_FAILED"; then
  echo "⚠️  Codex 자동 검수를 실행하지 못했습니다 (네트워크/타임아웃/인증 등). 수동 검수가 필요합니다." >&2
  _audit_codex "FAILED" 0
  exit 0
fi

# SUGGESTIONS 분리 헬퍼 — APPROVE / BLOCKER 공통
_save_suggestions() {
  local body="$1"
  [[ -n "$body" ]] || return
  local sugg_file="$SUGG_DIR/suggestions-${SID_PREFIX}-$(date +%s).md"
  {
    echo "# Codex 제안 사항 ($(date '+%Y-%m-%d %H:%M'))"
    echo ""
    printf '%s\n' "$body"
  } > "$sugg_file"
  echo "💡 Codex 제안 사항 저장: ${sugg_file#$ROOT_DIR/}" >&2
}

# codex exec는 프롬프트를 출력에 에코하며, 프롬프트에도 VERDICT:/SUGGESTIONS: 줄이
# 있으므로 첫-매칭 분리는 에코에 걸린다. 마지막 VERDICT: 줄부터가 실제 응답이다.
FINAL_SECTION=$(printf '%s\n' "$REPORT" | awk '
  /^VERDICT:/ { n = NR }
  { lines[NR] = $0 }
  END { if (!n) exit; for (i = n; i <= NR; i++) print lines[i] }')

SUGGESTION_BODY=$(printf '%s\n' "$FINAL_SECTION" | awk 'found{print} /^SUGGESTIONS:/{found=1}')

# APPROVE → 라운드 초기화, 정지 허용
if [[ $RC -eq 0 ]]; then
  _save_suggestions "$SUGGESTION_BODY"
  rm -f "$STATE_FILE"
  echo "✅ Codex 검수 통과 (APPROVE)." >&2
  _audit_codex "APPROVE" 0
  exit 0
fi

# RC == 1 → BLOCKER: 라운드 카운트
ROUND=$(_read_state_field "round")
ROUND=$((ROUND + 1))
_write_state_field "round" "$ROUND"
_audit_codex "BLOCKER" "$ROUND"

# BLOCKERS만 Claude에 전달, SUGGESTIONS는 저장만 (에코 제거된 FINAL_SECTION 기준)
BLOCKER_BODY=$(printf '%s\n' "$FINAL_SECTION" | awk '/^SUGGESTIONS:/{exit} 1')
# FINAL_SECTION이 비면(VERDICT 줄 부재) 원문 전체로 폴백 — 정보 유실 방지
if [[ -z "$BLOCKER_BODY" ]]; then
  BLOCKER_BODY="$REPORT"
fi
_save_suggestions "$SUGGESTION_BODY"

if [[ $ROUND -gt $MAX_ROUNDS ]]; then
  rm -f "$STATE_FILE"
  {
    echo "⚠️  Codex 검수를 ${MAX_ROUNDS}회 반복했으나 여전히 BLOCKER가 있습니다."
    echo "    자동 수정 루프를 중단합니다. 아래 리포트를 수동으로 확인하세요."
    echo "----- Codex Review (BLOCKERS) -----"
    printf '%s\n' "$BLOCKER_BODY"
  } >&2
  exit 0
fi

{
  echo "🔁 Codex 자동 검수 결과: BLOCKER 발견 (라운드 ${ROUND}/${MAX_ROUNDS})"
  echo "아래 결함을 반영해 코드를 수정하세요. 수정 후 정지 시 다시 자동 검수됩니다."
  echo "수정할 항목이 없다고 판단되면 사용자에게 근거와 함께 확인을 요청하세요."
  echo "----- Codex Review (BLOCKERS) -----"
  printf '%s\n' "$BLOCKER_BODY"
} >&2
exit 2
