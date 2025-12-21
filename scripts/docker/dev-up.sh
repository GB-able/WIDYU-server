#!/bin/bash

# 개발 환경 시작 스크립트
set -e

echo "🚀 Starting WIDYU Development Environment..."

# .env 파일 존재 여부 확인
if [ ! -f .env ]; then
    echo "⚠️  .env not found!"
    echo "📝 Copying .env.example to .env..."
    cp .env.example .env
    echo "⚠️  Please edit .env with your actual configuration values"
    exit 1
fi

# 컨테이너 빌드 및 시작
echo "🔨 Building Docker images..."
docker compose -f docker-compose.yml -f docker-compose.dev.yml build

echo "🚢 Starting containers..."
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d

# 서비스가 정상 작동할 때까지 대기
echo "⏳ Waiting for services to be healthy..."
sleep 10

# 서비스 상태 확인
echo ""
echo "📊 Service Status:"
docker compose -f docker-compose.yml -f docker-compose.dev.yml ps

echo ""
echo "✅ Development environment is up!"
echo ""
echo "📌 Service URLs:"
echo "   - Application: http://localhost:8080"
echo "   - Nginx: http://localhost"
echo "   - Swagger UI: http://localhost/swagger-ui/index.html"
echo "   - MySQL: localhost:3306"
echo "   - Redis: localhost:6379"
echo ""
echo "📝 Useful commands:"
echo "   - View logs: ./scripts/docker/logs.sh dev"
echo "   - Stop services: ./scripts/docker/dev-down.sh"
echo "   - Restart API: docker compose -f docker-compose.yml -f docker-compose.dev.yml restart widyu-api"
