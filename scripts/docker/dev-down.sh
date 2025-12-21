#!/bin/bash

# 개발 환경 종료 스크립트
set -e

echo "🛑 Stopping WIDYU Development Environment..."

# 컨테이너 중지 및 제거
docker compose -f docker-compose.yml -f docker-compose.dev.yml down

echo "✅ Development environment stopped!"
echo ""
echo "💡 To remove volumes (database data), run:"
echo "   docker compose -f docker-compose.yml -f docker-compose.dev.yml down -v"
