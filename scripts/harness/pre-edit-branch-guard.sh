#!/bin/bash
# PreToolUse(Edit|Write) 가드 — 이슈·브랜치 없이 현재 브랜치에 바로 코드를 얹는 것을 막는다.
#
# 두 가지를 본다:
#   1) main/develop 위에서의 코드 수정  → 무조건 차단 (해제 불가)
#   2) 세션이 이 브랜치를 아직 확인하지 않음 → 최초 1회 차단 (ack 파일로 해제)
#
# Silent Success: 통과는 exit 0 무출력, 차단은 stderr + exit 2.
# ack 는 (세션 × 브랜치) 단위라 세션 중 브랜치를 바꾸면 다시 한 번 묻는다.

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
STATE_DIR="$ROOT_DIR/.claude/state"

input=$(cat)

# on-file-edit.sh 와 같은 방식 — python3 1회로 두 값을 NUL 구분해 뽑는다.
# 개행 구분은 경로에 개행이 든 경우 잘려 나가 가드가 조용히 건너뛰어진다.
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
parse_rc=$?

# 파싱 실패는 통과시킨다. 이 훅은 사고 방지용이지 보안 경계가 아니고,
# 여기서 fail-closed 하면 파싱이 깨지는 순간 편집이 전면 중단된다.
# (시크릿·파괴적 명령의 fail-closed 는 pre-bash-guard.sh 담당)
[[ $parse_rc -ne 0 || -z "$file_path" ]] && exit 0

# 저장소 밖 파일(스크래치패드 등)은 대상이 아니다
case "$file_path" in
  "$ROOT_DIR"/*) rel="${file_path#$ROOT_DIR/}" ;;
  *) exit 0 ;;
esac

# 작업 기록·메모 성격의 경로는 이슈 대상이 아니다
case "$rel" in
  apiDocs/*|.claude/*|.agents/*|*.log) exit 0 ;;
esac

branch="$(git -C "$ROOT_DIR" branch --show-current 2>/dev/null)"
[[ -z "$branch" ]] && exit 0   # detached HEAD·git 아님 → 판단 불가, 통과

if [[ "$branch" == "main" || "$branch" == "develop" ]]; then
  cat >&2 <<MSG
차단: '$branch' 브랜치에서 코드를 수정하려 했습니다. ($rel)

작업 순서를 지키세요 — develop 최신화 → 이슈 생성 → feature/{이슈번호} 브랜치.
  /issue 스킬을 실행하면 위 절차가 그대로 진행됩니다.

이 차단은 해제할 수 없습니다. 브랜치를 먼저 만드세요.
MSG
  exit 2
fi

# (세션 × 브랜치) ack 확인
#
# 슬러그만 쓰면 안 된다. tr 이 '/' 를 '-' 로 바꾸므로 feature/foo 와 feature-foo 가
# 같은 파일명이 되고, 같은 세션에서 한쪽을 ack 한 뒤 다른 쪽으로 전환하면 확인 없이
# 통과한다. 가드가 막으려던 드리프트가 그대로 일어난다.
# 그래서 전체 브랜치명의 체크섬을 함께 붙인다. 슬러그는 .claude/state/ 를 눈으로
# 훑을 때 어느 브랜치인지 알아보려고 남긴다.
# session_id 는 앞 8자만 쓴다. on-stop.sh 의 codex-round-<sid8>.json 과 맞춘 것이고,
# 한 머신에서 동시에 도는 세션 수를 감안하면 UUID 앞 8자로 충분하다.
mkdir -p "$STATE_DIR"
branch_slug="$(printf '%s' "$branch" | tr -c 'A-Za-z0-9._-' '-')"
branch_sum="$(printf '%s' "$branch" | cksum | cut -d' ' -f1)"
ack="$STATE_DIR/branch-ack-${session_id:0:8}-${branch_slug}-${branch_sum}.txt"
[[ -f "$ack" ]] && exit 0

cat >&2 <<MSG
차단: 이 세션은 아직 '$branch' 에서 작업하기로 확인한 적이 없습니다. ($rel)

지금 하려는 게 둘 중 무엇인지 정하고 진행하세요.

  1) 새 작업이다 → /issue 스킬로 이슈와 feature/{번호} 브랜치를 만든 뒤 그쪽에서 수정합니다.

  2) '$branch' 가 맡은 작업을 이어서 하는 게 맞다 → 아래로 한 번만 확인하고 계속합니다.
     echo "<이 브랜치에서 계속하는 이유 한 줄>" > "$ack"

사용자에게 묻지 않고 2번을 고르지 마세요. 판단이 서지 않으면 사용자에게 확인하세요.
MSG
exit 2
