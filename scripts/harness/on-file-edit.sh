#!/bin/bash
# PostToolUse hook: Edit/Write 도구 사용 후 Java 파일 규칙 검사
# 입력: stdin으로 JSON (tool_name, tool_input 포함)

input=$(cat)
file_path=$(echo "$input" | jq -r '.tool_input.file_path // ""' 2>/dev/null)

# Java 파일이 아니거나 테스트/generated 경로면 스킵
if [[ "$file_path" != *.java ]]; then exit 0; fi
if [[ "$file_path" == */test/* ]] || [[ "$file_path" == */generated/* ]]; then exit 0; fi
if [[ "$file_path" != */main/java/* ]]; then exit 0; fi

exec bash "$(dirname "$0")/validate-java-rules.sh" "$file_path"
