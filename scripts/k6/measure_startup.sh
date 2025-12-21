#!/bin/bash

# 애플리케이션 기동 시간 측정 스크립트
# 사용법: ./measure_startup.sh <image_name> <port>

set -e

IMAGE_NAME=${1:-app-base}
PORT=${2:-8080}
CONTAINER_NAME="startup-test-$$"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="$PROJECT_ROOT/.env.benchmark"

echo "=========================================="
echo "Measuring startup time for: $IMAGE_NAME"
echo "Port: $PORT"
echo "=========================================="

# 환경 변수 파일 존재 여부 확인
if [ ! -f "$ENV_FILE" ]; then
  echo "⚠️  Warning: .env.benchmark file not found at $ENV_FILE"
  echo "Creating container without environment variables..."
fi

# 컨테이너 시작 시간 기록
START_TIME=$(date +%s%N)

# 컨테이너 실행
ENV_FILE_FLAG=""
if [ -f "$ENV_FILE" ]; then
  ENV_FILE_FLAG="--env-file $ENV_FILE"
fi

docker run -d --rm \
  --name "$CONTAINER_NAME" \
  --memory=2000m \
  --cpus=1 \
  -p "$PORT:8080" \
  $ENV_FILE_FLAG \
  "$IMAGE_NAME" > /dev/null

echo "Container started. Waiting for application to be ready..."

# 헬스 체크 루프
MAX_WAIT=120  # 최대 120초 대기
ELAPSED=0
HEALTH_URL="http://localhost:$PORT/test"

while [ $ELAPSED -lt $MAX_WAIT ]; do
  sleep 0.5
  ELAPSED=$((ELAPSED + 1))

  # 헬스 체크 수행
  if curl -sf "$HEALTH_URL" > /dev/null 2>&1; then
    END_TIME=$(date +%s%N)
    STARTUP_TIME=$(echo "scale=3; ($END_TIME - $START_TIME) / 1000000000" | bc)

    echo ""
    echo "✅ Application is ready!"
    echo "Startup time: ${STARTUP_TIME}s"
    echo ""

    # 로그에서 Spring Boot 기동 시간 추출
    SPRING_STARTUP=$(docker logs "$CONTAINER_NAME" 2>&1 | grep "Started" | grep -oE '[0-9]+\.[0-9]+ seconds' | head -1 || echo "N/A")
    echo "Spring Boot reports: $SPRING_STARTUP"

    # 컨테이너 정리
    docker stop "$CONTAINER_NAME" > /dev/null
    exit 0
  fi
done

echo ""
echo "❌ Timeout: Application did not start within ${MAX_WAIT} seconds"
docker stop "$CONTAINER_NAME" > /dev/null
exit 1