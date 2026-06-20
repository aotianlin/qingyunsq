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
| `SPRINGDOC_ENABLED` | Knife4j / OpenAPI 文档开关，**生产必须设置为 `false`**（默认即 false） | `false` |

可选但强烈建议设置：

| 变量 | 默认 | 建议 |
|---|---|---|
| `AI_API_KEY` | 空 | 真正接入 LLM 时再配置 |
| `EMAIL_FROM` / `RESET_LINK_BASE` | localhost https | 改为本校真实域名（https） |
| `OFFICE_PREVIEW_URL` | `http://localhost:8012/onlinePreview` | 内网 kkfileview 地址 |

> **配置之外的运维约定**：
> - **Token 持久化 = Redis（Sa-Token tik 风格 token）**，**并非 JWT 模式**——历史 `JWT_SECRET` / `SA_TOKEN_JWT_SECRET_KEY` 是死配置，已从 `docker-compose.yml` 移除；详见 §1.1。
> - 实际守护 token → loginId 映射的密钥是 `REDIS_PASSWORD`，请按 §1.1 中的运维操作清单执行检查与轮转。
> - **Knife4j / OpenAPI 文档双重屏蔽（漏洞 2，security-audit-hardening T2.2/T2.4）**：生产部署必须保证 `SPRINGDOC_ENABLED=false`（应用层默认即 `false`，由 `application.yml` 的 `springdoc.api-docs.enabled` / `springdoc.swagger-ui.enabled` 共同控制）；同时 `deploy/nginx/nginx.conf` 已在 `server` 块内对 `/swagger-ui|v3/api-docs|swagger-resources|doc.html|webjars` 路径返回 404，作为边缘纵深防御。即便运维误开启应用层开关，外部仍无法读取接口契约。

### 1.2 uni-app 移动端与第三方登录升级

`D:\develop\qingyun_app` 的 uni-app 迁移工程已经接入微信小程序登录、安卓 App QQ 登录、GitHub Device Flow 登录。云服务器通过拉取远程代码更新时，请按下面顺序执行：

```bash
mysql -u $MYSQL_USER -p$MYSQL_PASSWORD $MYSQL_DATABASE \
  < db/migrations/V20260620_01__wechat_login_users.sql

mysql -u $MYSQL_USER -p$MYSQL_PASSWORD $MYSQL_DATABASE \
  < db/migrations/V20260620_02__qq_github_login_users.sql
```

后端环境变量：

| 变量 | 用途 | 获取位置 |
|---|---|---|
| `WECHAT_MINI_PROGRAM_APP_ID` | 微信小程序登录 AppID | 微信公众平台 -> 开发管理 -> 开发设置 |
| `WECHAT_MINI_PROGRAM_APP_SECRET` | 微信小程序登录 AppSecret | 微信公众平台 -> 开发管理 -> 开发设置 |
| `QQ_CONNECT_APP_ID` | 安卓 App QQ 登录 AppID | QQ 互联 -> 应用管理 -> 移动应用 |
| `QQ_CONNECT_APP_KEY` | QQ 互联 AppKey，当前仅用于凭证留档 | QQ 互联 -> 应用管理 -> 移动应用 |
| `GITHUB_OAUTH_CLIENT_ID` | GitHub Device Flow Client ID | GitHub -> Settings -> Developer settings -> OAuth Apps |
| `GITHUB_OAUTH_CLIENT_SECRET` | GitHub Device Flow Client Secret | GitHub -> Settings -> Developer settings -> OAuth Apps |

客户端打包注意：

- 微信登录使用小程序 `uni.login({ provider: "weixin" })`，AppID 仍以微信开发者工具和小程序后台配置为准，前端代码不硬编码。
- QQ 登录只在 App 端启用。HBuilderX / uni-app `manifest.json` 必须启用 App OAuth 模块，并把 QQ 互联移动应用的 AppID 写入 `app-plus.distribute.sdkConfigs.oauth.qq.appid`；真机调试需要自定义基座。
- GitHub 登录使用 OAuth Device Flow。创建 GitHub OAuth App 后，需要在 OAuth App 设置中启用 Device Flow；GitHub 表单要求 Homepage URL / Authorization callback URL，可填你的 HTTPS 官网或 App 介绍页，当前 App 端实际不依赖回调地址。

