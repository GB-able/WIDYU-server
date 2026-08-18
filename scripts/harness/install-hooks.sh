#!/usr/bin/env bash
# install-hooks.sh — .claude/settings.json 에 harness 훅을 설치한다.
#
# 사용: bash scripts/harness/install-hooks.sh [--force]
#   --force : 기존 settings.json 을 덮어쓴다 (기본은 이미 있으면 건너뜀)
#
# 이 PR 에서 추가된 훅 구성:
#   PostToolUse(Edit|Write) → on-file-edit.sh + audit-log.sh
#   PostToolUse(Bash)       → audit-log.sh
#   Stop                    → on-stop.sh + audit-log.sh
#
# .claude/ 는 .gitignore 대상이므로 settings.json 을 직접 추적할 수 없다.
# 이 스크립트를 실행해 templates/settings-template.json 을 .claude/settings.json 으로 설치한다.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TEMPLATE="$ROOT/scripts/harness/settings-template.json"
TARGET="$ROOT/.claude/settings.json"
FORCE="${1:-}"

if [[ ! -f "$TEMPLATE" ]]; then
  echo "❌ 템플릿 없음: $TEMPLATE" >&2
  exit 1
fi

mkdir -p "$ROOT/.claude"

# .agents/skills 는 Codex 용 경로라 Claude Code 가 자동 탐색하지 않는다.
# SKILL.md 에 name/description 프론트매터가 이미 있으므로 심링크만 걸면
# 그대로 슬래시 커맨드로 잡힌다. (CLAUDE.md 문장에만 의존하면 로딩이 확률적이다)
# settings.json 존재 여부와 무관하게 걸어야 한다 — 아래 조기 종료보다 앞에 둔다.
if [[ ! -e "$ROOT/.claude/skills" ]]; then
  ln -s ../.agents/skills "$ROOT/.claude/skills"
  echo "✅ 스킬 연결: .claude/skills → .agents/skills"
fi

if [[ -f "$TARGET" && "$FORCE" != "--force" ]]; then
  echo "ℹ️  $TARGET 이미 존재합니다. 덮어쓰려면 --force 옵션을 사용하세요."
  echo "   현재 설정 유지."
  exit 0
fi

cp "$TEMPLATE" "$TARGET"
echo "✅ 훅 설치 완료: $TARGET"
echo ""
echo "   활성화된 훅:"
echo "   • PostToolUse(Edit|Write) → on-file-edit.sh (규칙 검사) + audit-log.sh"
echo "   • PostToolUse(Bash)       → audit-log.sh (명령 기록)"
echo "   • Stop                    → on-stop.sh (Codex 검수) + audit-log.sh"
