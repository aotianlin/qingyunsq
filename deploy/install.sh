#!/usr/bin/env bash
set -euo pipefail

echo "========================================="
echo "  CampusForum 一键部署脚本"
echo "========================================="
echo ""
echo "🔥 2026-05-22 安全加固部署须知"
echo "-----------------------------------------"
echo "本次部署引入了 security-hardening 安全加固，"
echo "启动前需要先应用 db/migrations/V20260522_*.sql 数据库迁移。"
echo "本脚本会在 docker compose 启动前自动检测迁移是否已应用；"
echo "如未应用会给出清晰的失败提示并中止。"
echo "完整说明见: deploy/SECURITY.md §8"
echo "-----------------------------------------"
echo ""

# 检查 Docker
if ! command -v docker &>/dev/null; then
    echo "[ERROR] 请先安装 Docker 20.10+"
    exit 1
fi

if ! command -v docker compose &>/dev/null; then
    echo "[ERROR] 请先安装 Docker Compose v2+"
    exit 1
fi

# 检查 .env
if [ ! -f .env ]; then
    echo "[INFO] 未找到 .env 文件，正在从 .env.example 复制..."
    cp .env.example .env

    # ===== TDEPLOY.3：自动生成 SIGNED_URL_SECRET / CRYPTO_MASTER_KEY =====
    # 仅当占位值仍含 "ChangeMe" / "please-generate" 时才覆盖；运维已手填的值不动。
    if command -v openssl &>/dev/null; then
        AUTO_SIGNED_URL_SECRET=$(openssl rand -base64 48 | tr -d '\n')
        AUTO_CRYPTO_MASTER_KEY=$(openssl rand -base64 48 | tr -d '\n')
        # 用 sed 替换"包含 ChangeMe 的行"，避免覆盖运维的真实配置
        if grep -qE '^SIGNED_URL_SECRET=.*ChangeMe|^SIGNED_URL_SECRET=.*please-generate' .env; then
            sed -i.bak "s|^SIGNED_URL_SECRET=.*$|SIGNED_URL_SECRET=${AUTO_SIGNED_URL_SECRET}|" .env
            echo "[INFO] 已自动生成 SIGNED_URL_SECRET（48 字节随机串）"
        fi
        if grep -qE '^CRYPTO_MASTER_KEY=.*ChangeMe|^CRYPTO_MASTER_KEY=.*please-generate' .env; then
            sed -i.bak "s|^CRYPTO_MASTER_KEY=.*$|CRYPTO_MASTER_KEY=${AUTO_CRYPTO_MASTER_KEY}|" .env
            echo "[INFO] 已自动生成 CRYPTO_MASTER_KEY（48 字节随机串）"
        fi
        rm -f .env.bak
    else
        echo "[WARN] 未检测到 openssl，无法自动生成 SIGNED_URL_SECRET / CRYPTO_MASTER_KEY"
        echo "        请手动生成并写入 .env 后再次运行：openssl rand -base64 48"
    fi

    echo "[WARN] 请编辑 .env 文件，按 deploy/SECURITY.md 修改其它默认密码（MySQL / Redis / MinIO / MeiliSearch 等）后重新运行本脚本"
    exit 0
fi

# 加载环境变量
set -a
source .env
set +a

# 必填项校验：缺一不可。这些变量在 application-prod.yml 中没有兜底默认值，
# 一旦缺失，后端启动时占位符无法解析，会直接报错。
REQUIRED_VARS=(
  MYSQL_USER MYSQL_PASSWORD MYSQL_DATABASE
  REDIS_PASSWORD
  MINIO_ACCESS_KEY MINIO_SECRET_KEY
  MEILI_MASTER_KEY
  SIGNED_URL_SECRET
  WS_ALLOWED_ORIGINS
  # 2026-05-22 安全加固新增（缺一启动失败）
  CRYPTO_MASTER_KEY
  CORS_ALLOWED_ORIGINS
)
MISSING=()
for v in "${REQUIRED_VARS[@]}"; do
    if [ -z "${!v:-}" ]; then
        MISSING+=("$v")
    fi
