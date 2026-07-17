"""audit-log.py — 훅 stdin JSON 을 파싱해 감사 로그에 기록한다.

사용: python3 audit-log.py <audit_dir> <repo_root>
stdin: Claude Code 훅 JSON

설계 원칙:
- 예외가 발생해도 절대 abort 하지 않는다 (audit-log.sh 가 exit 0 보장).
- cmd 는 기록 전에 redaction + 200자 절단한다.
- session_id 부재 시 상태 파일을 쓰지 않고 session_id_missing 이벤트만 남긴다.
"""

import json
import re
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

# ---------------------------------------------------------------------------
# 인수
# ---------------------------------------------------------------------------

if len(sys.argv) < 3:
    sys.exit(0)

AUDIT_DIR = Path(sys.argv[1])
ROOT = Path(sys.argv[2])
AUDIT_DIR.mkdir(parents=True, exist_ok=True)

# ---------------------------------------------------------------------------
# stdin 읽기
# ---------------------------------------------------------------------------

try:
    raw = sys.stdin.read()
    data = json.loads(raw) if raw.strip() else {}
except Exception:
    sys.exit(0)

# ---------------------------------------------------------------------------
# 공통 필드
# ---------------------------------------------------------------------------

session_id: str = data.get("session_id", "")
hook_event: str = data.get("hook_event_name", "")
tool_name: str = data.get("tool_name", "")
ts = datetime.now(timezone.utc).isoformat(timespec="milliseconds")
month = datetime.now(timezone.utc).strftime("%Y-%m")
audit_file = AUDIT_DIR / f"audit-{month}.jsonl"

# ---------------------------------------------------------------------------
# session_id 부재 fallback
# ---------------------------------------------------------------------------

if not session_id:
    _record = {
        "ts": ts,
        "session_id": "",
        "event": "session_id_missing",
        "hook": hook_event or "unknown",
    }
    with open(audit_file, "a", encoding="utf-8") as f:
        f.write(json.dumps(_record, ensure_ascii=False) + "\n")
    sys.exit(0)

sid_prefix = session_id[:8]

# ---------------------------------------------------------------------------
# 민감정보 redaction
# ---------------------------------------------------------------------------

# Step 0: Authorization 헤더 — 스킴 무관하게 헤더 값 전체를 마스킹한다.
#   "Authorization: Basic dXNlcjpwYXNz" → "Authorization: ***"
#   "Authorization: Bearer eyJ...", "Authorization: ApiKey abc", "Authorization: JWT xyz" 모두 포함.
#   HTTP 헤더("Authorization: <value>")와 JSON/env("authorization": "<value>") 형태 모두 처리.
_AUTH_HEADER_RE = re.compile(
    r"(?i)(Authorization\s*[:\s\"']+)\S+(?:\s+\S+)*"
)

# Step 1: Bearer <token> — authorization 키워드가 Bearer를 값으로 소비하면
#   실제 토큰이 남는 버그를 차단한다. Bearer 뒤 토큰을 선행 마스킹한다.
_BEARER_RE = re.compile(r"(?i)Bearer\s+([A-Za-z0-9\-_.~+/=]{4,})")

# Step 2: URL userinfo (scheme://user:pass@host)
_URL_USERINFO_RE = re.compile(r"(?i)://([^:@/\s]+):([^@\s]{3,})@")

# Step 3: key=value / key: value / key="value" 형태 키워드 매칭
_REDACT_RE = re.compile(
    r"(?i)(token|secret|password|passwd|authorization|bearer|jwt"
    r"|credential|api[_\-]?key|db_password|jwt_secret)"
    r"([=:\s\"']*)"
    r"([^\s\"'&]{3,})"
)


