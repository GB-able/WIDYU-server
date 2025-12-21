#!/bin/bash

# SSL 인증서 갱신 스크립트
# 사용법: ./scripts/ssl/certbot-renew.sh
# Cron 설정 예시:
#   0 0 * * * flock -n /tmp/certbot-renew.lock /path/to/widyu/scripts/ssl/certbot-renew.sh >> /var/log/certbot-renew.log 2>&1

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# 로그 시작
echo "=========================================="
echo "SSL 인증서 갱신 체크 시작"
echo "시간: $(date '+%Y-%m-%d %H:%M:%S')"
echo "=========================================="

cd "$PROJECT_ROOT"

# Certbot을 사용하여 인증서 갱신 (만료 30일 이내만 갱신)
echo "🔄 인증서 갱신 체크 중..."

if docker compose run --rm certbot renew \
    --webroot \
    --webroot-path=/var/www/certbot \
    --deploy-hook "echo 'Certificate renewed'"; then

    echo "✅ 인증서 갱신 체크 완료"

    # Nginx 설정 테스트
    echo "🔍 Nginx 설정 테스트..."
    if docker compose exec nginx nginx -t 2>&1; then
        echo "✅ Nginx 설정 정상"

        # Nginx 리로드
        echo "🔄 Nginx 리로드..."
        docker compose exec nginx nginx -s reload
        echo "✅ Nginx 리로드 완료!"
    else
        echo "❌ Nginx 설정 오류! 리로드 생략"
        exit 1
    fi
else
    echo "⚠️  인증서 갱신 실패"
    exit 1
fi

echo "=========================================="
echo "완료: $(date '+%Y-%m-%d %H:%M:%S')"
echo "=========================================="