### 1.1 Token 持久化方式说明（澄清 Sa-Token 当前并非 JWT 模式）

为了避免运维基于"用了 Sa-Token = 必然有 JWT"的惯性误判而产生**虚假安全感**，特别澄清：

| 项 | 当前实现 | 备注 |
|---|---|---|
| Sa-Token 模式 | Redis 持久化 + `token-style: tik` 风格随机串 | 详见 `application.yml#sa-token` 块顶部注释 |
| 是否使用 JWT | **否**，未引入 `sa-token-jwt` 依赖 | 见 `backend/pom.xml`，`application.yml` 中也无 `sa-token.jwt-secret-key` 配置 |
| token → loginId 映射存放位置 | Redis（key 前缀 `satoken:*`） | 由 `sa-token-redis-jackson` 序列化 |
| 实际守护 token 不被伪造的密钥 | `REDIS_PASSWORD` | Redis 凭证泄漏即等同于全站会话窃取 |
| 历史 ENV `SA_TOKEN_JWT_SECRET_KEY` / `JWT_SECRET` | **死配置**，已从 `deploy/docker-compose.yml` 与 `deploy/.env.example` 中移除 | Sa-Token 在未引入 jwt 模块时不会读取这两个变量 |

**对应运维操作**：

1. 检查现存 `.env` / Secret Manager 中是否仍有 `JWT_SECRET=` 或 `SA_TOKEN_JWT_SECRET_KEY=`，若存在请直接删除——保留它会让自动化巡检工具误判"密钥在管"。
2. 重点轮转 `REDIS_PASSWORD`：满足 ≥ 16 字节随机串、定期轮转，并限制 Redis 仅监听 docker 内网。
3. 若未来计划切换为 Sa-Token JWT 模式（即引入 `sa-token-jwt` 依赖并填写 `sa-token.jwt-secret-key`），**必须先做密钥轮转计划**：
   - 至少准备一对新旧密钥的灰度切换窗口；
   - 在 `SecurityStartupValidator` 中追加对 `jwt-secret-key` 的长度（≥ 32 字节）与默认值校验；
   - 在切换日通过 `StpUtil.logoutByLoginId` 强制全员重新登录，避免新旧 token 混用。

## 2. 安全相关默认值速查

| 项 | 默认 | 修改入口 |
|---|---|---|
| Sa-Token Token 持久化 | Redis（Sa-Token tik 风格 token，非 JWT 模式） | `sa-token.token-style` + `sa-token-redis-jackson`；详见 §1.1 |
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


---

## 9. 2026-06-01 安全加固（security-audit-hardening spec）

> 本节记录第三轮安全审计后实施的 32 项加固变更，覆盖凭证管理、文档暴露、会话生命周期、文件存储一致性、限流暴力破解、多租户隔离、AI/SSRF、XSS / 输入净化、监控审计 9 个主题。spec 文件：[`.kiro/specs/security-audit-hardening/`](../.kiro/specs/security-audit-hardening/)。

### 9.1 部署前必读清单

按以下顺序执行：

#### 步骤 1：应用本轮新增的数据库迁移

```bash
mysql -u $MYSQL_USER -p$MYSQL_PASSWORD $MYSQL_DATABASE \
  < db/migrations/V20260601_02__messages_ai_risk_level.sql
mysql -u $MYSQL_USER -p$MYSQL_PASSWORD $MYSQL_DATABASE \
  < db/migrations/V20260601_04__audit_log_extend.sql
mysql -u $MYSQL_USER -p$MYSQL_PASSWORD $MYSQL_DATABASE \
  < db/migrations/V20260601_05__resources_legacy_md5.sql
mysql -u $MYSQL_USER -p$MYSQL_PASSWORD $MYSQL_DATABASE \
  < db/migrations/V20260601_06__sensitive_word_regex.sql
```

⚠️ 业务影响：
- `V20260601_02`（messages.ai_risk_level）默认 0，对存量私信无影响
- `V20260601_04`（audit_logs.user_agent + 索引）仅新增字段
- `V20260601_05`（resources.file_md5 标 deprecated）仅注释变更
- `V20260601_06`（sensitive_words 增加 regex 列）仅新增字段

