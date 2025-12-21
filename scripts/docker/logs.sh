#!/bin/bash

# 로그 조회 스크립트
ENV=${1:-dev}
SERVICE=$2

if [ "$ENV" != "dev" ] && [ "$ENV" != "prod" ]; then
    echo "Usage: ./scripts/docker/logs.sh [dev|prod] [service-name]"
    echo ""
    echo "Examples:"
    echo "  ./scripts/docker/logs.sh dev              # All dev logs"
    echo "  ./scripts/docker/logs.sh dev widyu-api    # Only API logs"
    echo "  ./scripts/docker/logs.sh prod nginx       # Production nginx logs"
    exit 1
fi

if [ "$ENV" = "dev" ]; then
    COMPOSE_CMD="docker compose -f docker-compose.yml -f docker-compose.dev.yml"
else
    COMPOSE_CMD="docker compose -f docker-compose.yml -f docker-compose.prod.yml"
fi

if [ -z "$SERVICE" ]; then
    echo "📋 Showing logs for all services (${ENV})..."
    $COMPOSE_CMD logs -f
else
    echo "📋 Showing logs for $SERVICE (${ENV})..."
    $COMPOSE_CMD logs -f "$SERVICE"
fi
