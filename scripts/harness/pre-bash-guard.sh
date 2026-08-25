#!/bin/bash
# PreToolUse(Bash) 가드 — 되돌릴 수 없는 명령과 시크릿 읽기를 실행 전에 차단한다.
#
# settings.json 의 deny 와 겹치는 항목이 있다. 의도된 이중 방어다.
# deny 는 Read 도구와 명령 문자열만 보고, 이 훅은 파이프·세미콜론으로 이어진
# 명령까지 본다. ADR-0018 에서 실측했듯 어느 쪽도 보안 경계가 아니라 사고 방지 장치다.
#
# fail-closed: stdin JSON 을 파싱하지 못하면 통과가 아니라 차단한다.
# 무엇을 실행하려는지 모르는 상태에서 통과시키면 가드가 있으나 마나다.
# (브랜치 가드 pre-edit-branch-guard.sh 는 반대로 fail-open 이다. 그쪽은 막으려는
#  사고가 git switch 로 되돌려지는 반면, fail-closed 면 편집이 전면 중단된다. ADR-0021)
#
# Silent Success: 통과는 exit 0 무출력, 차단은 stderr + exit 2.
#
# 판정을 grep 이 아니라 python re 로 하는 이유:
#   grep 은 줄 단위라 heredoc 본문의 각 줄에도 ^ 앵커가 걸린다. 실제로 커밋 메시지
#   본문에 위험 명령을 설명으로 적었다가 커밋 자체가 차단됐다.
#   re 는 MULTILINE 없이 \A 를 쓰면 "명령 문자열의 진짜 시작"만 앵커가 된다.

set -uo pipefail

VERDICT="$(cat | python3 -c '
import json, re, sys

try:
    data = json.load(sys.stdin)
except Exception:
    sys.exit(9)          # fail-closed

cmd = data.get("tool_input", {}).get("command", "")
if not cmd:
    sys.exit(0)


def strip_heredocs(text):
    """heredoc 본문을 지운다.

    본문은 실행되는 명령이 아니라 데이터다. 커밋 메시지나 이슈 본문에 위험 명령을
    설명으로 적었다가 차단되는 오탐을 막는다. 동시에 본문을 지워야 줄바꿈으로 이어진
    진짜 명령에 ^ 앵커를 안전하게 쓸 수 있다.
    """
    lines = text.split("\n")
    kept, i = [], 0
    while i < len(lines):
        kept.append(lines[i])
        m = re.search(r"<<-?\s*[\x27\"]?([A-Za-z_][A-Za-z0-9_]*)[\x27\"]?", lines[i])
        if m:
            term = m.group(1)
            i += 1
            while i < len(lines) and lines[i].strip() != term:
                i += 1   # 본문 폐기
        i += 1
    return "\n".join(kept)


cmd = strip_heredocs(cmd)

# 명령이 시작될 수 있는 자리: 줄 시작(heredoc 본문은 위에서 제거됨),
# 또는 ; && || | ` $( 직후.
POS = r"(?:^|[;&|`]|\$\()\s*"
FLAGS = re.MULTILINE

RULES = [
    # ── 되돌릴 수 없는 명령 ──────────────────────────────────────────────
    (POS + r"rm\s+(?:-[a-zA-Z]*[rR][a-zA-Z]*f|-[a-zA-Z]*f[a-zA-Z]*[rR])",
     "rm -rf 는 막혀 있습니다. 지울 대상이 확실하면 사용자에게 요청하세요."),

    (POS + r"(?:git\s+)?rm\s+[^;&|]*(?:scripts/mysql/|\.sql(?:\s|$))",
     "SQL 마이그레이션 파일 삭제는 막혀 있습니다. 필요하면 사용자에게 요청하세요."),

    # push\b 뒤에서 공백을 미리 먹지 않는다. 먹으면 "git push -f" 의 -f 앞에
    # 남는 공백이 없어 매칭에 실패한다.
    (POS + r"git\s+push\b(?:[^;&|]*\s)?-(?:-force|f)(?:\s|$)",
     "force push 는 막혀 있습니다."),

    (POS + r"git\s+reset\s+--hard",
     "git reset --hard 는 되돌릴 수 없습니다. 막혀 있습니다."),

    (POS + r"git\s+clean\s+-[a-zA-Z]*[fd]",
     "git clean 은 추적되지 않는 파일을 지웁니다. 막혀 있습니다."),

    (POS + r"docker(?:\s+compose|-compose)\s+down\b[^;&|]*\s-v(?:\s|$)",
     "compose 볼륨 삭제는 DB 데이터까지 지웁니다. 막혀 있습니다."),
]

for pattern, message in RULES:
    if re.search(pattern, cmd, FLAGS):
        print(message)
        sys.exit(2)

# ── Read() deny 가 못 막는 Bash 경유 시크릿 읽기 ─────────────────────────
# settings.json 의 Read() deny 는 Read 도구에만 걸린다.
# 읽기 명령이 명령 자리에 있고 + 인자에 시크릿 경로가 있을 때만 막는다.
READER = POS + r"(?:cat|head|tail|less|more|nl|base64|xxd|od|strings|awk|sed)(?:\s|$)"
SECRET = r"(?:firebase/[^\s]*\.json|(?:^|[\s/])\.env(?:\s|$)|\.pem(?:\s|$)|\.p8(?:\s|$))"

if re.search(READER, cmd, FLAGS) and re.search(SECRET, cmd, FLAGS):
    print("시크릿 파일(firebase 키·.env·인증서)을 Bash 로 읽는 것은 막혀 있습니다.")
    sys.exit(2)

sys.exit(0)
' 2>/dev/null)"
rc=$?

if [[ $rc -eq 9 || $rc -gt 2 ]]; then
  echo "차단: PreToolUse 가드가 명령을 파싱하지 못했습니다(python3 미설치 또는 입력 손상). 안전을 위해 실행을 막습니다." >&2
  exit 2
fi

if [[ $rc -eq 2 ]]; then
  echo "차단: ${VERDICT}" >&2
  exit 2
fi

exit 0
