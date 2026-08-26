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
# 파일명 키는 전체 session_id 와 전체 branch 를 함께 해시해서 만든다.
# 어느 쪽이든 잘라 쓰면 서로 다른 (세션, 브랜치) 조합이 한 파일을 공유할 수 있고,
# 그러면 확인 없이 통과해 이 가드가 막으려는 드리프트가 그대로 일어난다.
#   - 슬러그만 쓰면: tr 이 '/' 를 '-' 로 바꿔 feature/foo 와 feature-foo 가 같은 파일.
#   - session_id 앞 8자만 쓰면: ack 파일이 세션 종료 후에도 디스크에 남으므로,
#     앞 8자가 겹치는 옛 세션의 ack 를 새 세션이 물려받는다.
# on-stop.sh 의 codex-round-<sid8>.json 은 앞 8자를 쓰지만 그건 라운드 카운터라
# 충돌해도 카운터를 공유하는 정도다. 여기는 게이트 우회라 위험도가 다르다.
#
# 해시는 sha256 앞 16자(64비트)를 쓴다. 적대적 상황이 아니고 파일도 이 머신에만
# 있으므로 64비트로 충분하다. 슬러그를 앞에 남기는 건 .claude/state/ 를 눈으로
# 훑을 때 어느 브랜치 것인지 알아보기 위해서다(차단 메시지가 이 경로를 안내한다).
#
# python3 호출이 하나 늘지만, 여기까지 오는 건 제외 경로·보호 브랜치를 모두
# 통과한 편집뿐이라 대부분의 호출은 그 전에 끝난다.
mkdir -p "$STATE_DIR"
branch_slug="$(printf '%s' "$branch" | tr -c 'A-Za-z0-9._-' '-')"
ack_key="$(printf '%s\0%s' "$session_id" "$branch" | python3 -c '
import hashlib, sys
print(hashlib.sha256(sys.stdin.buffer.read()).hexdigest()[:16])
' 2>/dev/null)"

# 해시를 못 구하면 ack 를 신뢰할 수 없다. 조용히 통과시키지 말고 차단한다.
if [[ -z "$ack_key" ]]; then
  echo "차단: ack 키를 생성하지 못했습니다(python3 확인 필요). 안전을 위해 편집을 막습니다." >&2
  exit 2
fi

ack="$STATE_DIR/branch-ack-${branch_slug}-${ack_key}.txt"
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
