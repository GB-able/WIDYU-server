#!/usr/bin/env bash
# install-hooks.sh — Codex 용 .agents/skills 를 Claude Code 가 찾도록 심링크를 건다.
#
# 사용: bash scripts/harness/install-hooks.sh
#
# 훅 설정(.claude/settings.json)은 이제 git 으로 추적되므로 복사 단계가 없다.
# clone·pull 하면 아래 훅이 그대로 적용된다:
#   PostToolUse(Edit|Write) → on-file-edit.sh + audit-log.sh
#   PostToolUse(Bash)       → audit-log.sh
#   Stop                    → on-stop.sh + audit-log.sh

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SETTINGS="$ROOT/.claude/settings.json"

mkdir -p "$ROOT/.claude"

# .agents/skills 는 Codex 용 경로라 Claude Code 가 자동 탐색하지 않는다.
# SKILL.md 에 name/description 프론트매터가 이미 있으므로 심링크만 걸면
# 그대로 슬래시 커맨드로 잡힌다. (CLAUDE.md 문장에만 의존하면 로딩이 확률적이다)
if [[ -e "$ROOT/.claude/skills" ]]; then
  echo "ℹ️  스킬 링크 이미 존재: .claude/skills"
else
  ln -s ../.agents/skills "$ROOT/.claude/skills"
  echo "✅ 스킬 연결: .claude/skills → .agents/skills"
fi

if [[ -f "$SETTINGS" ]]; then
  echo "✅ 훅 설정 확인: $SETTINGS (git 추적 파일)"
else
  echo "❌ $SETTINGS 이 없습니다. develop 최신화 후 다시 실행하세요." >&2
  exit 1
fi
