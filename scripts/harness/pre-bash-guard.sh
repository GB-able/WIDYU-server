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
# 판정을 정규식이 아니라 shlex 토큰으로 하는 이유 (PR #535 리뷰에서 드러난 것들):
#   - 따옴표 안의 <<EOF 를 heredoc 연산자로 오인하면 그 뒤 진짜 명령이 본문으로
#     취급돼 통째로 숨는다.
#   - rm -rf 만 보면 rm -r -f, rm --recursive --force 를 놓친다.
#   - reader 와 시크릿 경로를 각각 따로 찾으면 "cat README.md && echo .env" 처럼
#     시크릿을 읽지 않는 조합까지 막는다.
# 토큰으로 끊어 명령 단위(argv)를 만들면 셋 다 구조적으로 사라진다.

set -uo pipefail

VERDICT="$(cat | python3 -c '
import json, re, shlex, sys

try:
    data = json.load(sys.stdin)
except Exception:
    sys.exit(9)          # fail-closed

cmd = data.get("tool_input", {}).get("command", "")
if not cmd:
    sys.exit(0)

OPERATORS = {";", "&&", "||", "|", "&", "\n"}
READERS = {"cat", "head", "tail", "less", "more", "nl",
           "base64", "xxd", "od", "strings", "awk", "sed"}


def lex(text):
    """따옴표를 존중해 토큰으로 끊는다. 연산자는 별도 토큰이 된다.

    punctuation_chars 덕분에 맨 <<EOF 는 [\"<<\", \"EOF\"] 로,
    따옴표에 싸인 \x27<<EOF\x27 는 [\"<<EOF\"] 한 토큰으로 나뉜다.
    이 차이가 진짜 heredoc 연산자를 가려낸다.
    """
    lx = shlex.shlex(text, posix=True, punctuation_chars=True)
    lx.whitespace_split = True
    return list(lx)


def strip_heredocs(text):
    """heredoc 본문을 지운다. 본문은 실행되는 명령이 아니라 데이터다.

    커밋 메시지나 이슈 본문에 위험 명령을 설명으로 적었다고 차단하면 안 된다.
    동시에 본문을 지워야 줄바꿈으로 이어진 진짜 명령을 제대로 볼 수 있다.
    """
    lines = text.split("\n")
    kept, i = [], 0
    while i < len(lines):
        kept.append(lines[i])
        try:
            toks = lex(lines[i])
        except ValueError:
            toks = []
        delim = None
        for j, t in enumerate(toks):
            if t in ("<<", "<<-") and j + 1 < len(toks):
                delim = toks[j + 1]
                break
        if delim is not None:
            i += 1
            while i < len(lines) and lines[i].strip() != delim:
                i += 1   # 본문 폐기
        i += 1
    return "\n".join(kept)


def segments(text):
    """연산자·줄바꿈으로 끊어 명령 단위 argv 목록을 만든다."""
    out = []
    for line in text.split("\n"):
        if not line.strip():
            continue
        try:
            toks = lex(line)
        except ValueError:
            raise
        cur = []
        for t in toks:
            if t in OPERATORS:
                if cur:
                    out.append(cur)
                cur = []
            else:
                cur.append(t)
        if cur:
            out.append(cur)
    return out


def split_argv(argv):
    """앞쪽 환경변수 대입을 걷어내고 (명령, 인자들) 로 나눈다."""
    i = 0
    while i < len(argv) and re.match(r"^[A-Za-z_][A-Za-z0-9_]*=", argv[i]):
        i += 1
    argv = argv[i:]
    if not argv:
        return None, []
    return argv[0].rsplit("/", 1)[-1], argv[1:]


def shorts(args):
    """-rf, -r 같은 짧은 옵션의 문자 집합."""
    s = set()
    for a in args:
        if a.startswith("-") and not a.startswith("--") and len(a) > 1:
            s.update(a[1:])
    return s


