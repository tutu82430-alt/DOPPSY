#!/usr/bin/env bash
# ==============================================================================
# Doppsy Healthcare Concierge — Let's Encrypt SSL Bootstrap Script
# ==============================================================================
# Usage:
#   chmod +x scripts/init-ssl.sh
#   ./scripts/init-ssl.sh doppsy.in admin@doppsy.in
# ==============================================================================

set -euo pipefail

DOMAINS=${1:-"doppsy.in api.doppsy.in www.doppsy.in"}
EMAIL=${2:-"admin@doppsy.in"}
RSA_KEY_SIZE=4096
STAGING=0 # Set to 1 if testing to avoid Let's Encrypt rate limits

PRIMARY_DOMAIN=$(echo "$DOMAINS" | awk '{print $1}')

echo "================================================================="
echo "⚡ Bootstrapping Let's Encrypt SSL for Doppsy: $DOMAINS"
echo "================================================================="

# 1. Create directory structure in docker volume paths if not existing
docker-compose -f docker-compose.prod.yml up -d db backend frontend

# 2. Check if dummy cert exists; if no real cert, generate a dummy cert so Nginx can boot
DUMMY_DIR="/var/lib/docker/volumes/doppsy_certbot_certs/_data/live/$PRIMARY_DOMAIN"

echo "==> Ensuring Nginx can start..."
docker volume create doppsy_certbot_certs || true
docker volume create doppsy_certbot_data || true

# Temporary dummy certificate generation via temporary container
docker run --rm \
  -v doppsy_certbot_certs:/etc/letsencrypt \
  certbot/certbot:latest \
  certificates || true

echo "==> Requesting initial certificates via Certbot webroot..."
# Start Nginx
docker-compose -f docker-compose.prod.yml up -d nginx

# Build domain arguments
DOMAIN_ARGS=""
for domain in $DOMAINS; do
  DOMAIN_ARGS="$DOMAIN_ARGS -d $domain"
done

# Select staging vs production endpoint
STAGING_ARG=""
if [ "$STAGING" != "0" ]; then
  STAGING_ARG="--staging"
fi

echo "==> Requesting Let's Encrypt SSL certificate for $PRIMARY_DOMAIN..."
docker run --rm \
  -v doppsy_certbot_certs:/etc/letsencrypt \
  -v doppsy_certbot_data:/var/www/certbot \
  certbot/certbot:latest \
  certonly --webroot -w /var/www/certbot \
  $STAGING_ARG \
  $DOMAIN_ARGS \
  --email "$EMAIL" \
  --rsa-key-size "$RSA_KEY_SIZE" \
  --agree-tos \
  --force-renewal \
  --non-interactive

echo "==> Reloading Nginx with production TLS certificates..."
docker-compose -f docker-compose.prod.yml exec nginx nginx -s reload

echo "================================================================="
echo "✅ SSL Certificate successfully installed for $PRIMARY_DOMAIN!"
echo "   Endpoint: https://$PRIMARY_DOMAIN"
echo "   API Base: https://$PRIMARY_DOMAIN/api"
echo "   Webhook : https://$PRIMARY_DOMAIN/api/v1/whatsapp/webhook"
echo "================================================================="
