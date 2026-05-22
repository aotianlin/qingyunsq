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


---

## 8. 2026-05-22 安全加固（security-hardening spec）

> 本节记录第二轮安全审计后实施的 32 项加固变更，覆盖凭证管理、注入防护、SSRF、XSS、限流、文件上传、部署强化等方面。spec 文件：[`.kiro/specs/security-hardening/`](../.kiro/specs/security-hardening/)。

### 8.1 部署前必读清单

按以下顺序执行，跳步会导致后端启动失败或运行时异常：

#### 步骤 1：应用数据库迁移

```bash
# 在能够访问 MySQL 的运维主机上执行（路径相对仓库根）
mysql -u $MYSQL_USER -p$MYSQL_PASSWORD $MYSQL_DATABASE \
  < db/migrations/V20260522_01__reset_token_hash.sql

mysql -u $MYSQL_USER -p$MYSQL_PASSWORD $MYSQL_DATABASE \
  < db/migrations/V20260522_02__resource_sha256.sql
```

详细脚本说明：[`db/migrations/README.md`](../db/migrations/README.md)。

⚠️ **业务影响**：
- `V20260522_01` 会清空所有现存的 `users.reset_token`，进行中的"忘记密码"流程链接会失效，需要用户重新申请
- `V20260522_02` 仅 ALTER ADD COLUMN，无数据影响

#### 步骤 2：配置新增的必填环境变量

复制 `deploy/.env.example` 到 `deploy/.env`，填写以下**新增**变量（缺失会导致 Spring 启动失败）：

| 变量 | 说明 | 推荐生成方式 |
|------|------|--------------|
| `CRYPTO_MASTER_KEY` | AES-GCM 主密钥（≥ 32 字节随机串），用于加密租户 AI API Key | `openssl rand -base64 48` |
| `CORS_ALLOWED_ORIGINS` | CORS 跨域来源白名单，逗号分隔 | `https://campus.example.edu,https://m.example.edu` |

可选变量（有合理默认值，按需调整）：

| 变量 | 默认 | 建议 |
|------|------|------|
| `ALLOWED_ASSET_HOSTS` | 空（不校验） | `cdn.example.edu,storage.example.edu`（限制头像 URL 域名） |
| `WS_TICKET_ENFORCED` | `false` | 部署一周观察前端兼容情况后切 `true`，禁用旧 token query 路径 |
| `WS_TICKET_TTL_SECONDS` | 30 | 通常无需调整 |
| `UPLOAD_REAL_MIME_CHECK` | `true` | 保持 true；Tika 检测会增加 ~50ms 上传延迟 |
| `CRYPTO_LEGACY_MODE` | `false` | 紧急回滚开关，仅在新加密路径出问题时临时设 `true` |

#### 步骤 3：启动后端

```bash
cd deploy && docker compose up -d --build app
```

启动失败常见原因：
- `CRYPTO_MASTER_KEY` 长度不足 32 字节 → 启动日志会有 `IllegalStateException: security.crypto.master-key 长度不足 32 字节`
- `CORS_ALLOWED_ORIGINS` 未设置 → Spring 占位符无法解析报错

#### 步骤 4：验证

部署完成后跑下面这组命令验证关键加固生效：

```bash
# 1) 验证 nginx 安全响应头（应有 CSP/HSTS/X-Frame-Options 等）
curl -I https://your-domain/

# 2) 验证 actuator 已对外屏蔽（应返回 404）
curl -i https://your-domain/actuator/env

# 3) 验证 SSRF 拦截：管理员尝试保存指向内网的 AI baseUrl 应被拒绝
curl -X PUT https://your-domain/api/v1/admin/tenants/1/ai-config \
  -H "Authorization: $SUPER_ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"provider":"openai","baseUrl":"http://169.254.169.254/","apiKey":"x","model":"x"}'
# 期望返回 40005 SSRF_BLOCKED

# 4) 验证 LIKE 通配符注入已修复
# 创建带特殊 tag 的帖子，订阅者数应 = 0（之前的 bug 会命中所有用户）
# （需要登录态，此处仅给思路）
```

### 8.2 主要加固项速查