def longs(args):
    return {a[2:].split("=")[0] for a in args if a.startswith("--") and len(a) > 2}


def operands(args):
    """옵션이 아닌 인자. 리다이렉션 대상은 여기 포함되지 않는다(연산자로 분리됨)."""
    return [a for a in args if not a.startswith("-")]


def is_secret(path):
    base = path.rsplit("/", 1)[-1]
    if base == ".env" or base.startswith(".env."):
        return True
    if base.endswith(".pem") or base.endswith(".p8"):
        return True
    if "/firebase/" in path and path.endswith(".json"):
        return True
    return False


def verdict(argv):
    name, args = split_argv(argv)
    if name is None:
        return None

    sub = args[0] if args else ""

    # git rm 은 rm 과 같은 규칙으로 본다
    if name == "git" and sub == "rm":
        name, args = "rm", args[1:]

    if name == "rm":
        sh, lo = shorts(args), longs(args)
        recursive = bool(sh & set("rR")) or "recursive" in lo
        force = "f" in sh or "force" in lo
        if recursive and force:
            return "rm -rf 는 막혀 있습니다. 지울 대상이 확실하면 사용자에게 요청하세요."
        for op in operands(args):
            if "scripts/mysql/" in op or op.endswith(".sql"):
                return "SQL 마이그레이션 파일 삭제는 막혀 있습니다. 필요하면 사용자에게 요청하세요."

    if name == "git" and sub == "push":
        rest = args[1:]
        if "f" in shorts(rest) or "force" in longs(rest):
            return "force push 는 막혀 있습니다."

    if name == "git" and sub == "reset" and "hard" in longs(args[1:]):
        return "git reset --hard 는 되돌릴 수 없습니다. 막혀 있습니다."

    if name == "git" and sub == "clean":
        rest = args[1:]
        if shorts(rest) & set("fd") or longs(rest) & {"force", "d"}:
            return "git clean 은 추적되지 않는 파일을 지웁니다. 막혀 있습니다."

    # down 의 위치를 고정하면 안 된다. docker compose -f a.yml down -v 처럼
    # 옵션이 앞에 오면 서브커맨드가 뒤로 밀린다.
    compose_down = (
        (name == "docker" and sub == "compose" and "down" in operands(args))
        or (name == "docker-compose" and "down" in operands(args))
    )
    if compose_down:
        if "v" in shorts(args) or "volumes" in longs(args):
            return "compose 볼륨 삭제는 DB 데이터까지 지웁니다. 막혀 있습니다."

    # Read() deny 는 Read 도구에만 걸린다. Bash 경유 읽기를 여기서 막는다.
    # 시크릿 경로가 "이 reader 의 인자"일 때만 막아야 오탐이 없다.
    if name in READERS:
        for op in operands(args):
            if is_secret(op):
                return "시크릿 파일(firebase 키·.env·인증서)을 Bash 로 읽는 것은 막혀 있습니다."

    return None


COARSE = [
    (r"rm\s+-[a-zA-Z]*[rR][a-zA-Z]*f|rm\s+-[a-zA-Z]*f[a-zA-Z]*[rR]", "rm -rf 는 막혀 있습니다."),
    (r"git\s+push\b[^\n]*--force", "force push 는 막혀 있습니다."),
    (r"git\s+reset\s+--hard", "git reset --hard 는 되돌릴 수 없습니다. 막혀 있습니다."),
]

try:
    for argv in segments(strip_heredocs(cmd)):
        msg = verdict(argv)
        if msg:
            print(msg)
            sys.exit(2)
except ValueError:
    # 따옴표가 안 맞는 등 토큰화 실패. 셸에서도 유효하지 않은 명령일 가능성이 높다.
    # 조용히 통과시키지는 않고, 거친 정규식으로 최소한의 그물은 친다.
    for pat, msg in COARSE:
        if re.search(pat, cmd):
            print(msg)
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
