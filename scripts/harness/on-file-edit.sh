#!/bin/bash
# PostToolUse hook: Edit/Write 도구 사용 후 Java 파일 규칙 검사 + audit 기록

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
AUDIT_DIR="$ROOT_DIR/.claude/audit"

input=$(cat)
file_path=$(echo "$input" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('tool_input',{}).get('file_path',''))" 2>/dev/null || true)
session_id=$(echo "$input" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('session_id',''))" 2>/dev/null || true)

# Java 파일이 아니거나 테스트/generated 경로면 스킵
if [[ "$file_path" != *.java ]]; then exit 0; fi
if [[ "$file_path" == */test/* ]] || [[ "$file_path" == */generated/* ]]; then exit 0; fi
if [[ "$file_path" != */main/java/* ]]; then exit 0; fi

# 규칙 검사 실행 (exec 대신 호출 — 종료 코드 캡처 후 audit 기록)
bash "$(dirname "$0")/validate-java-rules.sh" "$file_path"
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

exit $VIOLATIONS_RC
