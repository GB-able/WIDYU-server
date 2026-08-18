#!/bin/bash
# PostToolUse hook: Edit/Write 도구 사용 후 Java 파일 규칙 검사 + audit 기록

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
AUDIT_DIR="$ROOT_DIR/.claude/audit"

input=$(cat)
# 값 2개를 python3 2회 기동으로 뽑던 것을 1회로 합친다.
# 이 훅은 Edit/Write 마다 실행되고 그중 81%는 java 가 아니라 즉시 종료하므로,
# 파싱 비용이 그대로 낭비된다.
# 구분자는 NUL 이어야 한다. 개행으로 나누면 경로에 개행이 든 경우 잘려 나가
# 규칙 검사가 조용히 건너뛰어진다.
{
  IFS= read -r -d '' file_path
  IFS= read -r -d '' session_id
} < <(
  printf '%s' "$input" | python3 -c "
import json, sys
d = json.load(sys.stdin)
out = sys.stdout.buffer
out.write(d.get('tool_input', {}).get('file_path', '').encode() + b'\0')
out.write(d.get('session_id', '').encode() + b'\0')
" 2>/dev/null
)

# Java 파일이 아니거나 테스트/generated 경로면 스킵
if [[ "$file_path" != *.java ]]; then exit 0; fi
if [[ "$file_path" == */test/* ]] || [[ "$file_path" == */generated/* ]]; then exit 0; fi
if [[ "$file_path" != */main/java/* ]]; then exit 0; fi

# 규칙 검사 실행 (exec 대신 호출 — 종료 코드 캡처 후 audit 기록)
RULE_OUT=$(bash "$(dirname "$0")/validate-java-rules.sh" "$file_path" 2>&1)
VIOLATIONS_RC=$?

# java_rule_check 이벤트 audit 기록 (violations: 0=통과, 1=위반 존재)
mkdir -p "$AUDIT_DIR"
python3 -c "
import json, datetime, sys
from pathlib import Path
audit_dir, sid, fpath, vrc = Path(sys.argv[1]), sys.argv[2], sys.argv[3], int(sys.argv[4])
month = datetime.datetime.now(datetime.timezone.utc).strftime('%Y-%m')
record = {
    'ts': datetime.datetime.now(datetime.timezone.utc).isoformat(timespec='milliseconds'),
    'session_id': sid, 'sid_prefix': sid[:8] if sid else '',
    'event': 'java_rule_check',
    'file': fpath,
    'violations': vrc,
    'tags': [],
}
out = audit_dir / ('audit-' + month + '.jsonl')
out.parent.mkdir(parents=True, exist_ok=True)
with open(out, 'a') as f:
    f.write(json.dumps(record, ensure_ascii=False) + '\n')
" "$AUDIT_DIR" "$session_id" "$file_path" "$VIOLATIONS_RC" 2>/dev/null || true

# PostToolUse 에서 모델에 피드백이 전달되는 종료 코드는 2 뿐이고, 전달 통로는 stderr 다.
# 이전에는 stdout + exit 1 이라 101회 발동 중 위반 2건이 모델에 한 번도 닿지 않았다.
if [[ $VIOLATIONS_RC -ne 0 ]]; then
  printf '%s\n' "$RULE_OUT" >&2
  exit 2
fi

exit 0