def redact(text: str, max_len: int = 200) -> str:
    # 0) Authorization 헤더 전체 마스킹 (스킴 무관: Basic, Bearer, ApiKey, JWT 등)
    text = _AUTH_HEADER_RE.sub(lambda m: m.group(1) + "***", text)
    # 1) 잔여 Bearer 토큰 선행 마스킹
    text = _BEARER_RE.sub("Bearer ***", text)
    # 2) URL userinfo (user:pass@host)
    text = _URL_USERINFO_RE.sub(lambda m: f"://{m.group(1)}:***@", text)
    # 3) 나머지 keyword=value 패턴
    text = _REDACT_RE.sub(lambda m: m.group(1) + m.group(2) + "***", text)
    if len(text) > max_len:
        text = text[:max_len] + "…"
    return text

# ---------------------------------------------------------------------------
# 태깅
# ---------------------------------------------------------------------------

_DANGEROUS = [
    re.compile(r"git\s+reset\s+--hard"),
    re.compile(r"git\s+push\s+(-f\b|--force\b|--force-with-lease\b)"),
    re.compile(r"git\s+checkout\s+\."),
    re.compile(r"git\s+checkout\s+--\s+"),
    re.compile(r"git\s+restore\b"),
    re.compile(r"git\s+clean\s+\S*f"),
]
# rm -rf 는 위험 경로에 한해 dangerous
_RM_DANGEROUS_PATH = re.compile(
    r"rm\s+-[rf]{1,2}\s+.*?(src|\.git|backend|admin|scripts)\b"
)
_GIT_WRITE = re.compile(r"\bgit\s+(commit|push|merge)\b")
_TEST = re.compile(r"gradlew.*test|run-module-tests")
_BUILD = re.compile(r"gradlew.*(build|compileJava)")


def tag_bash(cmd_raw: str) -> list[str]:
    tags: list[str] = []
    if any(p.search(cmd_raw) for p in _DANGEROUS) or _RM_DANGEROUS_PATH.search(cmd_raw):
        tags.append("dangerous")
    if _GIT_WRITE.search(cmd_raw):
        tags.append("git_write")
    if _TEST.search(cmd_raw):
        tags.append("test")
    if _BUILD.search(cmd_raw):
        tags.append("build")
    return tags


_PROTECTED_BRANCHES = {"develop", "main", "master"}


def current_branch() -> str:
    try:
        r = subprocess.run(
            ["git", "-C", str(ROOT), "rev-parse", "--abbrev-ref", "HEAD"],
            capture_output=True, text=True, timeout=3,
        )
        return r.stdout.strip()
    except Exception:
        return ""


def tag_file(file_path: str) -> list[str]:
    tags: list[str] = []
    branch = current_branch()
    if branch in _PROTECTED_BRANCHES:
        tags.append("workflow_risk")
        if "/src/main/" in file_path:
            tags.append("operational_code_on_protected_branch")
    return tags


# ---------------------------------------------------------------------------
# 이벤트 생성
# ---------------------------------------------------------------------------

record: dict = {"ts": ts, "session_id": session_id, "sid_prefix": sid_prefix}

if hook_event == "PostToolUse" and tool_name == "Bash":
    cmd_raw: str = (data.get("tool_input") or {}).get("command", "")
    record.update(
        {
            "event": "bash",
            "cmd": redact(cmd_raw),
            "tags": tag_bash(cmd_raw),
        }
    )

elif hook_event == "PostToolUse" and tool_name in ("Edit", "Write"):
    file_path: str = (data.get("tool_input") or {}).get("file_path", "")
    record.update(
        {
            "event": "file_write",
            "file": file_path,
            "tags": tag_file(file_path),
        }
    )

elif hook_event == "Stop":
    record.update(
        {
            "event": "stop",
            "stop_hook_active": data.get("stop_hook_active", False),
            "tags": [],
        }
    )

else:
    record.update(
        {
            "event": "unknown",
            "hook": hook_event,
            "tool": tool_name,
            "tags": [],
        }
    )

# ---------------------------------------------------------------------------
# 기록
# ---------------------------------------------------------------------------

with open(audit_file, "a", encoding="utf-8") as f:
    f.write(json.dumps(record, ensure_ascii=False) + "\n")