#### 步骤 2：补齐本轮"新必填 / 推荐"环境变量

| 变量 | 说明 | 推荐生成 |
|---|---|---|
| `SPRINGDOC_ENABLED` | Knife4j / OpenAPI 文档开关，**生产必须 `false`** | `false` |
| `WS_TICKET_ENFORCED` | WS 票据强制；先按 false 部署一周观察，再切 true | `false`（灰度后 → `true`） |

可选但强烈建议：

| 变量 | 默认 | 建议 |
|---|---|---|
| `CRYPTO_LEGACY_CUTOVER_DATE` | `2026-09-01` | 旧 ECB 密文清理截止；过期 + 仍有遗留即抛 |
| `WS_TICKET_ENFORCED_CUTOVER_DATE` | `2026-07-01` | WS legacy token 清理截止；过期 + enforced=false 即抛 |

#### 步骤 3：边缘冒烟

```bash
BASE_URL=https://your-campus.edu deploy/scripts/security-smoke.sh
```

期望：所有用例 PASS。WS legacy token query 用例需 `TEST_WS_LEGACY_TOKEN=1` 才执行（仅在 `WS_TICKET_ENFORCED=true` 灰度生效后启用）。

### 9.2 主要加固项速查

| 主题 | 加固内容 | 配置入口 / 关键代码 |
|---|---|---|
| 凭证 | `CryptoUtils` 收缩为 package-private `EcbCryptoUtils`，仅 `decrypt`，禁回退原文 | `infra.security.crypto.legacy` |
| 凭证 | `SecurityStartupValidator` prod 严格阻断（master-key / signed-url-secret / Redis 强度 + ws/crypto cutover 校验） | `security.crypto.*` `security.ws-ticket.*` |
| 凭证 | `application*.yml` 删除 `signed-url-secret` / `master-key` 字面默认 | `application.yml` |
| 文档 | `DocAccessFilter` 应用层 + nginx 边缘双重屏蔽 swagger / api-docs / webjars | `infra.security.DocAccessFilter`、`deploy/nginx/nginx.conf` |
| 会话 | `changePassword` / `resetPassword` 调用 `invalidateAllSessions` 全踢下线 | `user.service.UserService` |
| 会话 | WS query 参数 URL decode + legacy token metrics + 限频 WARN | `tenant.websocket.TenantHandshakeInterceptor` |
| 文件 | `StorageService` 接口签名扩 `size` + `issuePublicGetUrl`；MinIO 用 `statObject` 回查 size | `infra.StorageService*` |
| 文件 | `MimeTypeValidator` 严格化（黑名单 + 拒绝未注册扩展名 + 不传 Tika 文件名 hint） | `resource.service.MimeTypeValidator` |
| 文件 | `assertHostAllowed` 默认从 `self-hosts` 推导，空名单语义反转为"仅本站存储域名" | `user.service.UserService#assertHostAllowed` |
| 限流 | `RouteTemplateExtractor` + `RateLimitInterceptor` 改用路由模板，模板缺失兜底减半 | `infra.ratelimit.*` |
| 限流 | overrides 配置高成本端点配额（messages/posts-detail/preview/download/export） | `application.yml#rate-limit.overrides` |
| 限流 | `EmailVerificationCodeService` Redis 异常 fail-closed + 常量时间比较 + IP 维度计数 | `user.service.EmailVerificationCodeService` |
| 限流 | `PostViewDeduper` 浏览计数 SETNX 去重，30 分钟 TTL | `post.service.PostViewDeduper` |
| 多租户 | `TenantStartupValidator` 校验 `ignore-tables` schema 一致性 | `tenant.TenantStartupValidator` |
| 多租户 | `MeiliSearchClient.search` 强制 tenantId filter；session vs subdomain 一致性校验 | `infra.search.*`、`tenant.MultiTenantResolver` |
| AI / SSRF | `OpenAiCompatService` 解 Bean 化 + `TenantAwareAiService` 客户端缓存 + fail-loud | `ai.service.*` |
| AI / SSRF | `SafeHttpClient` 禁用自动 redirect + connect 阶段二次校验 host | `infra.security.SafeHttpClient` |
| XSS | `HtmlSanitizerService` + `TextNormalizer` + `MarkdownEscaper` 三件套；业务侧接入帖子/评论/私信/引用 | `infra.sanitize.*` |
| XSS | `SearchService.searchUsers` 字段收紧 + `PublicUserVO` 字段审计 | `search.service.SearchService` |
| XSS | `SensitiveWordService.getRiskLevel` 接入归一化 + 正则 | `sensitive.service.SensitiveWordService` |
| 数据 | 私信 `messages.ai_risk_level` 风险等级落库 | `message.service.MessageService` |
| 数据 | 导出权限拆分 4 端点 + PII 脱敏 + `MAX_ROWS=50_000`；`fullPii=true` 仅 SUPER_ADMIN | `admin.export.ExportController` / `ExportService` |
| 数据 | Admin DTO 化（`ChangeRoleRequest` / `BatchUpdateUserStatusRequest` / `SendMessageRequest`） | `admin.dto.*` |
| 数据 | Nickname 字符白名单（DTO 层） | `user.dto.*` |
| 监控 | `SecurityMetrics` 集中埋点 9 个 Counter；prometheus exposure + nginx 仅内网放行 `/actuator/prometheus` | `infra.metrics.SecurityMetrics`、`deploy/nginx/nginx.conf` |
| 审计 | `AuditContext` + 5 参重载，异步线程不再依赖 RequestContextHolder | `infra.audit.*` |
| 审计 | `MdcTraceIdFilter` + logback pattern `[traceId tenantId userId]` | `infra.web.MdcTraceIdFilter` |
| 异常 | `TenantContextMissingException` 替代字符串匹配；`ErrorCode` 扩展 `TENANT_MISMATCH` / `DOC_ACCESS_DENIED` / `EXPORT_FORBIDDEN` / `WEAK_CONFIG` | `tenant.TenantContextMissingException`、`common.ErrorCode` |


