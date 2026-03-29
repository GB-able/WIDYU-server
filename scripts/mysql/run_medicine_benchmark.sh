#!/bin/bash

# 의약품 검색 성능 벤치마크: LIKE vs FULLTEXT
# 사용법: ./scripts/mysql/run_medicine_benchmark.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# .env에서 DB 정보 로드
if [ -f "$PROJECT_ROOT/.env" ]; then
    export $(grep -E '^(MYSQL_DATABASE|MYSQL_USER|MYSQL_PASSWORD|MYSQL_ROOT_PASSWORD)=' "$PROJECT_ROOT/.env" | xargs)
fi

DB_NAME="${MYSQL_DATABASE:-widyu}"
DB_USER="${MYSQL_USER:-root}"
DB_PASS="${MYSQL_ROOT_PASSWORD:-${MYSQL_PASSWORD:-}}"
CONTAINER_NAME="widyu-mysql"

echo "=============================================="
echo "  의약품 검색 성능 벤치마크"
echo "  LIKE '%keyword%'  vs  FULLTEXT MATCH AGAINST"
echo "=============================================="
echo ""

# MySQL 컨테이너 실행 확인
if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    echo "❌ MySQL 컨테이너(${CONTAINER_NAME})가 실행 중이지 않습니다."
    echo "   먼저 실행하세요: ./scripts/docker/dev-up.sh"
    exit 1
fi

# FULLTEXT 인덱스 존재 확인
echo "🔍 FULLTEXT 인덱스 확인 중..."
INDEX_EXISTS=$(docker exec "$CONTAINER_NAME" mysql -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" \
    -se "SELECT COUNT(*) FROM information_schema.STATISTICS
         WHERE table_schema = '${DB_NAME}'
           AND table_name = 'medicine'
           AND index_type = 'FULLTEXT';" 2>/dev/null)

if [ "$INDEX_EXISTS" = "0" ]; then
    echo ""
    echo "⚠️  FULLTEXT 인덱스가 없습니다. 먼저 생성합니다..."
    docker exec -i "$CONTAINER_NAME" mysql -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" \
        < "$SCRIPT_DIR/add_medicine_fulltext_index.sql" 2>/dev/null
    echo "✅ FULLTEXT 인덱스 생성 완료"
else
    echo "✅ FULLTEXT 인덱스 확인됨"
fi

echo ""
echo "⏱️  벤치마크 실행 중..."
echo ""

# 벤치마크 실행 및 결과 저장
RESULT=$(docker exec -i "$CONTAINER_NAME" mysql -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" \
    2>/dev/null < "$SCRIPT_DIR/medicine_benchmark.sql")

echo "$RESULT"

echo ""
echo "=============================================="
echo "✅ 벤치마크 완료"
echo "=============================================="
