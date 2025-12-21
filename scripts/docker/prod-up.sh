#!/bin/bash

# 프로덕션 환경 시작 스크립트
set -e

echo "🚀 Starting WIDYU Production Environment..."

# .env 파일 존재 여부 확인
if [ ! -f .env ]; then
    echo "❌ Error: .env not found!"
    echo "📝 Please create .env from .env.example.prod"
    exit 1
fi

# 필수 환경 변수 검증
echo "🔍 Validating environment variables..."
required_vars=(
    "RDS_ENDPOINT"
    "RDS_USERNAME"
    "RDS_PASSWORD"
    "REDIS_PASSWORD"
    "JWT_ACCESS_SECRET"
    "JWT_REFRESH_SECRET"
)

for var in "${required_vars[@]}"; do
    if ! grep -q "^${var}=" .env || grep -q "^${var}=$" .env; then
        echo "❌ Error: ${var} is not set in .env"
        exit 1
    fi
done

# 최신 코드 Pull (CI/CD 환경인 경우)
if [ "$CI" = "true" ]; then
    echo "📦 Pulling latest code..."
    git pull origin main
fi

# Docker 이미지 빌드
echo "🔨 Building Docker image..."
docker compose -f docker-compose.yml -f docker-compose.prod.yml build --no-cache

# 기존 컨테이너 안전하게 종료
if docker compose -f docker-compose.yml -f docker-compose.prod.yml ps -q | grep -q .; then
    echo "🔄 Stopping old containers..."
    docker compose -f docker-compose.yml -f docker-compose.prod.yml down --timeout 30
fi

# 새 컨테이너 시작
echo "🚢 Starting containers..."
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d

# 헬스 체크 대기
echo "⏳ Waiting for health checks..."
sleep 15

# 서비스 상태 확인
echo ""
echo "📊 Service Status:"
docker compose -f docker-compose.yml -f docker-compose.prod.yml ps

# 헬스 체크 검증
echo ""
echo "🏥 Health Check:"
if curl -f http://localhost/actuator/health > /dev/null 2>&1; then
    echo "✅ Application is healthy!"
else
    echo "⚠️  Warning: Health check failed. Check logs:"
    echo "   docker compose -f docker-compose.yml -f docker-compose.prod.yml logs widyu-api"
fi

echo ""
echo "✅ Production environment is up!"
echo ""
echo "📝 Useful commands:"
echo "   - View logs: docker compose -f docker-compose.yml -f docker-compose.prod.yml logs -f"
echo "   - Stop services: ./scripts/docker/prod-down.sh"
echo "   - Restart API: docker compose -f docker-compose.yml -f docker-compose.prod.yml restart widyu-api"
