#!/usr/bin/env bash
set -euo pipefail

echo "========================================="
echo "  CampusForum one-click deployment"
echo "========================================="

if ! command -v docker &>/dev/null; then
    echo "[ERROR] Please install Docker 20.10+ first"
    exit 1
fi

if ! docker compose version &>/dev/null; then
    echo "[ERROR] Please install Docker Compose v2+ first"
    exit 1
fi

if [ ! -f .env ]; then
    echo "[INFO] .env not found, copying from .env.example"
    cp .env.example .env

    if command -v openssl &>/dev/null; then
        AUTO_SIGNED_URL_SECRET=$(openssl rand -base64 48 | tr -d '\n')
        AUTO_CRYPTO_MASTER_KEY=$(openssl rand -base64 48 | tr -d '\n')
        sed -i.bak "s|^SIGNED_URL_SECRET=.*$|SIGNED_URL_SECRET=${AUTO_SIGNED_URL_SECRET}|" .env
        sed -i.bak "s|^CRYPTO_MASTER_KEY=.*$|CRYPTO_MASTER_KEY=${AUTO_CRYPTO_MASTER_KEY}|" .env
        rm -f .env.bak
        echo "[INFO] Generated SIGNED_URL_SECRET and CRYPTO_MASTER_KEY"
    else
        echo "[WARN] openssl not found. Generate secrets manually with: openssl rand -base64 48"
    fi

    echo "[WARN] Edit deploy/.env, replace ChangeMe values, then run this script again"
    exit 0
fi

set -a
source .env
set +a

REQUIRED_VARS=(
  MYSQL_ROOT_PASSWORD MYSQL_USER MYSQL_PASSWORD MYSQL_DATABASE
  REDIS_PASSWORD
  MEILI_MASTER_KEY
  SIGNED_URL_SECRET CRYPTO_MASTER_KEY
  CORS_ALLOWED_ORIGINS WS_ALLOWED_ORIGINS
  EMAIL_FROM RESET_LINK_BASE
)

case "${STORAGE_TYPE:-oss}" in
  oss)
    REQUIRED_VARS+=(STORAGE_OSS_ENDPOINT OSS_ACCESS_KEY OSS_SECRET_KEY OSS_BUCKET)
    ;;
  local)
    REQUIRED_VARS+=(STORAGE_LOCAL_PATH)
    ;;
  *)
    echo "[ERROR] STORAGE_TYPE must be oss or local"
    exit 1
    ;;
esac

MISSING=()
for v in "${REQUIRED_VARS[@]}"; do
    if [ -z "${!v:-}" ]; then
        MISSING+=("$v")
    fi
done

if [ ${#MISSING[@]} -gt 0 ]; then
    echo "[ERROR] Missing required variables:"
    for v in "${MISSING[@]}"; do echo "  - $v"; done
    exit 1
fi

if [ "$(printf '%s' "$CRYPTO_MASTER_KEY" | wc -c)" -lt 32 ]; then
    echo "[ERROR] CRYPTO_MASTER_KEY must be at least 32 bytes"
    exit 1
fi

if [ "$(printf '%s' "$SIGNED_URL_SECRET" | wc -c)" -lt 32 ]; then
    echo "[ERROR] SIGNED_URL_SECRET must be at least 32 bytes"
    exit 1
fi

WEAK_PATTERNS=("ChangeMe" "minioadmin" "masterKey" "please-generate" "please-override")
CHECK_VARS=(MYSQL_ROOT_PASSWORD MYSQL_PASSWORD REDIS_PASSWORD MEILI_MASTER_KEY SIGNED_URL_SECRET CRYPTO_MASTER_KEY)
if [ "${STORAGE_TYPE:-oss}" = "oss" ]; then
    CHECK_VARS+=(OSS_ACCESS_KEY OSS_SECRET_KEY)
fi

for v in "${CHECK_VARS[@]}"; do
    val="${!v:-}"
    for pat in "${WEAK_PATTERNS[@]}"; do
        if [[ "$val" == *"$pat"* ]]; then
            echo "[ERROR] $v still contains placeholder token \"$pat\""
            exit 1
        fi
    done
done

echo "[INFO] Pulling base images..."
docker compose pull --ignore-buildable

echo "[INFO] Building backend image..."
docker compose build app

echo "[INFO] Starting services..."
docker compose up -d

echo "[INFO] Waiting for backend health..."
for i in $(seq 1 30); do
    if curl -sf http://localhost:8080/actuator/health >/dev/null 2>&1; then
        echo "[OK] Backend is healthy"
        break
    fi
    sleep 2
    if [ "$i" = "30" ]; then
        echo "[WARN] Backend is not healthy yet. Check logs with: docker compose logs -f app"
    fi
done

echo ""
echo "========================================="
echo "  Deployment finished"
echo "  Site: http://${APP_DOMAIN:-localhost}"
echo "  Storage mode: ${STORAGE_TYPE:-oss}"
echo "========================================="
