#!/bin/bash
# 변경된 파일 기준으로 적절한 모듈 테스트 실행
# 사용법: bash scripts/harness/run-module-tests.sh [java-file-path | module-name]

ARG="$1"
ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"

# 모듈 감지
if [[ "$ARG" == *"widyu-domain"* || "$ARG" == "domain" ]]; then
    MODULE=":backend:widyu-domain"
    LABEL="widyu-domain"
elif [[ "$ARG" == *"widyu-api"* || "$ARG" == "api" ]]; then
    MODULE=":backend:widyu-api"
    LABEL="widyu-api"
elif [[ -z "$ARG" ]]; then
    # 인자 없으면 변경된 파일에서 자동 감지
    CHANGED=$(git -C "$ROOT_DIR" diff --name-only HEAD 2>/dev/null)
    if echo "$CHANGED" | grep -q "widyu-domain"; then
        MODULE=":backend:widyu-domain"
        LABEL="widyu-domain (변경 감지)"
    elif echo "$CHANGED" | grep -q "widyu-api"; then
        MODULE=":backend:widyu-api"
        LABEL="widyu-api (변경 감지)"
    else
        MODULE=""
        LABEL="전체"
    fi
else
    MODULE=""
    LABEL="전체"
fi

echo "[HARNESS] 테스트 실행: $LABEL"

cd "$ROOT_DIR" || exit 1

if [[ -n "$MODULE" ]]; then
    ./gradlew "$MODULE:test" --console=plain 2>&1 | tail -30
else
    ./gradlew test --console=plain 2>&1 | tail -30
fi

EXIT_CODE=${PIPESTATUS[0]}

if [[ $EXIT_CODE -eq 0 ]]; then
    echo "✅ 테스트 통과"
else
    echo "❌ 테스트 실패 — 전체 로그: ./gradlew $MODULE:test"
fi

exit $EXIT_CODE
