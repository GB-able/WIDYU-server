#!/usr/bin/env bash
# audit-log.sh — 일상 세션 감사 로그 기록기 (Step 8 / Phase 1)
#
# 모든 훅에서 호출된다. 실패해도 exit 0 (fail-open) — 작업을 절대 막지 않는다.
# stdin 으로 Claude Code 훅 JSON 을 받아 audit-log.py 에 위임한다.

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
AUDIT_DIR="$ROOT/.claude/audit"
STATE_DIR="$ROOT/.claude/state"

mkdir -p "$AUDIT_DIR" "$STATE_DIR"

# TTL 청소 — 24시간 경과 상태 파일 삭제 (모든 훅 공통 시작부에서 실행)
# 2시간이던 시절에는 긴 세션 도중 codex-round 상태가 지워져 무한 루프 방지 카운터와
# 검수 지문이 함께 리셋됐다. 세션 길이보다 넉넉해야 한다.
find "$STATE_DIR" -type f -mmin +1440 -delete 2>/dev/null || true

# stdin 을 Python 에 직접 전달 (heredoc escaping 없이)
python3 "$ROOT/scripts/harness/audit-log.py" "$AUDIT_DIR" "$ROOT" || true

exit 0