### 9.3 灰度日历与紧急回滚

| 名称 | Cutover 默认日期 | 行为 |
|---|---|---|
| `crypto.legacy-cutover-date` | `2026-09-01` | 过期且仍有 v1 ECB 密文 → 启动期校验抛错 |
| `ws-ticket.enforced-cutover-date` | `2026-07-01` | 过期且 `WS_TICKET_ENFORCED=false` → 启动期校验抛错 |

紧急回滚开关（已有，仍然支持）：

```bash
# 旧 ECB 加密密文解密链路出问题
CRYPTO_LEGACY_MODE=true docker compose up -d app
# Tika MIME 检测误伤合法上传
UPLOAD_REAL_MIME_CHECK=false docker compose up -d app
# Springdoc 必须临时打开（仅运维内网调试）
SPRINGDOC_ENABLED=true docker compose up -d app
```

### 9.4 监控指标（SecurityMetrics）

| 指标 | 含义 | 告警阈值建议 |
|---|---|---|
| `crypto_decrypt_legacy_total{tenantId=...}` | 旧 ECB 解密计数 | 持续递减；cutover 日前应 = 0 |
| `crypto_decrypt_failed_total` | 解密失败 | > 0 即告警 |
| `ssrf_blocked_total{stage=...}` | SSRF 拦截 | 出现非 0 即告警（潜在攻击） |
| `mime_mismatch_total{ext=...,detected=...}` | MIME 不一致 | 趋势观察，突发尖峰即告警 |
| `login_lockout_503_total` | 登录 fail-closed 503 | = Redis 异常次数；持续非 0 即基础设施告警 |
| `ws_legacy_token_used_total` | WS 旧 token 使用 | 灰度收尾后应 = 0，再切 `WS_TICKET_ENFORCED=true` |
| `tenant_violation_total{reason=...}` | 跨租户违规 | 出现非 0 即告警（潜在越权） |
| `rate_limit_429_total{routeTemplate=...}` | 限流命中 | 趋势观察 |
| `session_forced_logout_total{action=...}` | 敏感凭证变更后强制下线 | 与 PASSWORD_CHANGE / RESET 业务量对账 |
