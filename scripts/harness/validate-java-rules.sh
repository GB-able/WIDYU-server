#!/bin/bash
# WIDYU 코드 규칙 검사 (CLAUDE.md 기반)
# 사용법: bash scripts/harness/validate-java-rules.sh <java-file-path>

FILE="$1"
if [[ ! -f "$FILE" ]]; then exit 0; fi

BASENAME=$(basename "$FILE")
ERRORS=0

echo "[HARNESS] 규칙 검사: ${FILE#*/WIDYU-server/}"

# ──────────────────────────────────────────────
# 규칙 1: 삼항 연산자 금지
# ──────────────────────────────────────────────
TERNARY=$(grep -n ' ? ' "$FILE" \
    | grep -v '^\s*//' \
    | grep -v '^\s*\*' \
    | grep -v '? extends' \
    | grep -v '? super' \
    | grep -v '".*?.*"')
if [[ -n "$TERNARY" ]]; then
    echo "❌ [규칙1] 삼항 연산자 금지 → if/else 또는 early return으로 변경"
    echo "$TERNARY" | sed 's/^/   /'
    ((ERRORS++))
fi

# ──────────────────────────────────────────────
# 규칙 2: DTO 직접 생성 금지 (Service/Facade)
# ──────────────────────────────────────────────
if [[ "$BASENAME" == *Service* || "$BASENAME" == *Facade* ]]; then
    DTO_NEW=$(grep -nE 'new [A-Z][a-zA-Z]*(Response|Request|Dto|Res|Req)\(' "$FILE" \
        | grep -v '^\s*//')
    if [[ -n "$DTO_NEW" ]]; then
        echo "❌ [규칙2] DTO 직접 생성 금지 → DTO에 from()/of() 팩토리 메서드 추가"
        echo "$DTO_NEW" | sed 's/^/   /'
        ((ERRORS++))
    fi
fi

# ──────────────────────────────────────────────
# 규칙 3: Controller → Repository 직접 접근 금지
# ──────────────────────────────────────────────
if [[ "$FILE" == *"/controller/"* ]]; then
    REPO_IMPORT=$(grep -n 'import.*\.repository\.' "$FILE" | grep -v '^\s*//')
    if [[ -n "$REPO_IMPORT" ]]; then
        echo "❌ [규칙3] Controller에서 Repository 직접 import 금지 (계층 분리 원칙)"
        echo "$REPO_IMPORT" | sed 's/^/   /'
        ((ERRORS++))
    fi
fi

# ──────────────────────────────────────────────
# 규칙 4: @Entity는 widyu-domain에만 위치
# ──────────────────────────────────────────────
if [[ "$FILE" == *"widyu-api"* ]]; then
    ENTITY=$(grep -n '^@Entity' "$FILE")
    if [[ -n "$ENTITY" ]]; then
        echo "❌ [규칙4] @Entity는 widyu-domain에 위치해야 합니다"
        ((ERRORS++))
    fi
fi

# ──────────────────────────────────────────────
# 규칙 5: Repository는 widyu-api에만 위치
# ──────────────────────────────────────────────
if [[ "$FILE" == *"widyu-domain"* && "$BASENAME" == *Repository* ]]; then
    echo "❌ [규칙5] Repository는 widyu-api에 위치해야 합니다"
    ((ERRORS++))
fi

# ──────────────────────────────────────────────
# 규칙 6: @Async 메서드에 @Transactional 확인
# ──────────────────────────────────────────────
ASYNC_NO_TX=$(awk '
    /^[[:space:]]*@Async/ { found_async=1; next }
    found_async && /^[[:space:]]*@/ { if (!/Transactional/) { print NR": "$0" ← @Async인데 @Transactional 없음"; found_async=0 } next }
    found_async && /^[[:space:]]*(public|protected|private)/ { print NR": "$0" ← @Async인데 @Transactional 없음"; found_async=0 }
    { found_async=0 }
' "$FILE")
if [[ -n "$ASYNC_NO_TX" ]]; then
    echo "⚠️  [규칙6] @Async 메서드에 @Transactional 누락 가능"
    echo "$ASYNC_NO_TX" | sed 's/^/   /'
fi

# ──────────────────────────────────────────────
# 결과
# ──────────────────────────────────────────────
if [[ $ERRORS -gt 0 ]]; then
    echo "⛔ 규칙 위반 ${ERRORS}건 — 수정 필요"
    exit 1
else
    echo "✅ 규칙 검사 통과"
fi
