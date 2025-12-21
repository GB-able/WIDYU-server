#!/bin/bash

# Docker 리소스 정리 스크립트
set -e

echo "🧹 Cleaning up Docker resources..."
echo ""
echo "This will:"
echo "  - Stop and remove all WIDYU containers"
echo "  - Remove all WIDYU volumes (⚠️  DATABASE DATA WILL BE LOST)"
echo "  - Remove WIDYU images"
echo "  - Clean up unused Docker resources"
echo ""
read -p "Are you sure? (yes/no): " confirm

if [ "$confirm" != "yes" ]; then
    echo "❌ Cleanup cancelled"
    exit 0
fi

# 개발 환경 중지 및 제거
if [ -f docker-compose.dev.yml ]; then
    echo "🔄 Removing dev environment..."
    docker compose -f docker-compose.yml -f docker-compose.dev.yml down -v 2>/dev/null || true
fi

# 프로덕션 환경 중지 및 제거
if [ -f docker-compose.prod.yml ]; then
    echo "🔄 Removing prod environment..."
    docker compose -f docker-compose.yml -f docker-compose.prod.yml down -v 2>/dev/null || true
fi

# WIDYU 이미지 제거
echo "🗑️  Removing images..."
docker images | grep widyu | awk '{print $3}' | xargs -r docker rmi -f 2>/dev/null || true

# 사용하지 않는 리소스 정리
echo "🧹 Cleaning up unused resources..."
docker system prune -f

echo ""
echo "✅ Cleanup complete!"
echo ""
echo "📊 Remaining Docker resources:"
docker system df