done
if [ ${#MISSING[@]} -gt 0 ]; then
    echo "[ERROR] 以下必填变量未设置（详见 deploy/SECURITY.md）："
    for v in "${MISSING[@]}"; do echo "  - $v"; done
    exit 1
fi

# 长度校验：CRYPTO_MASTER_KEY 必须 ≥ 32 字节，否则启动会被 SecurityStartupValidator 拦截
if [ "${#CRYPTO_MASTER_KEY}" -lt 32 ]; then
    echo "[ERROR] CRYPTO_MASTER_KEY 长度 ${#CRYPTO_MASTER_KEY} 字节不足 32 字节"
    echo "        建议生成方式：openssl rand -base64 48"
    exit 1
fi

# 弱默认值检测：避免使用示例配置中的占位符上线
WEAK_PATTERNS=("ChangeMe" "minioadmin" "masterKey" "please-generate" "please-override")
for v in MYSQL_PASSWORD REDIS_PASSWORD MINIO_ACCESS_KEY MINIO_SECRET_KEY MEILI_MASTER_KEY SIGNED_URL_SECRET CRYPTO_MASTER_KEY; do
    val="${!v:-}"
    for pat in "${WEAK_PATTERNS[@]}"; do
        if [[ "$val" == *"$pat"* ]]; then
            echo "[ERROR] $v 仍为示例占位值（包含 \"$pat\"），请改为强随机串后再部署"
            exit 1
        fi
    done
done

# 数据库迁移检查：V20260522_02 加入了 file_sha256 列，未应用则后端启动后上传会出错
echo "[INFO] 检查数据库迁移是否已应用..."
SQL_CHECK="SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${MYSQL_DATABASE}' AND table_name='resources' AND column_name='file_sha256';"
HAS_SHA256=$(docker compose exec -T mysql mysql -u"${MYSQL_USER}" -p"${MYSQL_PASSWORD}" "${MYSQL_DATABASE}" -sse "${SQL_CHECK}" 2>/dev/null || echo "0")
if [ "${HAS_SHA256}" != "1" ]; then
    echo "[ERROR] 数据库迁移未应用：resources.file_sha256 列不存在"
    echo "        请先执行以下命令再重新运行本脚本（详见 db/migrations/README.md）："
    echo "        docker compose exec -T mysql mysql -u\$MYSQL_USER -p\$MYSQL_PASSWORD \$MYSQL_DATABASE \\"
    echo "          < db/migrations/V20260522_01__reset_token_hash.sql"
    echo "        docker compose exec -T mysql mysql -u\$MYSQL_USER -p\$MYSQL_PASSWORD \$MYSQL_DATABASE \\"
    echo "          < db/migrations/V20260522_02__resource_sha256.sql"
    echo ""
    echo "        如果 mysql 容器尚未启动（首次部署），可临时跳过本检查："
    echo "        SKIP_MIGRATION_CHECK=1 ./install.sh"
    if [ "${SKIP_MIGRATION_CHECK:-0}" != "1" ]; then
        exit 1
    fi
    echo "[WARN] 跳过迁移检查（SKIP_MIGRATION_CHECK=1），请在容器启动后立即手动应用迁移"
fi

echo "[INFO] 拉取 Docker 镜像..."
docker compose pull

echo "[INFO] 构建应用镜像..."
docker compose build app

echo "[INFO] 启动服务..."
docker compose up -d

echo "[INFO] 等待服务就绪..."
sleep 10

# 健康检查
if curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "[OK] 应用启动成功!"
else
    echo "[WARN] 应用可能还在启动中，请稍后检查日志"
fi

echo ""
echo "========================================="
echo "  部署完成！"
echo "  访问地址: http://${APP_DOMAIN:-localhost}"
echo "  MinIO 控制台: http://localhost:9001"
echo "========================================="