| 类别 | 加固内容 | 配置入口 |
|------|----------|----------|
| 凭证 | AI API Key 改用 AES-GCM + HKDF 加密（CryptoService） | `security.crypto.*` |
| 凭证 | reset_token 改用 SHA-256 哈希存储（TokenHasher） | 自动生效，无需配置 |
| 凭证 | WebSocket 改用一次性 ticket，token 不再走 URL | `security.ws-ticket.*` |
| 注入 | tag 订阅查询 LIKE 通配符转义 + tenant_id 显式过滤 | 自动生效 |
| 跨租户 | MeiliSearch 索引/搜索强制 tenantId filter | 自动生效 |
| SSRF | SafeHttpClient 在 Socket 连接阶段二次校验目标 IP（防 DNS 重绑定） | 自动生效 |
| 权限 | 角色变更 / 封禁后强制下线（StpUtil.kickout） | 自动生效 |
| 权限 | changeRole 加角色权重校验（防 SUPER_ADMIN 反向降级） | 自动生效 |
| 资源 | 无权访问与不存在统一返回 404（防 ID 枚举） | 自动生效 |
| 限流 | 敏感路径（auth/*、ai/*）改 fail-closed | 自动生效 |
| 限流 | 登录失败计数加 IP 维度（20 次/15 分钟） | `security.login-lockout.ip-*` |
| 上传 | spring.servlet.multipart.max-file-size = 50MB | `application.yml` |
| 上传 | Apache Tika 检测真实 MIME 类型 | `security.upload.real-mime-check` |
| 上传 | 默认禁用 zip/rar/7z 扩展名 | `upload.allowed-extensions` |
| XSS | Markdown / PDF iframe 加 sandbox | 前端 Resources.vue |
| XSS | UpdateProfile URL 加协议白名单 + 域名白名单 | `security.upload.allowed-asset-hosts` |
| 信息泄露 | 公共场景改用 PublicUserVO（去除 email） | 自动生效 |
| 部署 | nginx 五项安全响应头（CSP/HSTS 等）+ 屏蔽 actuator | `deploy/nginx/nginx.conf` |
| 部署 | Dockerfile 改用非 root 用户 appuser | `backend/Dockerfile` |
| 部署 | 显式 CORS 白名单（CorsConfig） | `security.cors.allowed-origins` |
| 审计 | AuditLogService 改用 TrustedProxyResolver 解析真实 IP | 自动生效 |

### 8.3 灰度与紧急回滚

所有加固通过 feature flag 控制，可在不重新部署代码的前提下回滚：

```bash
# 旧 ECB 加密密文解密链路出问题，临时回退
CRYPTO_LEGACY_MODE=true docker compose up -d app

# Tika 检测误伤合法上传，临时关闭
UPLOAD_REAL_MIME_CHECK=false docker compose up -d app

# 登录锁定干扰开发联调
# 在 application-dev.yml 中设 security.login-lockout.enabled=false
```

### 8.4 后续清理（小版本）

加固代码留有兼容期分支，待全量数据迁移完成后可删除：

| 目标 | 触发条件 | 清理动作 |
|------|----------|----------|
| 旧 ECB 加密兼容 | 所有租户 `ai_config.encVersion = 2` | 删除 `CryptoUtils` 与 `CryptoService.decryptLegacyEcb` |
| 旧 MD5 指纹 | `resources.file_sha256` 全部回填完成 | 删除 `Resource.fileMd5` 字段与 `resources.file_md5` 列 |
| 旧 token query 兼容 | 前端全量切到 ticket | 删除 `TenantHandshakeInterceptor.verifyByLegacyToken` 旧路径 |

### 8.5 监控指标建议

在 Grafana / Prometheus 增加以下指标观察加固生效情况：

- `crypto_decrypt_legacy_total`：旧 ECB 解密次数（应随灰度递减至 0）
- `login_lockout_503_total`：fail-closed 触发的 503（应 = Redis 异常次数）
- `ssrf_blocked_total`：DNS 重绑定 / 私网拦截次数（理想 = 0，非 0 即攻击）
- `mime_mismatch_total`：MIME 不一致拦截次数（评估真实攻击量）
- `ws_ticket_issued_total` / `ws_legacy_token_used_total`：用于决定何时切 `WS_TICKET_ENFORCED=true`

> 完整审计与设计细节参见 [`.kiro/specs/security-hardening/`](../.kiro/specs/security-hardening/)（包含 bugfix.md 缺陷清单、design.md 设计文档、tasks.md 实施记录）。
