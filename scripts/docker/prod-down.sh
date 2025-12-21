#!/bin/bash

# 프로덕션 환경 종료 스크립트
set -e

echo "🛑 Stopping WIDYU Production Environment..."

# 컨테이너 안전하게 종료
docker compose -f docker-compose.yml -f docker-compose.prod.yml down --timeout 30

echo "✅ Production environment stopped!"
echo ""
echo "💡 Note: Volumes are preserved. To remove them, run:"
echo "   docker compose -f docker-compose.yml -f docker-compose.prod.yml down -v"
