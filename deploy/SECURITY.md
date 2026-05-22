# 部署安全须知（必读）

> 本项目自 commit `39875dc` 起完成了一轮安全加固。生产部署前请务必按本文档配置，否则后端会启动失败或留有可被利用的弱默认值。

## 1. 必填环境变量（缺一会启动失败）

`application-prod.yml` 已移除以下变量的弱默认值，缺失任意一项 Spring 启动会因占位符无法解析直接报错。复制 `deploy/.env.example` 到 `deploy/.env` 后，按需替换：

| 变量 | 说明 | 示例 |
|---|---|---|
| `SPRING_DATASOURCE_URL` | MySQL JDBC URL | `jdbc:mysql://mysql:3306/campus_forum?...` |
| `MYSQL_USER` / `MYSQL_PASSWORD` | 应用账号（不要用 root） | `campusforum` / 强密码 |
| `REDIS_HOST` / `REDIS_PASSWORD` | Redis 主机 + 密码 | `redis` / 强密码 |
| `STORAGE_MINIO_ENDPOINT` | MinIO/S3 端点 | `http://minio:9000` |
| `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | 对象存储凭证（务必非默认值） | 32+ 位随机串 |
| `SEARCH_MEILISEARCH_HOST` | MeiliSearch 端点 | `http://meilisearch:7700` |
| `MEILI_MASTER_KEY` | MeiliSearch 主密钥 | 32+ 位随机串 |
| `SIGNED_URL_SECRET` | 资源下载/预览签名 URL 的 HMAC 密钥（高强度） | 64+ 位随机串 |
| `WS_ALLOWED_ORIGINS` | 允许连接 WebSocket 的来源（CSWSH 防御） | `https://campus.example.edu` |

可选但强烈建议设置：

| 变量 | 默认 | 建议 |
|---|---|---|
| `AI_API_KEY` | 空 | 真正接入 LLM 时再配置 |
| `EMAIL_FROM` / `RESET_LINK_BASE` | localhost https | 改为本校真实域名（https） |
| `OFFICE_PREVIEW_URL` | `http://localhost:8012/onlinePreview` | 内网 kkfileview 地址 |

## 2. 安全相关默认值速查

| 项 | 默认 | 修改入口 |
|---|---|---|
| Sa-Token token 总有效期 | 7 天（604800s） | `sa-token.timeout` |
| Sa-Token 闲置过期 | 4 小时（14400s） | `sa-token.active-timeout` |
| 登录失败锁定 | 5 次 / 15 分钟窗口，锁 15 分钟 | `security.login-lockout.*` |
| 资源签名 URL TTL | 60 秒 | `security.signed-url-ttl-seconds` |
| AI baseUrl 私网拦截 | 启用 | `security.ai-base-url-block-private-network` |
| 可信反向代理 IP | 127/8、10/8、172.16/12、192.168/16 | `security.trusted-proxies` |
| 限流 | 已认证 200/min；登录 10/min；AI 5/min；上传 10/min；忘记密码 5/15min | `rate-limit.*` |
| 管理端点暴露 | `health` 一个，`show-details: never` | `management.endpoints.*` |
| 密码强度 | 8-64 位且必须含字母+数字 | `RegisterRequest` |

## 3. 反向代理 / 网关

- 必须把 nginx / ingress 的真实出口 IP 加入 `security.trusted-proxies`，否则 `X-Forwarded-For` 会被忽略，限流会按代理 IP 单点计数（结果是过严或失效）。
- 强烈建议在 nginx 层另设一道 `/api/v1/auth/login` 限流（如 `limit_req zone=login burst=20 nodelay;`）作为纵深防御。
- 必须屏蔽 `/actuator/**` 暴露给公网，留作运维内网使用。

## 4. 数据库与对象存储

- **不要**沿用 `MINIO_ACCESS_KEY=minioadmin` / `MINIO_SECRET_KEY=minioadmin` 这类默认凭证。
- MySQL 应用账号最小权限：仅授予 `campus_forum` 库的 SELECT/INSERT/UPDATE/DELETE/CREATE/ALTER/INDEX，**不要**用 root。
- Redis 必须开启密码（`REDIS_PASSWORD`），并禁用外网监听；如同主机 docker 网络可省。

## 5. 升级注意

如果你正在从旧版本升级：

- 旧 token 仍可继续使用，直到 7 天后或闲置 4 小时后失效；想立即作废全站 token，重启后端时清掉 Redis 中 `satoken:*` 键即可。
- 旧资源原本通过 `/uploads/<key>` 直链访问 — 该映射已删除，前端改为调 `/api/v1/resources/{id}/signed-url` 拿短期签名。如有外部三方系统硬编码引用 `/uploads/...`，需要同步迁移为签名 URL。
- 旧 AI 配置中 `baseUrl` 若指向私网/本机/链路本地地址，启动后会被拒绝并回退到 mock；请通过 SUPER_ADMIN 后台修改成公网 https 地址。

## 6. 验证

部署完成后建议跑一遍：

```bash
# 1. 触发登录失败锁定
for i in 1 2 3 4 5 6; do curl -X POST .../api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"x@y.com","password":"wrong"}'; done
# 第 6 次起应返回 429

# 2. 资源直链访问应 403
curl -i https://your-domain/uploads/2025-01-01/uuid.pdf  # 期望 404 或 403

# 3. actuator 不在公网
curl -i https://your-domain/actuator/env  # 期望 401/403/404

# 4. 申请签名 URL 后下载
TOKEN=$(curl ... /api/v1/auth/login | jq -r .data.token)
SIG=$(curl -H "Authorization: $TOKEN" /api/v1/resources/1/signed-url?action=download | jq -r .data.token)
curl -i "/api/v1/resources/1/download?sig=$SIG"  # 期望 200，附件流
curl -i "/api/v1/resources/1/download?sig=invalid" # 期望 403
```

## 7. 漏洞反馈

发现安全问题请通过私有渠道（issue 标签 `security` 或邮件）联系维护者，请勿在公开 issue 中披露利用细节。
