#!/bin/bash

# JVM 최적화 전/후 성능 벤치마크 실행 스크립트
# 사용법: ./run_benchmark.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
K6_SCRIPT="$PROJECT_ROOT/k6/load-test.js"
ENV_FILE="$PROJECT_ROOT/.env.benchmark"

echo "=============================================="
echo "JVM Optimization Benchmark"
echo "=============================================="
echo ""

# 환경 변수 파일 존재 여부 확인
if [ ! -f "$ENV_FILE" ]; then
  echo "⚠️  Warning: .env.benchmark file not found!"
  echo "Path: $ENV_FILE"
  echo ""
  echo "Please create .env.benchmark file with required environment variables."
  echo "See .env.benchmark for template."
  echo ""
  read -p "Continue without environment variables? (y/N): " -n 1 -r
  echo ""
  if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    exit 1
  fi
fi
echo ""

# 1단계: Docker 이미지 빌드
echo "📦 Step 1: Building Docker images..."
echo ""

cd "$PROJECT_ROOT/backend/widyu-api"

echo "Building baseline image (Dockerfile.base)..."
docker build -t widyu-app-base -f Dockerfile.base .

echo "Building optimized image (Dockerfile.opt)..."
docker build -t widyu-app-opt -f Dockerfile.opt .

echo "✅ Images built successfully"
echo ""

# 2단계: 기동 시간 측정
echo "⏱️  Step 2: Measuring startup times..."
echo ""

echo "--- Baseline ---"
bash "$SCRIPT_DIR/measure_startup.sh" widyu-app-base 8080

sleep 3

echo ""
echo "--- Optimized ---"
bash "$SCRIPT_DIR/measure_startup.sh" widyu-app-opt 8081

echo ""
echo "=============================================="
echo "📊 Step 3: Running load tests..."
echo "=============================================="
echo ""

# 3-1단계: Baseline 부하 테스트
echo "--- Testing Baseline (port 8080) ---"
echo ""

ENV_FILE_FLAG=""
if [ -f "$ENV_FILE" ]; then
  ENV_FILE_FLAG="--env-file $ENV_FILE"
fi

docker run -d --rm \
  --name widyu-app-base-test \
  --memory=2000m \
  --cpus=1 \
  -p 8080:8080 \
  $ENV_FILE_FLAG \
  widyu-app-base

# 애플리케이션이 완전히 시작될 때까지 대기
echo "Waiting for application to start..."
sleep 15

# 헬스 체크
until curl -sf http://localhost:8080/test > /dev/null 2>&1; do
  echo "Waiting for health check..."
  sleep 2
done

echo "Running k6 load test..."
BASE_URL=http://localhost:8080 k6 run "$K6_SCRIPT" | tee /tmp/k6-baseline.txt

echo ""
echo "Collecting Actuator metrics..."
echo "GC Pause:"
curl -s http://localhost:8080/actuator/metrics/jvm.gc.pause | jq '.measurements[] | select(.statistic=="TOTAL_TIME") | .value'
echo "Memory Used:"
curl -s http://localhost:8080/actuator/metrics/jvm.memory.used | jq '.measurements[0].value'

docker stop widyu-app-base-test
sleep 3

echo ""
echo "--- Testing Optimized (port 8081) ---"
echo ""

docker run -d --rm \
  --name widyu-app-opt-test \
  --memory=512m \
  --cpus=1 \
  -p 8081:8080 \
  $ENV_FILE_FLAG \
  widyu-app-opt

echo "Waiting for application to start..."
sleep 15

until curl -sf http://localhost:8081/test > /dev/null 2>&1; do
  echo "Waiting for health check..."
  sleep 2
done

echo "Running k6 load test..."
BASE_URL=http://localhost:8081 k6 run "$K6_SCRIPT" | tee /tmp/k6-optimized.txt

echo ""
echo "Collecting Actuator metrics..."
echo "GC Pause:"
curl -s http://localhost:8081/actuator/metrics/jvm.gc.pause | jq '.measurements[] | select(.statistic=="TOTAL_TIME") | .value'
echo "Memory Used:"
curl -s http://localhost:8081/actuator/metrics/jvm.memory.used | jq '.measurements[0].value'

docker stop widyu-app-opt-test

echo ""
echo "=============================================="
echo "✅ Benchmark completed!"
echo "=============================================="
echo ""
echo "Results summary:"
echo "  Baseline results: /tmp/k6-baseline.txt"
echo "  Optimized results: /tmp/k6-optimized.txt"
echo ""
echo "Compare the results to measure the impact of JVM optimization."