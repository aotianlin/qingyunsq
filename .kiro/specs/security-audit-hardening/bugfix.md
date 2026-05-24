# CampusForum 安全审计与加固 Bugfix Spec

## Introduction

> 中文别名：引言

本文档是对 CampusForum（Spring Boot 3 + Sa-Token + MyBatis-Plus + Vue 3 + 多租户）项目的一次完整安全审计，覆盖认证、授权、租户隔离、注入、XSS、SSRF、文件上传、AI、密钥与配置、CORS/CSRF、限流、敏感词、审计日志、依赖与部署等维度。

项目此前已完成一轮 `security-hardening` 加固（见 `deploy/SECURITY.md §8`），本文针对**残余风险**和**新发现的弱点**继续推进。每条问题以"漏洞条件 V(X)"方法论描述：在何种前提下会被触发、违反了什么安全属性、修复后必须保留哪些既有功能不被破坏。

> 本阶段仅产出 `bugfix.md`，不直接修改任何业务代码。详细修复设计与具体实现拆分在后续 `design.md` / `tasks.md` 中。

## Bug Analysis

> 中文别名：安全审计概要

本节按"现状缺陷 → 期望状态 → 保留性约束"三段给出本次审计的全局视图，再通过下方 32 条 `## 漏洞 N` 给出每条问题的精确条件、影响、修复方向与 EARS 安全属性。

### Current Behavior (Defect)

中文：**总体评价**。

- 项目在第一轮加固后已经具备较完整的纵深防御骨架（租户三层拦截、AES-GCM 凭证加密、签名 URL、限流分级、SSRF 多重校验、Tika MIME 检测、登录账号+IP 双维度锁定、TenantBindingCheckInterceptor、WS Ticket 等）。
- 但在以下几个方向仍残留可被利用的弱点：
  1. 生产环境 **Knife4j / `v3/api-docs`** 没有任何屏蔽，TenantResolutionFilter 主动放行；
  2. **Sa-Token 文档/swagger/actuator 未经任何鉴权**，前端运维误把 8080 端口直暴公网时即可被外部读取接口契约；
  3. 旧 ECB 加密的硬编码密钥 `CampusForum@1234` 仍存活在 `CryptoUtils` 中，且未真正限制调用方；
  4. **修改密码 / 重置密码后未踢下线**，旧 token 在 7 天总有效期内仍可使用；
  5. **MinIO 上传调用 `inputStream.available()`** 作为对象大小，导致大文件被截断（同时也是数据完整性 / 拒绝服务面）；
  6. 多处签名密钥（`signed-url-secret`）在生产仅 `WARN`，弱默认值不会阻断启动；
  7. 限流 key 拼接 `request.getRequestURI()`，对含 path variable 的端点（`/api/v1/posts/{id}` 等）形成"按 ID 分桶"的旁路；
  8. **WebSocket 旧 `?token=` 路径**在默认 `WS_TICKET_ENFORCED=false` 下仍开放，主令牌仍会被记录到 access log；
  9. 密码 / 重置 / 验证码消费未使用常量时间比较，**EmailVerificationCodeService.isRateLimited fail-open**；
  10. SearchService 通过 `email LIKE` 暴露邮箱→昵称映射，可批量做用户枚举。

下列编号子句是对上述全局缺陷的精简归纳（按"漏洞 N"对应关系给出全局级 EARS Defect 子句，每条对应 32 条详述中的一项；详细复现条件 V(X) 与影响请见对应"漏洞 N"段）：

1.1 WHEN 应用以 `prod` profile 启动且 `SPRINGDOC_ENABLED` / nginx 屏蔽未配置 THEN 系统向公网暴露 `/swagger-ui/**` 与 `/v3/api-docs/**` 完整接口契约（漏洞 2）。
1.2 WHEN 任意调用方引用 `CryptoUtils.encrypt/decrypt` THEN 系统使用全局硬编码 ECB 密钥加密，且解密失败时静默回退原始密文（漏洞 1）。
1.3 WHEN `SIGNED_URL_SECRET` 缺失或仍为仓库默认占位串 THEN 系统仅打印 WARN 即继续启动，所有 RESOURCE / WS_TICKET 签名可被全量伪造（漏洞 3）。
1.4 WHEN 部署使用 `SA_TOKEN_JWT_SECRET_KEY` ENV THEN 系统不经校验地接受该值，但 Sa-Token 实际为 Redis tik 风格 token 模式，该密钥不生效，造成密钥治理虚假安全感（漏洞 4）。
1.5 WHEN 用户成功执行 `changePassword` / `resetPassword` THEN 系统不调用 `StpUtil.logoutByLoginId`，旧 token 在 Sa-Token 7 天总时长内继续有效（漏洞 5）。
1.6 WHEN 用户上传 ≥ ~64KB 文件且 `storage.type=minio` THEN 系统调用 `inputStream.available()` 作为对象大小传入 MinIO SDK，文件被截断到 buffer 大小，SHA-256 同步算错（漏洞 6）。
1.7 WHEN 限流 key 拼接的请求路径包含 path variable THEN 系统按"具体 URI"分桶，每个 ID 拥有独立配额，攻击者可枚举 ID 绕过限流（漏洞 7）。
1.8 WHEN `WS_TICKET_ENFORCED=false` 且客户端走 `wss://host/ws/notify?token=<sa-token>` THEN 系统接受 query 中的主 token 并将其写入 nginx access log（漏洞 8）。
1.9 WHEN 任意已登录用户对 `/api/v1/search?type=USER&q=@xx.com` 发起搜索 THEN 系统对 `email` 与 `studentNo` 执行 LIKE 匹配，可批量枚举用户（漏洞 9）。
1.10 WHEN `rate-limit.exclude-patterns` 包含 `/api/v1/auth/login` 或其他敏感前缀 THEN 系统在拦截器入口直接放行，敏感路径 fail-closed 与 override 配额一并失效（漏洞 10）。
1.11 WHEN Redis 不可用 THEN `EmailVerificationCodeService.isRateLimited` 返回 false，邮件验证码发送 fail-open，可被滥用做邮件轰炸；`verifyAndConsume` 使用 `String.equals` 而非常量时间比较（漏洞 11）。
1.12 WHEN `tenantService.resolveAiCredentials` 抛 `CryptoException` 或租户未配置 AI THEN `TenantAwareAiService#delegate` 静默降级到 mock 且不写审计日志；同时 `OpenAiCompatService` 作为持有全局 `ai.api-key` 的 Spring Bean 长期存在（漏洞 12）。
1.13 WHEN TENANT_ADMIN 调用 `POST /api/v1/admin/export/{users|posts|audit_logs|reports}` THEN 系统仅校验 `tenant:dashboard` 粗粒度权限，导出明文邮箱与学号 PII，无审计日志、无单次行数上限、无每管理员限流（漏洞 13）。
1.14 WHEN `TENANT_IGNORE_TABLES` 列表错误地纳入了带 `tenant_id` 的表 THEN 系统跳过该表的租户隔离 SQL 改写，TENANT_ADMIN 可读到全租户数据；`DashboardVO` 缺少 `tenantId` 字段使前端无法区分数据范围（漏洞 14）。
1.15 WHEN 用户调用 `POST /api/v1/users/me/assets` 上传头像 THEN 系统返回 `/uploads/<key>` 或 storageKey 字面量作为 URL，前端访问 404；`security.upload.allowed-asset-hosts` 为空时 `assertHostAllowed` 直接放行任意外部 URL（漏洞 15）。
1.16 WHEN 已登录用户 `POST /api/v1/messages` 携带 `Map<String,String>` body THEN 系统不校验 `receiverId` / `content` / `imageUrl`，无跨租户显式断言，无敏感词过滤、无 30 条/分钟级限流（漏洞 16）。
1.17 WHEN 管理员调用 `AdminUserController#changeRole` / `batchSetStatus` 等接口 THEN 系统使用 `@RequestBody Map<...>` 解析入参，缺失字段时 NPE 5xx；批量操作非 `@Transactional`，部分失败留下半改状态（漏洞 17）。
1.18 WHEN 帖子 / 评论 / 私信内容包含 HTML/JS 载荷 THEN 系统不调用 OWASP HTML Sanitizer 即落库；前端 `renderMentions` 通过字符串拼接 `<a>` 并经 `v-html` 渲染，存在存储型 XSS 链路（漏洞 18）。
1.19 WHEN SUPER_ADMIN 通过 `TenantService#toggleStatus` 停用租户 THEN 系统不调用 `ActiveTenantCache.evict` 也不按租户 kickout 全部用户，被停用租户用户在 cache TTL 内仍可正常操作（漏洞 19）。
1.20 WHEN 用户引用历史帖子且引用对象的 nickname / title / content 含 Markdown 控制字符 THEN `PostService#create` 在拼接引用块前不做 Markdown 转义，存在引用块逃逸 / 仿冒发言（漏洞 20）。
1.21 WHEN 同一已登录用户对自己或他人帖子反复发起 `GET /api/v1/posts/{id}` THEN 系统对每次请求 `view_count + 1`，无 `(postId, userId)` 30 分钟去重（漏洞 21）。
1.22 WHEN `MeiliSearchClient#search` 收到 `tenantId == null` THEN 系统不追加 `filter: tenantId = X` 即静默执行全租户聚合检索（漏洞 22）。
1.23 WHEN 上游响应携带 30x 重定向到 `http://169.254.169.254/` 等私网地址 THEN `SafeHttpClient` 默认依赖 `HttpURLConnection` 自动跟随，重定向后那一跳未再走 `PrivateNetworkValidator` 校验（漏洞 23）。
1.24 WHEN 上传文件扩展名在 allowed-extensions 白名单内但未在 `EXT_TO_MIMES` 中声明 THEN `MimeTypeValidator` 跳过 MIME 校验静默放行；同时 Tika detection 仍接收 `RESOURCE_NAME_KEY` 文件名 hint，可被恶意 originalFilename 误导（漏洞 24）。
1.25 WHEN 客户端不发送 `X-Tenant-Id` header 但通过子域名访问 multi 模式下 THEN `TenantBindingCheckInterceptor` 直接放行，`MultiTenantResolver` 在未认证路径上仅以子域名解析租户，不与 Sa-Token Session 做一致性比对（漏洞 25）。
1.26 WHEN `AuditLogService.log` 在 `@Async` 异步线程被调用 THEN 注入的 request-scoped `HttpServletRequest` 代理抛 `IllegalStateException`，IP 字段为空，审计完整性受损（漏洞 26）。
1.27 WHEN 用户内容包含零宽字符 / 同形字 / 全角变体 THEN `SensitiveWordService#getRiskLevel` 仅 `String.contains` 匹配，敏感词审核被绕过（漏洞 27）。
1.28 WHEN 业务抛出 `IllegalStateException` THEN `GlobalExceptionHandler` 通过字符串匹配判定是否 `TenantContext is null`，未来重构时易漏改；同时 `Unhandled exception` 日志包含完整堆栈（漏洞 28）。
1.29 WHEN WebSocket ticket 字符串包含 `+` / `/` / `=` 经 `encodeURIComponent` 编码后作为 query THEN `TenantHandshakeInterceptor#extractQueryParam` 不调用 `URLDecoder.decode`，SignedUrlService 校验失败 401（漏洞 29）。
1.30 WHEN 前端在 `localStorage` 持久化 Sa-Token 主令牌 THEN 任意 XSS 链路可通过 `localStorage.getItem('token')` 偷取 token（漏洞 30）。
1.31 WHEN 任意接口构造 `R<T>` 响应 THEN `traceId` 由 `UUID.randomUUID().toString().substring(0, 8)` 现场生成，与 SLF4J MDC / 日志 traceId 无关联，事后无法溯源（漏洞 31）。
1.32 WHEN 加固组件运行 THEN 系统未通过 Micrometer 暴露 `crypto_decrypt_legacy_total` / `ssrf_blocked_total` / `mime_mismatch_total` / `ws_legacy_token_used_total` 等关键安全指标（漏洞 32）。

### Expected Behavior (Correct)

中文：**修复后期望状态**。

修复完成后系统应满足下列总体约束（每条漏洞内"安全属性 (EARS)"是该约束的细化）：

- 凭证管理：所有密钥统一通过 ENV 注入；启动期严格校验长度与默认值；勿将密钥写进 `application.yml`。
- 认证与会话：密码变更 / 重置 / 角色变更 / 封禁均触发 `StpUtil.logoutByLoginId(...)`；WebSocket 在 cutover 日期后强制 ticket 模式。
- 多租户隔离：`TENANT_IGNORE_TABLES` 配置启动期校验；`MeiliSearchClient.search` 在 tenantId 缺失时不静默放行；子域名解析与 Session 不一致时拒绝并审计。
- 限流：路由模板（含 path variable）共享桶；敏感路径 fail-closed；不可被排除；`/messages`、`/exports`、`/posts/{id}`、`/resources/{id}/download` 等高成本端点单独配额。
- 文件上传：StorageService 接口要求显式传 size；上传后 `statObject` 回查；MIME 校验默认拒绝未在白名单且 detected 不在已知集合内的扩展名；不再向 Tika 传文件名 hint。
- XSS / 注入：所有用户内容（帖子、评论、私信、引用）经过 OWASP Sanitizer；前端 mention 渲染禁用 `v-html`；nickname 增加字符白名单；引用块在拼接前 Markdown 转义。
- SSRF：禁用自动 redirect；redirect 必须重新校验目标 host；外联客户端统一走 SafeHttpClient。
- 文档暴露：prod 默认关闭 swagger / api-docs；nginx 双重屏蔽；actuator 仅暴露内网。
- 监控与审计：关键事件全量埋点 Micrometer Counter；审计日志写入显式 IP；MDC traceId 跨请求贯通。

### Unchanged Behavior (Regression Prevention)

中文：**保留性约束**（regression prevention）。

无论修复哪一条，下列既有功能必须保持不退化：

1. 已认证用户在 `application-dev.yml` 启动后能正常登录、发帖、上传、AI 对话。
2. multi 模式下子域名识别租户 + Sa-Token Session 优先策略不变。
3. 50MB 上传上限、签名 URL 60s TTL、Sa-Token 7d 总时长 + 4h 闲置过期保持。
4. 现有 4 类导出（CSV/XLSX）能力保留，仅做权限/字段/配额调整。
5. 现有限流 fail-closed 敏感路径列表不缩小。
6. CORS allowCredentials=false、CSP frame-ancestors='none' 等加固保持。
7. 现有审计日志结构兼容（仅扩展 + 新事件类型）。
8. v2 AES-GCM 加密链路不被新代码绕过；v1 ECB 仅供历史数据迁移读。

### 严重级别统计（共 32 条）

| 级别 | 数量 | 编号范围 | 说明 |
|---|---|---|---|
| Critical | 4 | 漏洞 1–4 | 直接导致信息泄漏、密钥伪造、跨租户数据访问，必须紧急修复 |
| High | 14 | 漏洞 5–18 | 可在常规攻击前提下被利用，需要在下个迭代修复 |
| Medium | 9 | 漏洞 19–27 | 缓解措施已部分到位但有绕过路径，建议在 30 天内修复 |
| Low | 4 | 漏洞 28–31 | 加固层面或防御纵深，建议在 90 天内修复 |
| Info | 1 | 漏洞 32 | 文档与监控类建议 |

> 严重级别参考 CVSS 思路（不严格计算分值）：综合考虑可达性（是否需要登录 / 管理员）、影响面（单租户 / 跨租户 / 全站）、可被利用难度。

### 评分体系约定

- **触发条件 V(X)**：以"在前置 X 满足时执行某操作即触发漏洞"的形式给出，便于后续编写复现脚本。
- **影响**：分别说明对机密性 (C) / 完整性 (I) / 可用性 (A) 的影响，以及是否跨租户、是否未授权可达。
- **安全属性 (EARS)**：用 EARS 格式描述修复后必须满足的属性，作为后续 fix-checking 的依据。
- **保留性 (Preservation)**：列出修复时不能破坏的既有合理功能，作为 preservation-checking 的依据。

### 受影响代码全局位置

```
backend/src/main/java/com/campusforum/
├── common/CryptoUtils.java               (Critical-1)
├── common/GlobalExceptionHandler.java    (Low-1)
├── infra/Knife4jConfig.java              (Critical-2)
├── infra/MinioStorageService.java        (High-2)
├── infra/MyBatisPlusConfig.java          (Medium-7)
├── infra/WebMvcConfig.java               (Info-1)
├── infra/ratelimit/RateLimitInterceptor.java (High-3, Medium-2)
├── infra/security/SecurityStartupValidator.java (Critical-3)
├── infra/security/SignedUrlService.java  (Critical-3)
├── infra/security/SafeHttpClient.java    (Low-3)
├── tenant/filter/TenantResolutionFilter.java (Critical-2)
├── tenant/websocket/TenantHandshakeInterceptor.java (High-1, Low-2)
├── user/service/UserService.java         (High-4, Medium-1, Medium-5)
├── user/service/EmailVerificationCodeService.java (High-5, Medium-3)
├── user/dto/RegisterRequest.java         (Medium-6)
├── post/service/PostService.java         (Medium-4, Medium-9)
├── post/service/CommentService.java      (Low-4)
├── search/service/SearchService.java     (High-6)
├── search/service/MeiliSearchClient.java (Medium-8)
├── ai/service/OpenAiCompatService.java   (High-7)
├── admin/export/ExportService.java       (High-8)
├── admin/controller/AdminController.java (High-9)
├── tenant/service/TenantService.java     (Critical-4, High-10)
├── notify/websocket/WebSocketConfig.java (Medium-10)
└── infra/security/CorsConfig.java        (Low-5 — 已加固但前端依赖)

deploy/
├── docker-compose.yml                    (Medium-9: SA_TOKEN_JWT_SECRET_KEY 实际未生效)
└── nginx/nginx.conf                      (High-2 加固入口、Critical-2 加固入口)
```


---

## 漏洞 1：旧版 ECB 加密硬编码密钥仍在生产代码中可被任意调用 [Critical]

### 风险描述
`CryptoUtils.java` 用硬编码 16 字节密钥 `"CampusForum@1234"` + AES/ECB/PKCS5Padding 提供加密接口，且解密失败时**静默回退原文**。该类虽标 `@Deprecated(forRemoval=true)`，但 `encrypt(...)` 方法没有任何调用方限制；任何后续代码引用都会写入弱密钥密文，且解密失败会让密文以明文形式继续流转，污染加密链路完整性边界。

### 受影响位置
- `backend/src/main/java/com/campusforum/common/CryptoUtils.java`（整文件）
- 调用入口：`backend/src/main/java/com/campusforum/infra/security/crypto/CryptoService.java#decryptLegacyEcb`（兼容期）

### 触发条件 V(X)
设 X = "新代码或 hotfix 引入对 `CryptoUtils.encrypt`/`decrypt` 的调用，或攻击者拿到一段历史 ECB 密文与目标 tenant 的 ai_config 列表"。在 X 满足时：
- `CryptoUtils.encrypt` 仍以全局硬编码密钥 + ECB 加密，**所有租户共享同一密钥**且无 IV，相同明文产生相同密文（密码本攻击）；
- `CryptoUtils.decrypt` 在异常时返回原始 ciphertext（`return encrypted`），让"密文格式非法 / 密钥换了"的情况伪装成"已解密"。

### 影响
- C：泄漏。一次性密钥被反编译/源码读取即可解密所有租户历史 AI API Key。
- I：完整性受损。decrypt 出错回退原文使下游业务无法区分"明文残留"和"成功解密"。
- A：低。
- 跨租户：是（密钥与租户无关）。
- 未授权可达：否（需要 DB 读权限 / 源码权限）。

### 安全属性 (EARS)
- **WHERE** 系统涉及对称加密 / 解密，**THE** 系统 **SHALL** 仅通过 `CryptoService` 暴露能力，禁止对外暴露 `CryptoUtils`。
- **WHEN** 调用方非 `CryptoService.decryptLegacyEcb`、**IF** 试图调用 `CryptoUtils`，**THEN** 编译期必须不通过（package-private + 显式 friend 类）。
- **IF** 解密失败，**THEN** 系统 **SHALL** 抛 `CryptoException` 并由 `GlobalExceptionHandler` 转为 `CRYPTO_FAILURE`，绝不返回原始密文。
- **WHILE** v1 ECB 兼容期未结束，**THE** 系统 **SHALL** 在每次走到 legacy 分支时上报 `crypto_decrypt_legacy_total` 指标，便于评估迁移完成度。

### 保留性 (Preservation)
- 现有 v2 GCM 加解密链路不能受影响；`resolveAiCredentials` 异步重加密路径必须保留。
- 兼容期不能删除 `decryptLegacyEcb`，否则未迁移的历史数据无法解密。

### 建议修复方向
1. 将 `CryptoUtils` 改为 `package-private`（去 `public`），并搬到 `infra.security.crypto.legacy` 子包中，仅供 `CryptoService` 引用；同时删除 `encrypt(...)` 方法（迁移阶段不需要再写入新 ECB 密文）。
2. 把 `decrypt` 内部 `return encrypted` 兜底替换为 `throw new CryptoException(...)`。
3. 在 `SecurityStartupValidator` 加入 `crypto.legacy-mode` 与 `crypto_decrypt_legacy_total` 监控埋点；当连续 N 天指标 = 0 时输出"可清理"提示。


---

## 漏洞 2：Knife4j / OpenAPI 文档在生产无认证暴露 [Critical]

### 风险描述
后端通过 `springdoc + knife4j-openapi3-jakarta-spring-boot-starter` 提供接口文档，`Knife4jConfig` 注册了 `v1` 分组并扫描 `/api/v1/**`，但同时存在三个放行路径让接口契约对外可读：
- `SaTokenConfig#addInterceptors` 仅对 `/api/v1/**` 应用 `StpUtil.checkLogin()`，而 `/swagger-ui/**`、`/v3/api-docs/**` 不在其拦截范围；
- `TenantResolutionFilter#isExcluded` 显式放行 `/swagger-ui/`、`/v3/api-docs/` 前缀；
- `deploy/nginx/nginx.conf` **没有**屏蔽 swagger / api-docs 路径，仅屏蔽了 `/actuator/`。

结果：在 Docker Compose 默认部署下，未登录用户访问 `https://<host>/v3/api-docs` 与 `https://<host>/swagger-ui/index.html` 即可拿到完整接口契约（参数、响应、内部错误码、AI/管理后台路径等），等于把整张 API 攻击面图纸送给攻击者。

### 受影响位置
- `backend/src/main/java/com/campusforum/infra/Knife4jConfig.java#campusForumOpenAPI`、`v1Api`
- `backend/src/main/java/com/campusforum/security/SaTokenConfig.java`（拦截器仅作用于 `/api/v1/**`）
- `backend/src/main/java/com/campusforum/tenant/filter/TenantResolutionFilter.java#isExcluded`（第 47-52 行放行 swagger / v3/api-docs）
- `deploy/nginx/nginx.conf`（缺少 `swagger-ui` / `v3/api-docs` 屏蔽 location）
- `application.yml`：`management.endpoints.web.exposure.include: health` 已限制 actuator，但 springdoc 默认开启不受 `management.*` 控制。

### 触发条件 V(X)
设 X = "生产环境按 `deploy/docker-compose.yml` 部署，未做额外网关层屏蔽"。在 X 满足时：
- 任意公网用户访问 `GET /v3/api-docs` → 返回完整 OpenAPI JSON；
- 访问 `/swagger-ui/index.html` → 渲染交互式文档，可现场调用 `/api/v1/auth/login` 等接口尝试枚举。

### 影响
- C：高。完整接口契约 + 错误码语义对外公开，极大降低攻击成本。
- I：低（不直接产生写入）。
- A：中。攻击者能基于文档定向构造大批量请求触发限流 / DoS。
- 跨租户：是（接口契约对所有租户都一样）。
- 未授权可达：是。

### 安全属性 (EARS)
- **WHEN** 应用 profile 为 `prod`，**THE** 系统 **SHALL** 拒绝任何来自非可信代理的对 `/swagger-ui/**`、`/v3/api-docs/**`、`/swagger-resources/**`、`/doc.html`、`/webjars/**` 的请求（返回 404 或 403）。
- **WHERE** 应用 profile 为 `dev`，**THE** 系统 **SHALL** 仅对登录管理员（`role` ∈ {SUPER_ADMIN, TENANT_ADMIN}）开放上述路径。
- **THE** 部署 nginx 配置 **SHALL** 在 `server` 块内显式 return 404 屏蔽上述路径作为纵深防御。

### 保留性 (Preservation)
- 开发环境 `npm run dev` + `mvn spring-boot:run` 调试时仍能通过 IDE 直连 `localhost:8080/swagger-ui/index.html` 看文档（来源 IP 命中 `security.trusted-proxies` 即放行）。
- `Knife4jConfig` Bean 自身保留，仅限制 HTTP 暴露面，避免影响 controller 上的 `@Operation` 注解扫描。

### 建议修复方向
1. 新增 `springdoc.api-docs.enabled` 与 `springdoc.swagger-ui.enabled` 通过 ENV `SPRINGDOC_ENABLED` 控制，生产 profile 默认关闭。
2. 在 Spring 层新增 `DocAccessFilter`：`@Profile("prod")` 时一律拒绝；`dev` 下校验 Sa-Token 角色。
3. `TenantResolutionFilter#isExcluded` 把 `/swagger-ui/`、`/v3/api-docs/` 改成"仅本机"判断，与 actuator 同等待遇。
4. `deploy/nginx/nginx.conf` 增加：
   ```
   location ~ ^/(swagger-ui|v3/api-docs|swagger-resources|doc\.html|webjars)/ { return 404; }
   ```
5. 部署文档 `deploy/SECURITY.md §1` 增加"必须设置 SPRINGDOC_ENABLED=false"提示。


---

## 漏洞 3：`signed-url-secret` 弱默认值在生产仅 WARN 即可启动，签名 URL 可被全量伪造 [Critical]

### 风险描述
`SignedUrlService` 使用 HMAC-SHA256 对 `(userId|type|resourceId|action|exp)` 进行签名，密钥来自 `security.signed-url-secret`：
- `application.yml` 默认值是 `campus-forum-default-signed-url-secret-please-override`（明文写在仓库里）；
- `application-prod.yml` 仅声明 `signed-url-secret: ${SIGNED_URL_SECRET}`，**无 `:default` 兜底**，缺失会启动失败 — 这是好事；
- 但 `SecurityStartupValidator#validateSignedUrlSecret` 对默认值仅 `log.warn(...)`，**不会阻断启动**。

实际部署中只要运维忘记设置 `SIGNED_URL_SECRET`、或者把 `application.yml` 默认值带到生产，就会形成**全站签名密钥可预测**：攻击者读取 GitHub 公开仓库即可获得密钥，进而伪造任意 `RESOURCE` / `WS_TICKET` 签名，绕过资源访问鉴权和 WebSocket 握手鉴权。

### 受影响位置
- `backend/src/main/resources/application.yml#L99`：`signed-url-secret: ${SIGNED_URL_SECRET:campus-forum-default-signed-url-secret-please-override}`
- `backend/src/main/java/com/campusforum/infra/security/SecurityStartupValidator.java#validateSignedUrlSecret`（第 60-66 行只 WARN）
- `backend/src/main/java/com/campusforum/infra/security/SignedUrlService.java`（密钥消费者）
- `backend/src/main/java/com/campusforum/infra/security/WsTicketService.java`（同样消费该密钥）
- 同样问题密钥：`security.crypto.master-key` 在 `application.yml` 提供了硬编码默认 `dev-only-change-me-please-32bytes-...`，dev/prod 错配时可能误用。

### 触发条件 V(X)
设 X = "生产部署 ENV `SIGNED_URL_SECRET` 未设置，且没有从 `application.yml` 中删除默认值；或运维把 `dev-only-change-me-please-...` 直接复制到生产 ENV"。在 X 满足时：
- 后端启动只会打 WARN 不阻断；
- 攻击者构造 `payload = userId|RESOURCE|<targetId>|download|<future-ts>`，用公开默认密钥计算 HMAC 即可下载任意租户、任意资源；
- 同样可伪造 `WS_TICKET`，绕过 `TenantHandshakeInterceptor` 直连 `/ws/notify` 接收他人通知。

### 影响
- C：高（任意资源下载、跨租户）。
- I：中（可借伪造 ticket 接通 WebSocket 后伪装在线用户）。
- A：低。
- 跨租户：是（密钥与租户无关）。
- 未授权可达：部分场景需要拼一个有效 userId，但 `userId` 可枚举且不参与签名校验唯一性。

### 安全属性 (EARS)
- **THE** 系统 **SHALL** 在启动期检测 `security.signed-url-secret` 内容；命中以下任一条件即抛 `IllegalStateException` 终止启动：长度 < 32 字节、包含字符串 `please-override`、包含字符串 `dev-only-change-me`、等于 `application.yml` 中默认值（fingerprint 比对）。
- **WHEN** profile = `prod`，**THE** 系统 **SHALL** 拒绝任何形式的默认值兜底（移除 `application.yml` 中默认值）。
- **THE** `security.crypto.master-key` 同样规则：禁止字面包含 `dev-only-change-me` / `please-override`。
- **THE** 系统 **SHALL** 在文档 `deploy/SECURITY.md §1` 强调用 `openssl rand -base64 48` 生成。

### 保留性 (Preservation)
- 开发环境（`spring.profiles.active=dev`）允许使用 `dev-only-` 前缀的默认值，便于本地起服务；启动校验仅在 `prod` profile 启用阻断。
- 已生效的 v2 加密数据不需要重加密；签名 URL 是短期（60s）的，密钥更换不会影响历史已签发链接的"有效但过期"语义。

### 建议修复方向
1. `SecurityStartupValidator#validateSignedUrlSecret` 改为：
   - 读取当前 `Environment.activeProfile`；
   - `prod` profile 下做严格校验：`length < 32 || contains("please-override") || contains("dev-only-change-me")` → throw；
   - `dev`/`test` 仅 WARN。
2. 删除 `application.yml` 中的 `signed-url-secret` 默认值，改为 `${SIGNED_URL_SECRET:}` 让 `prod` 缺失 ENV 时启动失败（与 `application-prod.yml` 行为一致）。
3. 生产强制要求密钥来自 ENV/Secret Manager，不允许写入 yml 文件。
4. 增加部署期校验脚本 `deploy/install.sh` 调用 `openssl rand -base64 48` 生成 `.env` 模板。


---

## 漏洞 4：`docker-compose.yml` 中 `SA_TOKEN_JWT_SECRET_KEY` 是死配置，Sa-Token 实际未启用 JWT 模式 [Critical]

### 风险描述
`deploy/docker-compose.yml` 第 56 行声明 `- SA_TOKEN_JWT_SECRET_KEY=${JWT_SECRET}`，`deploy/.env.example` 鼓励运维生成 `JWT_SECRET`。但 `application*.yml` 中 `sa-token` 块**没有任何 jwt 配置**，pom 也没有引入 `sa-token-jwt` 模块（仅 `sa-token-spring-boot3-starter` + `sa-token-redis-jackson`）。

这导致两个互相矛盾的安全态势：
1. Sa-Token 实际使用 Redis 持久化的 `tik` 风格 token（`token-style: tik`），并无 JWT 校验，`SA_TOKEN_JWT_SECRET_KEY` 是无效配置 — 运维生成的所谓"JWT 密钥"其实没有任何作用，给运维一种虚假安全感；
2. 文档（`deploy/SECURITY.md §1`）也未列 `JWT_SECRET`，但 `.env.example` 仍引用 — 配置漂移会让一些自动化巡检工具（看到 `JWT_SECRET=` 留空）误判"未泄漏"，但实际项目用的是 Sa-Token 自己的随机 token，强度依赖于 Sa-Token Token 风格而非 JWT。

更严重的是：`tik` 风格 token 默认使用 `UUID` 风格随机串（足够长、可接受），但 `is-share=false` + `is-concurrent=true` 的组合下，**用户登录后服务端没有对原 token 主动失效**（"修改密码后旧 token 仍有效"问题，见 High-4）。Sa-Token 在配置 JWT 模式 + 在 Redis 同时维护时的"密钥泄漏即全站伪造"风险其实并不存在，但**运维误以为有 JWT 保护**导致重要安全配置（如对称密钥防泄漏、密钥轮转计划）被忽视。

### 受影响位置
- `deploy/docker-compose.yml`（第 56 行：`SA_TOKEN_JWT_SECRET_KEY=${JWT_SECRET}`）
- `deploy/.env.example`（隐含的 `JWT_SECRET` 用法说明缺失）
- `deploy/SECURITY.md`（未澄清 Sa-Token 实际不是 JWT 模式）
- `application.yml#sa-token` 块（缺 `jwt-secret-key` 与 `token-style` 文档）
- `backend/pom.xml`（无 `sa-token-jwt` 依赖）

### 触发条件 V(X)
设 X = "运维严格按 `.env.example` 设置 `JWT_SECRET`，并以为该密钥保护用户 token 不可伪造"。在 X 满足时：
- 实际 token 来源不依赖该密钥；攻击者真正能利用的攻击面是 Redis 凭证泄漏或 SignedUrlService 密钥泄漏（漏洞 3）；
- 当未来某个迭代真的开启 JWT 模式（从 Redis 改成 stateless），现有配置会"自动启用"未轮转过的 `JWT_SECRET`，构成密钥老化攻击面。

### 影响
- C：中（误导运维，间接导致密钥治理松懈）。
- I：低（暂时无直接攻击路径）。
- A：低。
- 跨租户：是。
- 未授权可达：否。
- 严重级别评定为 **Critical** 的原因：**配置漂移** 是高级 APT 攻击的常见踏板，且未来开启 JWT 模式时存在"以为换密钥实际没换"的隐患。

### 安全属性 (EARS)
- **THE** 部署文档 **SHALL** 显式说明 Sa-Token 当前为 Redis 持久化 + tik 风格 token，**未使用 JWT**。
- **IF** 未来切换为 JWT 模式（`sa-token.jwt-secret-key` 生效），**THEN** 系统 **SHALL** 在启动期校验 `jwt-secret-key` 长度 ≥ 32 字节并阻止默认值。
- **THE** `.env.example` 中 `JWT_SECRET` 项 **SHALL** 删除或标注 "保留位，未启用，若未来启用需先做密钥轮转计划"。
- **THE** Redis 凭证 `REDIS_PASSWORD` **SHALL** 在 `prod` profile 下校验长度 ≥ 16 且非默认值，因为 Redis 的对应键 `satoken:*` 直接持有 token → loginId 映射，泄漏即等同于全站会话窃取。

### 保留性 (Preservation)
- 现有 token 颁发与续期流程不变。
- `sa-token.is-concurrent=true` 与 7 天总有效期保持不变（业务需要）。

### 建议修复方向
1. 删除 `docker-compose.yml` 第 56 行 `SA_TOKEN_JWT_SECRET_KEY` 与对应 `.env.example` 配置项；或者改为带有 `# 保留位，当前未生效` 注释。
2. `application.yml` 在 `sa-token` 块顶部增加注释说明 token 风格、是否 JWT。
3. `SecurityStartupValidator` 增加 Redis 凭证强度校验（`prod` profile 下要求长度 ≥ 16）。
4. 在 `deploy/SECURITY.md §1` 表格中增加"Token 持久化 = Redis"一行，避免运维误解。


---

## 漏洞 5：修改密码 / 重置密码后旧 Sa-Token 仍可用，会话残留 [High]

### 风险描述
`UserService#changePassword` 与 `UserService#resetPassword` 仅更新 `password_hash` 并 `updateById`，**未调用 `StpUtil.kickout(userId)` 或 `StpUtil.logoutByLoginId(userId)`**。Sa-Token 默认 `timeout=7d` + `active-timeout=4h`，意味着：
- 攻击者拿到一段被钓鱼/被 XSS 偷走的 Sa-Token 后；
- 即使受害者修改密码或走"忘记密码"流程重置密码，原 token 仍然在 Redis 中有效；
- 受害者必须主动调 `/api/v1/auth/logout`（恶意 token 与正常 token 在不同设备上时不会一起注销）或等待 7 天自然过期。

更糟糕的是 `changeRole`、`banUser` 这些已经显式做了 `StpUtil.kickout(userId)`（见 `UserService#banUser`），而**最常见的密码变更场景反而漏了**，形成防御不一致。

### 受影响位置
- `backend/src/main/java/com/campusforum/user/service/UserService.java#changePassword`（第 192-200 行附近）
- `backend/src/main/java/com/campusforum/user/service/UserService.java#resetPassword`（第 213-225 行）

### 触发条件 V(X)
设 X = "某用户 token T 被攻击者拿到（XSS / 中间人 / 公共电脑残留），用户随后修改/重置密码"。在 X 满足时：
- 攻击者持 T 仍可调用所有需登录的接口；
- 即使受害者把"我已修改密码"理解为"会话已重置"，也不会触发 token 失效。

### 影响
- C：高（账号被持续控制读取私信、订阅、AI 对话）。
- I：高（可继续以受害者身份发帖、删除资源、骚扰他人）。
- A：低。
- 跨租户：否（限于受害者所在租户）。
- 未授权可达：否（前提是攻击者已拿到 token）。

### 安全属性 (EARS)
- **WHEN** 用户成功执行 `changePassword`、`resetPassword`、`forgotPassword` 后续 reset 或任何让密码失效的操作，**THE** 系统 **SHALL** 调用 `StpUtil.logoutByLoginId(userId)` 注销该用户的全部活跃 token，**包括当前请求自身**。
- **WHILE** 用户在前端执行修改密码后，**THE** 前端 **SHALL** 接收 401 并跳转登录页（与现有 `request.ts` 拦截器一致）。
- **THE** 系统 **SHALL** 在 `audit_log` 中记录 `PASSWORD_CHANGE` / `PASSWORD_RESET` 事件，包含被踢下线的活跃会话数。

### 保留性 (Preservation)
- 修改密码接口 200 响应语义不变（前端会被 401 拦截器接管，体验自然），但前端可读 `R.code` 判断成功后主动登出。
- 不影响 `banUser`/`unbanUser`/`changeRole` 已有的 kickout 行为。

### 建议修复方向
1. `changePassword` 与 `resetPassword` 在 `userMapper.updateById(user)` 之后追加 `StpUtil.logoutByLoginId(userId)`。
2. 增加 `UserService#kickoutOnSensitiveChange(Long userId, String action)` 工具方法，统一审计 + 注销。
3. 前端 `request.ts` 在 `PUT /auth/password` 与 `POST /auth/reset-password` 的成功回调中显式调用 `useAuthStore().logout()`，避免依赖 401 拦截。


---

## 漏洞 6：MinIO `inputStream.available()` 作为对象大小，导致 ≥ ~64KB 文件被截断写入 [High]

### 风险描述
`MinioStorageService#upload` 把上传写入 MinIO 时使用：

```java
client.putObject(PutObjectArgs.builder()
    .bucket(bucket)
    .object(storageKey)
    .stream(inputStream, inputStream.available(), -1)
    .contentType(...)
    .build());
```

`InputStream.available()` 的语义是"**当前可立即读取且不阻塞**的字节数估算"，对 Spring `MultipartFile.getInputStream()` 返回的 `BufferedInputStream` 来说该值是 buffer 大小（通常 8KB），不是文件总大小。MinIO Java SDK 的 `stream(stream, objectSize, partSize)` 在 `objectSize >= 0` 时**只读取 `objectSize` 字节**，因此实际只上传 8KB 后就停止，剩余字节静默丢失，得到的文件**末尾被截断**。

### 受影响位置
- `backend/src/main/java/com/campusforum/infra/MinioStorageService.java#upload`（第 56-62 行）

并且与 `ResourceService#upload` 改造后的"流式 SHA-256 + 存储"链路冲突：现在上传链路是
```java
DigestInputStream dis = new DigestInputStream(in, sha256);
storageService.upload(dis, originalName, file.getContentType());
sha256Hex = HexFormat.of().formatHex(sha256.digest());
```
当 MinIO 只消费 8KB 时，`sha256` 也只对 8KB 计算，导致**SHA-256 与实际想存储的内容不一致** — 但与 MinIO 实际存的 8KB 又"巧合一致"。这意味着：
- 资源元数据中的 `file_size`（来自 `file.getSize()`，正确）与 `file_sha256`（错误的"截断后哈希"）不一致；
- 同一逻辑文件上传两次但内容不同时仍被去重，因为它们的 8KB 头部相同。

### 触发条件 V(X)
设 X = "用户上传 PDF / DOCX / 图片大小 ≥ 64KB，且 `storage.type=minio`"。在 X 满足时：
- 100% 的资源会被截断；下载/预览时表现为"文件损坏 / PDF 末尾错误"；
- 攻击者构造前 8KB 内容相同、后续不同的两份恶意文件，第二份会被 SHA-256 去重逻辑当作"重复"忽略上传，**复用第一份的 storageKey** — 与 OSS Local 行为不一致，可能引发难以审计的"为什么我上传后是别人的内容"。

### 影响
- C：低/中（SHA-256 碰撞利用面狭窄，但资源去重错配可造成"误下别人的私有文件"）。
- I：高（数据完整性：所有大文件被截断）。
- A：高（业务直接不可用）。
- 跨租户：取决于 SHA-256 是否跨租户去重，当前 `Resource` 实体存在 `tenant_id`，去重 SQL 由 MyBatis-Plus tenant 拦截器加 `tenant_id` 过滤，所以跨租户不直接相通；但**租户内**用户 A 的截断文件可能被用户 B 复用。
- 未授权可达：否（需上传权限）。

### 安全属性 (EARS)
- **WHEN** 上传文件至 MinIO/S3 兼容存储，**THE** 系统 **SHALL** 显式提供文件总字节数（来自 `MultipartFile.getSize()` 而非 `available()`）或使用 multipart upload（`objectSize=-1, partSize>=5MB`）。
- **THE** 上传链路 **SHALL** 保证 `file.getSize()`、实际读取字节数、SHA-256 计算字节数、最终 MinIO 对象 size **完全一致**。
- **THE** 系统 **SHALL** 在上传完成后回查 `statObject` 确认对象大小与 `MultipartFile.getSize()` 相等，不一致即调用 `removeObject` 回滚并抛 `STORAGE_ERROR`。

### 保留性 (Preservation)
- `storage.type=local` / `oss` 路径不受影响。
- 现有去重逻辑（基于 `file_sha256`）保留，但要在哈希一致前提下生效。
- 现有 50MB `multipart.max-file-size` 上限保留。

### 建议修复方向
1. `MinioStorageService#upload` 改用：
   ```java
   .stream(inputStream, file.getSize(), -1)
   ```
   接口签名增加 `long size` 参数；或继续用 `-1`（流模式）但显式 `partSize=10485760`（10MB）。
2. `StorageService#upload` 接口签名扩展为 `upload(InputStream, originalName, contentType, long size)`，三个实现类同步更新。
3. `ResourceService#upload` 把 `file.getSize()` 显式传下去。
4. 上传完成后调用 `client.statObject(...)` 回查 size，不一致即回滚。
5. 增加集成测试 `ResourceUploadLargeFileIT`：上传 5MB / 20MB PDF，断言下载后字节级一致。


---

## 漏洞 7：限流 key 包含完整 URI（含 path variable），按 ID 分桶可被绕过 [High]

### 风险描述
`RateLimitInterceptor#preHandle` 拼装限流 key 时使用 `request.getRequestURI()`：

```java
rateLimitKey = "rate_limit:user:" + userId + ":" + path;  // 已登录
rateLimitKey = "rate_limit:ip:" + ip + ":" + path;        // 匿名
```

含 path variable 的端点（`/api/v1/posts/{id}`、`/api/v1/users/{id}`、`/api/v1/resources/{id}/download`、`/api/v1/spaces/{id}/posts` 等）每个 ID 都形成**独立的限流桶**。攻击者只要对不同 ID 轮询即可绕过 200 req/min 配额：
- 攻击者枚举 1..N 个 post ID，每个 ID 1 次请求 → 不会触发限流；
- `properties.getOverrides()` 通过 `METHOD path` 字符串精确匹配，path variable 端点完全无法挂上 override；
- 资源下载 `/api/v1/resources/{id}/download` 未在 overrides 列表，直接走"已认证 200/min" — 但因为 path 含 ID，每个资源单独 200/min，总流量 = N × 200/min。

### 受影响位置
- `backend/src/main/java/com/campusforum/infra/ratelimit/RateLimitInterceptor.java#preHandle`（第 56-66 行）
- `backend/src/main/resources/application.yml#rate-limit.overrides`（key 全部是字面量路径）

### 触发条件 V(X)
设 X = "攻击者获得任意一个有效登录 token 或匿名 IP，目标端点存在 path variable"。在 X 满足时：
- 单 token 对 `/api/v1/posts/{1..1000}` 各发 199 req → 总计 199_000 req/min；
- 资源下载暴力枚举 + 配合 SignedUrlService 默认密钥（漏洞 3）即可大批量拖取私有资源。

### 影响
- C：中（配合其他漏洞放大数据拉取速率）。
- I：低。
- A：高（DoS 防护被绕过）。
- 跨租户：是（限流粒度不分租户）。
- 未授权可达：部分匿名端点（如帖子详情若开放游客）。

### 安全属性 (EARS)
- **WHEN** 限流 key 派生自请求路径，**THE** 系统 **SHALL** 使用"路由模板"（route template，如 `/api/v1/posts/{id}`）而非具体 URI，保证 path variable 不分桶。
- **THE** 限流配置 `rate-limit.overrides` **SHALL** 支持路由模板作为 key（如 `[GET /api/v1/posts/{id}]`），拦截器在拼接 key 时使用 `HandlerMapping` 暴露的 `bestMatchingPattern` 属性。
- **WHERE** 是高成本端点（资源下载、AI、上传、登录），**THE** 系统 **SHALL** 单独配置 max-requests 上限。

### 保留性 (Preservation)
- 现有 `application.yml` 中已有的 overrides（`/api/v1/auth/login`、`/api/v1/ai/*`、`/api/v1/resources` POST）保持生效。
- 限流 fail-closed 路径（敏感 path）保持不变。

### 建议修复方向
1. 改写 `RateLimitInterceptor#preHandle`：通过 `request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)` 取路由模板代替 `request.getRequestURI()`。
2. 当模板缺失时回退到原 URI 但同时降低 max-requests（避免新模式失效时还能限流兜底）。
3. `application.yml` `rate-limit.overrides` 增加：
   - `[GET /api/v1/resources/{id}/download]`：30/min
   - `[GET /api/v1/resources/{id}/preview]`：30/min
   - `[GET /api/v1/posts/{id}]`：120/min
4. 写一组 SpringBootTest 验证：对 `/api/v1/posts/1`、`/api/v1/posts/2` 各发 200 次，第 201 次起两个端点都应该 429（共享桶）。


---

## 漏洞 8：WebSocket 旧 `?token=` 兼容路径默认开启，主令牌持续暴露在 URL 与 access log [High]

### 风险描述
`TenantHandshakeInterceptor#beforeHandshake` 优先识别 `ticket`，但 `securityProperties.getWsTicket().isEnforced()` 默认 `false`：未提供 ticket 时回退到 `verifyByLegacyToken`，从 query string 直接取 `token` 字段并通过 `StpUtil.getLoginIdByToken(token)` 验证。

```java
if (securityProperties.getWsTicket().isEnforced()) {
    response.setStatusCode(HttpStatus.UNAUTHORIZED);
    return false;
}
return verifyByLegacyToken(request, response, attributes);
```

在生产 `WS_TICKET_ENFORCED=false` 默认值下：
- 任何前端旧版本仍然把 `?token=<Sa-Token>` 拼到 URL 上；
- nginx 默认会把完整 URL（含 query string）写进 `access.log`；
- 浏览器历史、Referer、CDN log、APM 监控（如 SkyWalking、ARMS）会拷贝该 URL；
- token 一旦泄漏，攻击者可在 7 天内任意伪装受害者（在 active-timeout 4h 内尤其顺畅）。

`deploy/SECURITY.md §8.1` 的"建议先按默认 false 部署一周观察前端兼容情况，再切到 true"策略本身没问题，但**没有自动化触发条件**：等运维忘了切，主令牌就会持续泄漏。

### 受影响位置
- `backend/src/main/java/com/campusforum/tenant/websocket/TenantHandshakeInterceptor.java#beforeHandshake`、`verifyByLegacyToken`
- `backend/src/main/resources/application.yml#security.ws-ticket.enforced`（默认 false）
- `deploy/.env.example` `WS_TICKET_ENFORCED=false`
- 前端 `frontend/src/composables/useWebSocket.ts` 已经迁移到 ticket，但旧版客户端 / 第三方接入仍可能用 token query。

### 触发条件 V(X)
设 X = "运维按默认配置部署 + 任意旧客户端走 `wss://host/ws/notify?token=<sa-token>`"。在 X 满足时：
- nginx access.log 出现明文 token；
- 该 token 在 Sa-Token Redis 中映射 → loginId；攻击者复制粘贴即可在 4h 内重放。

### 影响
- C：高（账号长期可控）。
- I：高（伪装受害者发帖、私信、上传）。
- A：低。
- 跨租户：否（限于受害者租户）。
- 未授权可达：否（前提是攻击者能读 nginx log / 浏览器历史）。

### 安全属性 (EARS)
- **WHEN** 应用启动时间 ≥ "ws-ticket 灰度截止日期"（建议从 commit 时间起 30 天），**THE** 系统 **SHALL** 强制 `WS_TICKET_ENFORCED=true`，不再受 ENV 覆盖。
- **WHEN** legacy token 路径被命中，**THE** 系统 **SHALL** 累加 `ws_legacy_token_used_total` 指标，并定期采样输出 WARN 日志，提示具体客户端 IP / UA。
- **WHEN** 运行 ≥ 7 天且 `ws_legacy_token_used_total = 0`，**THE** 系统 **SHALL** 在启动日志中打印"建议关闭 legacy 路径"提示。
- **WHERE** profile = `prod` 且 `WS_TICKET_ENFORCED=false`，**THE** `SecurityStartupValidator` **SHALL** 输出 ERROR 日志（不阻断启动以保兼容期），并附带"距强制启用还剩 X 天"。

### 保留性 (Preservation)
- 兼容期内旧客户端可继续工作；ticket 路径不受影响。
- 切到 true 后旧客户端会立刻 401，需要前端配合升级（已经升级，所以风险很低）。

### 建议修复方向
1. 在 `SecurityStartupValidator` 增加"强制日期"机制：从代码 commit 时间起算 30 天后强制启用 ticket 模式。
2. `verifyByLegacyToken` 内增加 `Counter` 埋点 + 采样日志（限 1 次/分钟）。
3. nginx 配置增加：在 `/ws/` location 块内 `if ($args ~* (^|&)token=) { return 400; }` 作为纵深防御（仅在确认前端全部切换后开启）。
4. 文档更新：`deploy/SECURITY.md §8.1` 把 `WS_TICKET_ENFORCED=true` 调整为部署默认推荐值。


---

## 漏洞 9：搜索接口暴露邮箱，可批量做用户枚举 [High]

### 风险描述
`SearchService#searchUsers` 的 LIKE 字段集合包括 `email`：

```java
qw.and(w -> w.like(User::getNickname, keyword)
        .or().like(User::getEmail, keyword)
        .or().like(User::getStudentNo, keyword));
```

且关键字仅做 `[^\p{L}\p{N}\s]` 字符过滤，没有"是否包含 @"或"是否像邮箱"的限制。攻击者只要在搜索框输入 `@163.com`、`@qq.com`、`stu`、`2024` 等就可以批量拿到该域所有用户列表（含 `nickname`，配合 `studentNo` 可推导真实姓名 / 学号映射）。即使 `PublicUserVO.from(user)` 把邮箱字段去掉了，**LIKE 命中本身就是邮箱泄漏的副信道** — 搜索 `@163.com` 命中即代表邮箱后缀确认。

更严重的是 `SearchResultVO.author` 是 `PublicUserVO`，字段里**仍包含 `studentNo`**（`PublicUserVO` 已脱敏到只剩 nickname/avatar/role/college 等？需要确认）。

### 受影响位置
- `backend/src/main/java/com/campusforum/search/service/SearchService.java#searchUsers`（第 200-219 行）
- `backend/src/main/java/com/campusforum/user/dto/PublicUserVO.java`（需要进一步审计字段集）

### 触发条件 V(X)
设 X = "任意已登录用户调用 `GET /api/v1/search?type=USER&q=@163.com&limit=50`"。在 X 满足时：
- 单次返回最多 50 个匹配用户的 `nickname` + （潜在的）`studentNo`；
- 攻击者翻页（cursor = 上一页最小 ID）即可枚举全租户用户。

### 影响
- C：高（个人信息泄漏 — 邮箱后缀、学号前缀可与外部数据集做交叉关联推导真实身份）。
- I：低。
- A：低。
- 跨租户：否（受 MyBatis-Plus tenant 拦截器约束）。
- 未授权可达：否（需登录），但任意用户均可。

### 安全属性 (EARS)
- **THE** 用户搜索 **SHALL** 仅按 `nickname` 进行 LIKE 匹配，禁止按 `email` 与 `studentNo` 模糊匹配。
- **WHEN** 关键字长度 < 2 字符，**THE** 系统 **SHALL** 返回空列表。
- **WHEN** 关键字疑似邮箱 / 学号格式（包含 `@` 或长度 ≥ 8 全数字），**THE** 系统 **SHALL** 仅在调用方为 `TENANT_ADMIN` / `SUPER_ADMIN` 时允许该字段匹配。
- **THE** `SearchResultVO` **SHALL** 不包含 `studentNo`、`email` 字段；管理后台需要用户搜索时使用 `/api/v1/admin/users` 而非公共搜索。

### 保留性 (Preservation)
- 帖子 / 资源 / 空间搜索的 LIKE 字段不变。
- 管理员后台用户搜索仍能精确按 email / studentNo 匹配（通过 AdminUserController.list）。

### 建议修复方向
1. `searchUsers` 移除 `email` 与 `studentNo` 的 LIKE 分支；只保留 `nickname` LIKE。
2. 关键字最小长度 2，最大长度 32（已有），增加 "纯数字 / 含 @ 时拒绝" 的额外校验。
3. 审计 `PublicUserVO` 字段，确保返回的字段集与"匿名/路人可见"语义一致。
4. 文档说明"用户搜索仅按昵称"，引导管理员通过后台搜索定位用户。


---

## 漏洞 10：登录接口被 `rate-limit.exclude-patterns` 排除，导致登录限流 override 失效 [High]

### 风险描述
`application.yml` 同时声明了两条互相矛盾的配置：

```yaml
rate-limit:
  exclude-patterns:
    - /actuator/**
  overrides:
    "[POST /api/v1/auth/login]":
      max-requests: 10
      window-seconds: 60
```

而 `RateLimitProperties#excludePatterns` 的**默认值**是：
```java
private List<String> excludePatterns = List.of("/actuator/**", "/api/v1/auth/login");
```

`@ConfigurationProperties` 的字段与 yml 配置之间是 "替换" 还是 "合并" 与字段类型有关：`List<String>` 在 Spring Boot 中默认是**整体替换**（yml 完全覆盖默认值），所以 yml 的 `exclude-patterns: [/actuator/**]` 会覆盖默认 list，**`/api/v1/auth/login` 不会再被排除**。这一点是 OK 的。

**但**：`RateLimitInterceptor#preHandle` 的代码顺序是：

```java
// 排除路径检查
for (String pattern : properties.getExcludePatterns()) {
    if (matchPath(path, pattern)) return true;  // 先排除
}
// ...
// 命中 override 配置
RateLimitProperties.LimitConfig config = properties.getOverrides().get(endpointKey);
```

漏洞条件：
- 当运维使用了"老版本" `application.yml` 模板（默认 list 含 `/api/v1/auth/login`），或者后续有人补回该排除项；
- 或者运维通过 ENV 设置 `RATE_LIMIT_EXCLUDE_PATTERNS=/actuator/**,/api/v1/auth/login`；
- 排除分支命中后 **override 5次/min** 完全失效，登录就退化到无限流 — 暴力破解只受 `LoginLockoutService` 制约，但锁定阈值是 10 次/15 分钟（accountMax）+ 20 次/15 分钟（IP），实际上是个**很弱的兜底**。

另外即使在当前 `application.yml` 配置下，敏感路径列表（`SENSITIVE_PREFIXES`）走 fail-closed 这点是正确的，但 `exclude-patterns` 命中是**先返回 true 跳过敏感检查**的，这意味着 `/api/v1/auth/login` 被排除后 fail-closed 也失效。

### 受影响位置
- `backend/src/main/resources/application.yml#rate-limit`（缺乏注释说明替换语义）
- `backend/src/main/java/com/campusforum/infra/ratelimit/RateLimitProperties.java#excludePatterns`（默认值不合理）
- `backend/src/main/java/com/campusforum/infra/ratelimit/RateLimitInterceptor.java#preHandle`（排除分支优先级过高）

### 触发条件 V(X)
设 X = "运维误把 `/api/v1/auth/login` 加进 `exclude-patterns`"。在 X 满足时：
- 登录接口完全不被限流；
- 攻击者按 IP / 账号轮询暴力破解，仅靠 LoginLockoutService 阈值守护；
- 同时 `/api/v1/auth/forgot-password`、`/api/v1/auth/email-code` 等同样的"敏感写"路径若被误排除也会失守。

### 影响
- C：中（暴力破解成功率提升）。
- I：低。
- A：高（登录服务可被打挂）。
- 跨租户：是。
- 未授权可达：是。

### 安全属性 (EARS)
- **THE** `RateLimitProperties#excludePatterns` 默认值 **SHALL** 只包含 `/actuator/**`（已是 yml 中的事实值），代码默认值同步移除 `/api/v1/auth/login`。
- **THE** 系统 **SHALL** 在启动期校验 `excludePatterns` 不包含任何 `SENSITIVE_PREFIXES` 路径，命中即抛 `IllegalStateException`。
- **WHEN** 限流拦截器检查到 path ∈ `SENSITIVE_PREFIXES` ∩ `excludePatterns`，**THEN** 必须忽略排除并仍走 fail-closed，记录 ERROR 日志告警。

### 保留性 (Preservation)
- `/actuator/**` 仍可由运维排除（被 nginx 与 TenantResolutionFilter 双重屏蔽，多一层不影响）。
- 现有 override 列表保留。

### 建议修复方向
1. 移除 `RateLimitProperties#excludePatterns` 默认值中的 `/api/v1/auth/login`。
2. 在启动期由 `SecurityStartupValidator` 校验排除路径不与敏感前缀冲突。
3. 在 `RateLimitInterceptor#preHandle` 增加 "敏感路径不可被排除" 二次检查（即使运维人为加了也忽略）。


---

## 漏洞 11：邮箱验证码 Redis 不可用时 `isRateLimited` fail-open，可被刷码 / 邮件轰炸 [High]

### 风险描述
`EmailVerificationCodeService#isRateLimited` 在 Redis 异常时直接 `return false`：

```java
} catch (Exception e) {
    log.warn("Redis unavailable for email code rate limit check, allowing request: {}", e.getMessage());
    return false;
}
```

随后 `sendCode` 会真实地调用 `emailService.sendVerificationCode(...)`。结合敏感路径 fail-closed 的整体策略（漏洞 10 提到的 `RateLimitInterceptor` 对 `/api/v1/auth/email-code` 走 fail-closed），表面看 Redis 故障时 `RateLimitInterceptor` 会先拦下；但：
- 当 Redis 故障 < `tryAcquireFailClosed` 重试时间窗口（脚本执行成功但 RedisTemplate 后续 `expire` 失败的边界），仍可能让请求落到 service；
- 且 `RateLimitInterceptor` 的限流 key `rate_limit:user:<id>:/api/v1/auth/email-code` 与 service 内"按邮箱+用途"的限流 key 是**两个维度**：拦截器是按用户 ID/IP，service 是按邮箱+用途；
- 攻击者使用未登录路径 + 不同 IP + 同一邮箱反复请求时，service 维度才是真正起作用的那条；它一旦 fail-open 就直接放行邮件发送，被滥用做**邮件轰炸 / 钓鱼**。

并且 `verifyAndConsume` 的字符串比较是 `stored.equals(inputCode.trim())`，**不是常量时间比较**，理论上存在毫秒级时序信号；虽然 6 位数字爆破空间小（10^6），且配合 `LoginLockout` 5 次锁，但仍是不必要的弱点。

### 受影响位置
- `backend/src/main/java/com/campusforum/user/service/EmailVerificationCodeService.java#isRateLimited`（第 80-91 行）
- `backend/src/main/java/com/campusforum/user/service/EmailVerificationCodeService.java#verifyAndConsume`（第 70-76 行）

### 触发条件 V(X)
- V1（轰炸）：X = "Redis 抖动 / 慢查询窗口期，攻击者 1 秒内对 `/api/v1/auth/email-code?email=victim@x.com` 发 N 次"。 fail-open 即直接发 N 次邮件给受害者邮箱。
- V2（时序）：X = "攻击者掌握目标邮箱 + 验证码消费窗口"。利用 `equals` 时序差异减少爆破成本。

### 影响
- C：低。
- I：中（邮件轰炸 / 二次确认链路被滥用）。
- A：中（验证码邮件发送是高成本，可触发 SMTP 服务商拉黑）。
- 跨租户：是。
- 未授权可达：是（发送验证码是匿名接口）。

### 安全属性 (EARS)
- **WHEN** Redis 不可用、目标路径属于"短信/邮件发送 / 资金 / 凭证类"敏感操作，**THE** 系统 **SHALL** 走 fail-closed 拒绝（与限流敏感路径策略一致）。
- **THE** 邮件验证码发送 **SHALL** 同时维护"邮箱维度"与"IP 维度"两条 Redis 计数；任一超过阈值即拒绝。
- **THE** `verifyAndConsume` **SHALL** 使用 `MessageDigest.isEqual` 等常量时间比较函数。
- **THE** 验证码消费成功后，无论后续业务结果如何，**SHALL** 已经从 Redis 删除，避免重放（当前已实现）。

### 保留性 (Preservation)
- 业务层"邮箱不存在不暴露"的语义保留。
- 验证码 6 位数字 + 10 分钟过期不变。

### 建议修复方向
1. `isRateLimited` 改为 fail-closed：异常时返回 true（拒绝），并使用 `RATE_LIMITED` 错误码外抛；
2. `verifyAndConsume` 比较改为 `MessageDigest.isEqual(stored.getBytes, inputCode.trim().getBytes)`；
3. 增加"IP 维度发送速率"键 `email_code_rate_ip:<ip>:<scene>`：1 IP / 1 分钟 / 3 次邮件；
4. SMTP 失败时记录 ERROR 但**不**让计数回滚，避免攻击者利用 SMTP 错误重试。


---

## 漏洞 12：OpenAI 调用使用进程级共享 API Key（`@Value` 注入），多租户场景下交叉泄漏 [High]

### 风险描述
`OpenAiCompatService` 通过构造函数 `@Value("${ai.api-key}")` 注入 API Key：

```java
public OpenAiCompatService(@Value("${ai.base-url}") String baseUrl,
                           @Value("${ai.api-key}") String apiKey,
                           @Value("${ai.model:deepseek-chat}") String model) { ... }
```

而 `TenantAwareAiService#delegate()` 在调用时虽然读取了**当前租户**的 AI 配置并 `new OpenAiCompatService(baseUrl, apiKey, model)`，但**该 service 是 Spring Bean** —— 容器中早就存在一个用 `application.yml` 全局 ai-key 构造的单例（`@ConditionalOnProperty(name = "ai.provider", havingValue = "openai")` 在 prod 默认未开启时不创建，但开启了就有）；同时 `delegate()` 每次调用都 `new OpenAiCompatService(...)`，**绕过 Spring 容器**，新实例每次都走 `createRestTemplate()` 创建新 SafeHttpClient — 这本身不是漏洞，但有副作用：
1. 每次请求构造一个 `SimpleClientHttpRequestFactory`，性能不佳但更重要的是**没有代理/超时统一管理**；
2. 当 `provider` 不为 `"openai"` 时（如 `"deepseek"`、未来 `"qwen"`），代码 fallback 到 `mockAiService`，**租户 AI 失效但不告警** — 配置错误时管理员看到"AI 工作正常但其实是 mock 在回答"。

最关键的安全问题是：`TenantAwareAiService.stringValue()` 是**死代码未使用**，`delegate()` 取 apiKey 用 `config.get("apiKey")` —— 此 map 来自 `tenantService.resolveAiCredentials(tenantId)`，里面**已经解密**为明文。但 `TenantAwareAiService#delegate()` 把 `apiKey` 作为 `String` 直接传给 `new OpenAiCompatService(...)` 构造函数，进而被 `restTemplate.exchange(...)` 时通过 `setBearerAuth(apiKey)` 写入 HTTP header。该过程没有问题。

但 `OpenAiCompatService` 同时是一个 `@Service @ConditionalOnProperty(name = "ai.provider", havingValue = "openai")` Bean。当 `application.yml` 的 `ai.provider=openai` 时，**Spring 容器创建一个全局 Bean，使用 application.yml 中 `ai.api-key`**；这个 Bean 实际**永远不会被调用**（因为 `@Primary TenantAwareAiService` 持有 `MockAiService` 并在 delegate 中显式 new）。但它确实存在 — 持有一个全局 API Key、可能被 `@Autowired` 错误地注入到其他组件，造成"管理员后台租户 AI 配置看似按租户隔离，但实际上某些路径会用到全局 key"的灰色地带。

更直接的高危是 `TenantAwareAiService#delegate()` 在租户配置缺失时回退到 `mockAiService`，但这里**没有租户级审计日志**：当 SUPER_ADMIN 给 A 租户配了真实 key、又开启了 B 租户没配置 key 时，B 的用户问 AI 会得到 mock 的回答，业务上看起来正常 — 但如果某次 `cryptoService.decrypt` 抛 `CryptoException`，方法会被外层 catch 吞掉降级到 mock，**租户的真实 key 解密失败被静默降级**，可能让攻击者通过特定输入（破坏密文格式 / 触发降级）影响业务可用性。

### 受影响位置
- `backend/src/main/java/com/campusforum/ai/service/OpenAiCompatService.java`（构造函数 `@Value("${ai.api-key}")`）
- `backend/src/main/java/com/campusforum/ai/service/TenantAwareAiService.java#delegate`（每次 new）
- `backend/src/main/java/com/campusforum/tenant/service/TenantService.java#resolveAiCredentials`

### 触发条件 V(X)
- V1（全局 key 残留）：X = "`ai.provider=openai` 在 yml 中开启 + 历史代码中某 controller 直接 `@Autowired AiService aiService`"。在 X 满足时，注入的可能不是 `TenantAwareAiService`（虽然 `@Primary`，但若有人显式 `@Autowired OpenAiCompatService` 就拿到全局 bean），调用走全局 key。
- V2（静默降级）：X = "攻击者通过修改 `tenants.ai_config` JSON（要 SUPER_ADMIN 权限，或借其他写操作）使密文格式损坏"。 在 X 满足时，所有该租户用户的 AI 请求都会变成 mock，正常租户感知不到。

### 影响
- C：中（V1 让全局 key 跨租户使用）。
- I：中（V2 让租户 AI 静默降级，违反"配置即生效"预期）。
- A：低。
- 跨租户：V1 是。
- 未授权可达：否。

### 安全属性 (EARS)
- **THE** AI 调用链路 **SHALL** 不暴露任何"全局 API Key" Bean；`OpenAiCompatService` **SHALL** 改为 `@Scope("prototype")` 或转为非 Bean 工具类，由 `TenantAwareAiService` 显式构造。
- **WHEN** `tenantService.resolveAiCredentials` 抛 `CryptoException`，**THE** 系统 **SHALL** 在审计日志中记录 `AI_DECRYPT_FAIL` 事件，并向租户管理员发出可见告警，而非静默降级。
- **THE** 全局 `application.yml` 中 `ai.api-key` 字段 **SHALL** 被废弃；新增校验在 `prod` profile 下若该字段非空则启动失败（避免遗留 key）。

### 保留性 (Preservation)
- 多租户分别配置 AI 的现有功能不变。
- mock 模式作为容错回退仍保留，但需要审计可见。

### 建议修复方向
1. `OpenAiCompatService` 移除 `@Service` 注解，改为普通类；构造函数不再 `@Value`，由 `TenantAwareAiService` 用工厂方法构造。
2. `TenantAwareAiService#delegate()` 改为缓存 per-tenant client（`Map<Long, OpenAiCompatService>`），减少每请求 new 开销，同时在 `updateAiConfig` 时主动 `evict`。
3. `cryptoService.decrypt` 抛 `CryptoException` 时让 `delegate()` 不再 catch，由 `GlobalExceptionHandler` 统一返回 `CRYPTO_FAILURE`，让管理员看到错误而非"AI 工作正常"假象。
4. `application.yml` 全局 `ai.api-key` 字段加注释 "@deprecated 仅供 mock 测试"，并在启动期 prod profile 校验为空。


---

## 漏洞 13：CSV / XLSX 导出未做权限范围限制，TENANT_ADMIN 可拉走全租户用户邮箱与学号 [High]

### 风险描述
`ExportController` 仅靠 `@SaCheckPermission("tenant:dashboard")` 守门，把 4 类数据（users / posts / audit_logs / reports）以 csv/xlsx 流式导出。但：
1. **粒度太粗**：`tenant:dashboard` 是看板权限，跟"导出全表用户隐私"是两个语义。任何可看 dashboard 的管理员都能导。
2. **字段过多**：`exportUsersCsv`/`exportUsersXlsx` 输出列含 `email, nickname, student_no, college, major, grade, role, points, status, created_at` —— 邮箱和学号是高敏感字段，PII 直接对管理员客户端可见，且会进入运维下载历史、邮件附件、第三方表格软件的内存。
3. **无审计**：导出操作没有调用 `auditLogService.log(...)` 留痕，事后无法追责。
4. **无配额**：`BATCH_SIZE=1000` 是查询批次大小，但**总行数无上限**，攻击者一次能拉百万级行。
5. **CSRF**：`@PostMapping("/{dataType}")` 用 `RequestParam("format")` 即可触发；如果攻击者诱使管理员点开恶意页面发起 POST（或通过反射型 XSS）就能下载，攻击者通过同源 fetch 拿到结果。当前 `CorsConfig.allowCredentials=false` 让 CSRF 难度提高（攻击者无法读响应），但同站攻击仍是问题。

### 受影响位置
- `backend/src/main/java/com/campusforum/admin/export/ExportController.java`
- `backend/src/main/java/com/campusforum/admin/export/ExportService.java`

### 触发条件 V(X)
设 X = "TENANT_ADMIN 账号被钓鱼 / 内鬼 / token 被窃"。在 X 满足时：
- 一次 `POST /api/v1/admin/export/users?format=xlsx` 拉走全租户邮箱+学号；
- 拉 `audit_logs` + `reports` 可绘制完整用户行为画像；
- 行为不被审计日志记录。

### 影响
- C：极高（PII 集中外泄）。
- I：低。
- A：中（百万级查询拖慢 DB）。
- 跨租户：否（受 tenant 拦截器保护）。
- 未授权可达：否（需要 TENANT_ADMIN）。

### 安全属性 (EARS)
- **WHERE** 导出涉及 PII（用户邮箱 / 学号），**THE** 系统 **SHALL** 拆分独立权限点 `tenant:export:users`，与 `tenant:dashboard` 解耦；由 SUPER_ADMIN 决定哪些 TENANT_ADMIN 可导。
- **WHEN** 导出执行，**THE** 系统 **SHALL** 同步写入 `audit_log`，记录导出类型 / 行数 / 字段集 / 操作者 IP。
- **THE** 导出接口 **SHALL** 强制单次最大行数（建议 50_000），超过返回 400 并提示分批导出。
- **THE** 导出接口 **SHALL** 限流（每管理员 1 次/分钟），通过 `rate-limit.overrides` 配置。
- **THE** 默认导出字段 **SHALL** 把 `email` 与 `student_no` 改为脱敏（`abc***@example.com` / `2024***1`），仅在 SUPER_ADMIN 显式 `?fullPii=true` 且已审计时返回明文。

### 保留性 (Preservation)
- 现有 4 类导出能力保留。
- BOM + UTF-8 + CSV 公式注入防护保留。

### 建议修复方向
1. `AdminStpInterface` 增加 `tenant:export:users`、`tenant:export:posts` 等权限点；导出 controller 改用细粒度权限。
2. `ExportService` 增加行数计数与 50_000 上限；超过抛 `BATCH_SIZE_EXCEEDED`。
3. `ExportController#export` 写审计日志：`auditLogService.log("EXPORT", dataType, null, "format=" + format)`。
4. 用户/学号字段脱敏开关 + 默认脱敏。
5. 配置 `rate-limit.overrides` 添加导出端点 1/min。


---

## 漏洞 14：管理员 Dashboard 计数未走租户隔离辅助链路，TENANT_ADMIN 看到的是"全租户聚合" [High]

### 风险描述
`AdminController#dashboard` 直接调用：

```java
userMapper.selectCount(null);
postMapper.selectCount(null);
spaceMapper.selectCount(null);
commentMapper.selectCount(null);
```

`selectCount(null)` 会被 MyBatis-Plus 的 `TenantLineInnerInterceptor` 自动改写为 `WHERE tenant_id = ?`，**前提是 `TenantContext.getTenantId()` 已经被 TenantResolutionFilter 设置**。这部分行为目前确实正确（已有 `IllegalStateException` 兜底）。

**但**：`SUPER_ADMIN` 在 standalone 模式下永远落到 `standaloneTenantId=1`，而在 multi 模式下，super-admin 切换租户的方式是修改自身 session 的 `tenantId`（在 `MultiTenantResolver` 中通过 `StpUtil.getSession().get("tenantId")` 决定）—— 但 `completeLogin` 写入 session 的 tenantId **就是用户所在 tenantId**，没有给 SUPER_ADMIN 提供"切换查看租户"的能力，导致 SUPER_ADMIN 看到的 dashboard 实际上仅是自身所属租户的数据。这其实是个**功能缺失而非安全漏洞**。

但**真正的安全问题在于**：`AdminController#dashboard` 上的 `@SaCheckPermission("tenant:dashboard")` 与租户隔离的关系是：
- 该权限来自 `AdminStpInterface.getPermissionList`，根据当前用户角色返回；
- TENANT_ADMIN 只能看自己租户的 dashboard，没问题；
- **但 dashboard 之外**：`AdminUserController#list`、`AdminPostController#list`、`AdminAuditLogController#list` 都依赖 MyBatis-Plus 自动注入 `tenant_id`，对租户隔离的健壮性100%依赖 TenantLineInnerInterceptor 的"无差别注入"。**`TENANT_IGNORE_TABLES = {"tenants", "achievements", "sensitive_words"}`** 的列表显式跳过租户隔离 — 但如果某些表（如未来新增的 `system_config` / `notifications_template`）忘了加 `tenant_id` 列、又没加入 ignore list，会让 MyBatis-Plus 改写出 `WHERE tenant_id=?` 命中**不存在的列**，启动期 SQL 失败；反过来，如果**应该有租户隔离的表被错误加入 ignore list**，则 TENANT_ADMIN 即可看到全租户数据。

实际审计发现一个具体问题：
- `audit_logs` 表的 schema 是否带有 `tenant_id` 字段？`AuditLogService` 的 `log()` 没有显式写入 tenant_id；如果表里有该列，MyBatis-Plus 会自动注入；**如果表里没有 `tenant_id` 列**，启动期就会失败 — 但实际项目跑得起来，说明该表**有** `tenant_id` 列；那么写入时也由拦截器从 `TenantContext` 取 — 没问题；
- 但**SUPER_ADMIN 看 audit_log 时**因为 session.tenantId 仍是 SA 自己租户，看不到其他租户的审计记录，运维感知不到跨租户违规事件。

最直接可被攻击的高风险点是：**`AdminController#dashboard` 在 standalone 模式下、SUPER_ADMIN 账号被攻击者控制时**，攻击者会假定自己看到的是全局数据，但实际上只是 tenant_id=1 的数据 — 这本身不是漏洞；但 `R<DashboardVO>` 的字段命名不带 `tenantId`，**前端无法区分这是单租户还是全站**，可能误导决策（容量评估、性能扩容方向偏差）。

### 受影响位置
- `backend/src/main/java/com/campusforum/admin/controller/AdminController.java#dashboard`
- `backend/src/main/java/com/campusforum/admin/dto/DashboardVO.java`（缺少 `tenantId` 字段）
- `backend/src/main/java/com/campusforum/infra/MyBatisPlusConfig.java#TENANT_IGNORE_TABLES`（缺少自动巡检）

### 触发条件 V(X)
设 X = "未来某次迭代新增表 `xxx_template` 但未加 `tenant_id` 列，又未列入 `TENANT_IGNORE_TABLES`"。在 X 满足时：
- 启动期 SQL 失败（Hard-fail，可被快速发现）；
- 但相反的 V'（新增有 `tenant_id` 列的表却被错列入 ignore），会形成**全站可读** — 这是真正的隐患。

更现实的 X = "运维误把 `audit_log`、`points_log`、`messages` 之一加进 `TENANT_IGNORE_TABLES`"，结果跨租户数据可被任意一个 TENANT_ADMIN 读到。

### 影响
- C：高（一旦发生跨租户读）。
- I：高（同样可写）。
- A：低。
- 跨租户：是。
- 未授权可达：否。

### 安全属性 (EARS)
- **THE** `TENANT_IGNORE_TABLES` 列表 **SHALL** 集中维护，且任何修改必须经过 code review 并配套单元测试。
- **THE** 系统 **SHALL** 在启动期对 `TENANT_IGNORE_TABLES` 中每张表执行 schema 校验，确保它们**确实没有** `tenant_id` 列；命中即抛 `IllegalStateException`。
- **THE** `DashboardVO` **SHALL** 包含 `tenantId` 与 `tenantCode`，让前端明确数据范围。
- **THE** 系统 **SHALL** 提供 `/api/v1/admin/_tenant-isolation-test` 内部接口（仅 SUPER_ADMIN，开发环境），返回当前 SQL 改写视图，便于回归。

### 保留性 (Preservation)
- 现有 4 类 dashboard 计数返回不变。
- TenantLineInnerInterceptor 的核心行为不变。

### 建议修复方向
1. `TenantStartupValidator` 新增"ignore-tables schema 校验"步骤。
2. `DashboardVO` 增加 `tenantId/tenantCode` 字段，前端据此显示"当前 tenant 范围"。
3. SUPER_ADMIN 跨租户切换通过单独的"租户切换 API"（设置一个临时 `viewTenantId`），与受保护的 audit-log 关联。
4. `audit_logs` 表的 `tenant_id` 列加 NOT NULL 约束 + 索引，DB 层兜底。


---

## 漏洞 15：`UserController#uploadProfileAsset` 在 `local` 存储模式下生成 `/uploads/<key>` 链接，但该路径已被删除，前端会 404；MinIO 模式下却返回 storageKey 作为 URL [High]

### 风险描述
`UserController#uploadProfileAsset` 写出：

```java
String url = "local".equalsIgnoreCase(storageType) ? "/uploads/" + storageKey : storageKey;
return R.ok(UserAssetUploadVO.builder().url(url).storageKey(storageKey).build());
```

而 `WebMvcConfig` 的注释明确写着 "原先存在的 `/uploads/**` 静态资源映射已删除"。这意味着：
- `storage.type=local` 时，前端拿到 `/uploads/2025-01-01/xxx.png` 浏览器请求会 404；
- `storage.type=minio` 时，url 直接是 `2025-01-01/xxx.png` 这种 storageKey — 前端把它当 URL 用会变成 `https://your-domain/2025-01-01/xxx.png`，依然 404；
- 用户头像 / 封面 url 写进 `users.avatar_url` 后，调用 `assertHostAllowed(req.getAvatarUrl())` 时被 `URI.create(...)` 解析为相对路径 → host = null → 抛 `BAD_REQUEST` "URL 解析失败"。

实际后果：**头像上传链路在 prod 环境完全无法工作**，但代码看起来"成功上传"。这虽然首先是个功能 bug，但**安全侧面**包含两点：
1. 用户为了规避头像不可用问题会去找 service：可能改去用外部 URL（图床）→ 落入 `assertHostAllowed` 域名白名单逻辑，但生产 ENV `ALLOWED_ASSET_HOSTS` 默认空 → 任意外部 URL 都会被允许（白名单为空时 `assertHostAllowed` 直接 return），形成**SSRF 反射型加载场景** + 第三方追踪像素。
2. `storageKey` 直接当 URL 返回会暴露存储路径结构（年月日 + UUID 命名），增加路径推测攻击成本。

### 受影响位置
- `backend/src/main/java/com/campusforum/user/controller/UserController.java#uploadProfileAsset`（构造 url 部分）
- `backend/src/main/java/com/campusforum/user/service/UserService.java#assertHostAllowed`（白名单为空 → 全放行的语义存疑）
- `backend/src/main/java/com/campusforum/infra/WebMvcConfig.java`（删除了 `/uploads/**` 映射）

### 触发条件 V(X)
- V1：X = "生产 `storage.type=minio`（默认）+ 用户上传头像"。 url 错误 → 头像 404 → 引发用户走"外部图床"路径。
- V2：X = "管理员未配置 `ALLOWED_ASSET_HOSTS` ENV"。 任意 URL 被接受，触发反射加载与外站追踪。

### 影响
- C：低/中（追踪像素可探测用户在线状态、IP、UA、时区）。
- I：低。
- A：低（功能层 404）。
- 跨租户：否。
- 未授权可达：否。

### 安全属性 (EARS)
- **THE** `uploadProfileAsset` **SHALL** 返回**可访问的 URL**：
  - `local` 模式下通过签名 URL 接入 ResourceController 风格的下载端点；
  - `minio`/`oss` 模式下返回 `presignedGetObject(...)` 短期下载链接，或基于 SignedUrlService 的内部代理 URL `/api/v1/users/avatars/{key}?sig=...`。
- **WHEN** `security.upload.allowed-asset-hosts` 为空，**THE** 系统 **SHALL** 视为"仅允许本站存储域名"，而非"任意域名"。
- **THE** 头像 URL 必须经过域名白名单二次校验，white-list 默认包含本站 storage 域名 + CDN。

### 保留性 (Preservation)
- 用户能正常显示头像与封面（核心用户体验）。
- 其他白名单为"已配置"的场景行为不变。

### 建议修复方向
1. 引入新接口 `POST /api/v1/users/me/assets`：返回 `{ url: "/api/v1/users/avatars/<id>", storageKey, ... }`；新增 `GET /api/v1/users/avatars/{id}` 流式代理，复用 ResourceController 风格的鉴权与签名。
2. `assertHostAllowed` 把"空白名单"语义改为"仅本站"；`SecurityProperties.Upload` 提供 `defaultSelfHosts`（从 `${storage.minio.endpoint}` 推导）。
3. 前端 `Profile.vue` 拿到 url 后直接 `<img :src="user.avatarUrl" />`，无需额外处理。


---

## 漏洞 16：`MessageController#send` 用 `Map<String, String>` 接收，缺少校验导致越权与内容篡改 [High]

### 风险描述
私信发送接口未使用强类型 DTO + Bean Validation：

```java
@PostMapping
public R<MessageVO> send(@RequestBody Map<String, String> body) {
    long senderId = StpUtil.getLoginIdAsLong();
    long receiverId = Long.parseLong(body.get("receiverId"));
    String content = body.get("content");
    String imageUrl = body.get("imageUrl");
    return R.ok(messageService.send(senderId, receiverId, content, imageUrl));
}
```

问题：
1. **`receiverId` 无校验**：`Long.parseLong(null)` 会抛 `NumberFormatException` 走到 `GlobalExceptionHandler` 的兜底 → 500。
2. **`content` 无长度限制**：可发送数 MB 字符串，触发存储 / 推送 WebSocket 的内存压力。
3. **`imageUrl` 完全无校验**：可填 `javascript:` / `data:text/html,...` / 任意外站，前端渲染时若用 `<img :src="msg.imageUrl">` 没问题（浏览器忽略 javascript: img），但若做"消息预览"可能用 `<a :href>` 或预览组件，触发 XSS / SSRF 反射。
4. **跨租户**：`MessageService#send` **未校验 receiverId 与 senderId 是否同一租户**！MyBatis-Plus 在 `userMapper.selectById(receiverId)` 上自动加 tenant_id 过滤，所以"找不到接收者"会让 `receiver == null` 抛 `USER_NOT_FOUND` — 这条防线在 — 但这是**隐式依赖**，没有显式断言 + 没有审计日志，未来如果 `findById` 路径变了就立刻失效。
5. **敏感词不过滤**：私信 `content` 没过 `SensitiveWordService`。
6. **没有发送频率限制**：`/api/v1/messages` POST 不在 `rate-limit.overrides` 中，仅受全局 200/min 制约 — 可被滥用做骚扰 / 钓鱼。

### 受影响位置
- `backend/src/main/java/com/campusforum/message/controller/MessageController.java#send`（行 17-22）
- `backend/src/main/java/com/campusforum/message/service/MessageService.java#send`（行 27-50）

### 触发条件 V(X)
- V1：X = "已登录攻击者 POST `/api/v1/messages` body={ receiverId: 999, content: 'A'.repeat(1000000), imageUrl: 'data:text/html,<script>...</script>' }"。
- V2：X = "已登录攻击者用脚本对一组 receiverId 发 1000 条短消息"。
- V3：X = "前端某次重构把消息预览改成 `<img-link>` 包装，触发外链加载追踪用户 IP"。

### 影响
- C：中（私信内容可包含 XSS 载荷供他人查看时触发；imageUrl 反射加载追踪）。
- I：中（骚扰 / 钓鱼 / 钓鱼链接）。
- A：高（百万字符 message 写入 + WebSocket 广播会拖慢服务）。
- 跨租户：否（隐式由 selectById 防御）。
- 未授权可达：否。

### 安全属性 (EARS)
- **THE** 私信发送接口 **SHALL** 接受强类型 DTO `SendMessageRequest`，含 `@NotNull receiverId`、`@Size(max=2000) content`、`@Size(max=500) @Pattern(...)` `imageUrl`。
- **WHEN** `imageUrl` 非空，**THE** 系统 **SHALL** 强制 `^https?://` 协议，并经过头像/资源同款 `assertHostAllowed`。
- **THE** 系统 **SHALL** 显式校验 `senderId` 与 `receiverId` 同租户（`userMapper.selectByIdAndTenantId(...)`）。
- **THE** 私信内容 **SHALL** 经过 `SensitiveWordService.getRiskLevel`，level ≥ 2 直接拒绝；level = 1 写入 `messages.ai_risk_level` 字段。
- **THE** 系统 **SHALL** 在 `rate-limit.overrides` 增加 `[POST /api/v1/messages]` 配额（如 30 条/分钟）。
- **THE** WebSocket 推送 payload 中的 `content` **SHALL** 限制 50 字符，避免广播超大消息（已实现）；DB 入库时按 `@Size` 上限截断/拒绝（需新增）。

### 保留性 (Preservation)
- 现有 sendToUser WebSocket 推送逻辑保留。
- "不能给自己发私信"防御保留。

### 建议修复方向
1. 新建 `SendMessageRequest` DTO；controller 改用 `@Valid @RequestBody`。
2. `MessageService#send` 加显式跨租户断言。
3. 内容过敏感词 + 长度上限。
4. 新增 `rate-limit.overrides` 配置。


---

## 漏洞 17：`AdminUserController#changeRole` / `batchSetStatus` 入参未校验，运营误操作或攻击者输入 `body.get("status")` 不存在时 NPE [High]

### 风险描述
`AdminUserController` 多处使用 `Map<String, ...>` 直接 parse：

```java
public R<Void> changeRole(@PathVariable Long id, @RequestBody Map<String, String> body) {
    String newRole = body.get("role");
    userService.changeRole(id, newRole);
    ...
}
```

```java
public R<Void> batchSetStatus(@RequestBody Map<String, Object> body) {
    @SuppressWarnings("unchecked")
    List<Long> ids = ((List<Number>) body.get("ids")).stream()...;
    int status = Integer.parseInt(body.get("status").toString());
    ...
}
```

问题：
1. **`body.get("ids") == null`** 会抛 NPE → 500（被全局兜底吃掉，但日志混乱、信息泄漏）；
2. **`status` 类型不安全**：传 `"abc"` 抛 NumberFormatException，传 `999` 也能写入 → 业务层无 enum 校验；
3. **`role` 校验在 service 层**：`userService.changeRole` 内只校验 `["USER", "TENANT_ADMIN"]` — OK，但**没有校验 SUPER_ADMIN**：如果攻击者通过其他越权点拿到 SUPER_ADMIN session 的 role 字段（漏洞 4 提到的运营误配可能间接产生），可以传 `role=SUPER_ADMIN` — service 层 `List.of("USER", "TENANT_ADMIN")` 校验会拦下，所以这点是安全的；
4. **`ids` 重复 ID 不去重**：批量封禁时同一 ID 多次进入循环，多次写审计、多次 kickout。
5. **batch 操作非事务**：循环中 `userService.banUser(id)` 各自一个事务，部分失败会留下"半改"状态。
6. **审计日志泄漏运营行为模式**：`detail` 字段写入 `"by admin " + StpUtil.getLoginIdAsLong()`，操作人 ID 已经在 `operatorId` 字段中，重复且可被篡改。

### 受影响位置
- `backend/src/main/java/com/campusforum/admin/controller/AdminUserController.java#changeRole, batchSetStatus`
- `backend/src/main/java/com/campusforum/admin/controller/AdminPostController.java#setStatus`（同样的 Map 模式）

### 触发条件 V(X)
- V1：X = "管理员 client 误发 `{}` 给 batchSetStatus" → NPE → 500 → 日志中 `Unhandled exception` 报错（GlobalExceptionHandler 是 try ... finally TenantContext.clear，OK）。
- V2：X = "重复 ID 列表" → 多次审计、多次 kickout 占用 Redis。
- V3：X = "中途 DB 故障" → 部分 ID 已封禁，部分未封禁，运营无感知。

### 影响
- C：低。
- I：中（半改状态、审计日志噪音）。
- A：中（NPE 暴露、批量操作失败但响应 200）。
- 跨租户：否。
- 未授权可达：否。

### 安全属性 (EARS)
- **THE** Admin 接口 **SHALL** 全面使用强类型 DTO + Bean Validation；禁止 `@RequestBody Map<...>`。
- **THE** 批量操作 **SHALL** 整体事务化（`@Transactional`），且 ID 集合去重 + 非空校验 + 上限 100（已有上限校验）。
- **THE** 审计日志 **SHALL** 仅记录字段语义：操作类型、目标 ID 列表、字段变更前后值；不要在 `detail` 文本中拼"by admin {id}"，因为 `operatorId` 已经存在。

### 保留性 (Preservation)
- 现有审计日志结构保持，仅清洗冗余文本。

### 建议修复方向
1. 新增 `BatchUpdateUserStatusRequest`、`ChangeRoleRequest` DTO；controller 接收强类型。
2. `userService.batchBanOrUnban(List<Long> ids, int status, Long operatorId)` 抽出来 `@Transactional`。
3. `auditLogService.log(...)` 调整写入字段，避免冗余文本。


---

## 漏洞 18：评论与帖子内容无 HTML/Markdown 净化，前端 `renderMentions` 直接返回 HTML 字符串，存在存储型 XSS 链路 [High]

### 风险描述
后端 `CreatePostRequest.content` 与 `CreateCommentRequest.content` 仅做长度校验，未经 OWASP HTML Sanitizer 处理；项目 `pom.xml` 引入了 `owasp-java-html-sanitizer` 依赖但**全代码库 grep 不到任何使用**。

前端：
- `frontend/src/utils/mention.ts#renderMentions` 直接拼接 `<a>` 标签字符串：
  ```ts
  return text.replace(MENTION_RE, (_match, name) => {
    return `<a href="/search?q=${encodeURIComponent('@' + name)}" class="mention-link">@${name}</a>`;
  });
  ```
  返回的字符串会被某处通过 `v-html` 渲染（仓库内 grep `v-html` 没找到，但 `MentionText.vue` 是为 mention 渲染存在的，需要审查它是否使用 `v-html`）。
- `Resources.vue` 的 `iframe srcdoc=markdownSrcdoc sandbox="allow-popups allow-popups-to-escape-sandbox"` 用于 markdown 预览 — `allow-popups-to-escape-sandbox` 让 iframe 内的 `window.open(...)` 跳出 sandbox，可能被恶意 markdown 利用。

合并起来的攻击链：
1. 攻击者发帖内容 `## 你好@admin <img src=x onerror=alert(1)>`；
2. 后端不净化原样存储；
3. 前端某处用 `v-html` 渲染 → XSS 触发；
4. 攻击者诱导管理员浏览 → 拿到管理员 token；
5. 配合漏洞 5（修改密码不踢下线）→ 长期控制。

CSP 设置（nginx）`script-src 'self'` 能挡住远程脚本，但**inline event handler `onerror=`** 在没有 `'unsafe-inline'` 也是 — 等等，CSP 的 `script-src 'self'` 实际**会拦截** inline event handler（chrome 保留 `'unsafe-hashes'` 才能精细控制）。但 `style-src 'self' 'unsafe-inline'` 配合 `<style onload=...>` 类的攻击向量还有空间。

### 受影响位置
- `backend/src/main/java/com/campusforum/post/dto/CreatePostRequest.java`
- `backend/src/main/java/com/campusforum/post/dto/CreateCommentRequest.java`
- `frontend/src/utils/mention.ts#renderMentions`
- `frontend/src/components/MentionText.vue`（需审计 `v-html` 用法）
- `frontend/src/pages/Resources.vue` markdown iframe sandbox 标志位

### 触发条件 V(X)
设 X = "发帖/评论内容包含恶意 HTML/JS 载荷，前端某条渲染路径调用 `v-html` 或 srcdoc 渲染"。

### 影响
- C：高（窃取管理员 token、私信内容）。
- I：高（仿冒发帖、批量删帖）。
- A：低。
- 跨租户：否。
- 未授权可达：否（需要发帖权限），但帖子可被全站用户阅读。

### 安全属性 (EARS)
- **THE** 帖子与评论内容 **SHALL** 在落库前由 `OwaspSanitizerService` 处理：移除 `<script>`、event handlers、`javascript:` URL、`data:` URL 等危险载荷；保留 `<a>`、`<img>`、`<pre>`、`<code>`、`<blockquote>`、Markdown 转 HTML 后允许的标签。
- **THE** `MentionParser`/`MentionText.vue` 渲染 mention 时 **SHALL** 使用 Vue 模板渲染（`<RouterLink>`）而非字符串拼接 + `v-html`。
- **THE** `Resources.vue` 的 markdown iframe **SHALL** 改为 `sandbox="allow-popups"` 而非 `allow-popups-to-escape-sandbox`；并且不再使用 srcdoc，改为通过 `URL.createObjectURL(new Blob([html], { type:'text/html' }))` 隔离同源。
- **THE** 系统 **SHALL** 增加 CSP `report-uri /api/v1/security/csp-report`，方便发现 XSS 尝试。

### 保留性 (Preservation)
- 用户能正常使用 Markdown 与 mention。
- 链接 / 图片在帖子中正常渲染。
- 现有 `frame-ancestors 'none'` / `script-src 'self'` 保留。

### 建议修复方向
1. 新增 `infra/sanitize/HtmlSanitizerService`，包装 OWASP Sanitizer 的 `Sanitizers.FORMATTING.and(LINKS).and(BLOCKS).and(IMAGES)`。
2. `PostService#create / updatePost` 与 `CommentService#create / updateComment` 在写入前调用净化器。
3. `MentionText.vue` 改用 `parseMentions` 返回 segments，再用 `<template v-for><RouterLink>` 渲染，禁止使用 `v-html`。
4. `Resources.vue` markdown iframe sandbox 收紧。
5. `nginx.conf` CSP 增加 `report-uri`，新增 `/api/v1/security/csp-report` 端点入审计。


---

## 漏洞 19：敏感词字典在租户停用时未清理，停用后再启用会带回旧字典 [Medium]

### 风险描述
`SensitiveWordService` 增删改查均按 `TenantContext.getTenantId()` 进行，写入 `tenant_id` 字段。但 `TenantService#toggleStatus` 把租户置为 status=0 时**没有触发 `ActiveTenantCache.evict(...)`**：

```java
public void toggleStatus(Long id) {
    Tenant tenant = tenantMapper.selectById(id);
    if (tenant == null) throw ...;
    tenant.setStatus(tenant.getStatus() == 1 ? 0 : 1);
    tenantMapper.updateById(tenant);
}
```

`ActiveTenantCache#init` 内 `idCache` 的 loader 用 `selectById(id).filter(t -> t.getStatus() == 1)`，TTL = 60s。在 60s 内 SUPER_ADMIN 停用某租户后：
- `MultiTenantResolver` 仍可能读到 cache 命中的活跃 tenant；
- 该租户的用户依然可登录（Sa-Token Session 中保存 tenantId，与 cache 解耦）；
- 实际上 status=0 的租户用户能正常使用所有功能直到 cache 过期。

这是个**优先级稍低**但典型的"配置变更不立即生效"问题。安全侧具体后果：
- 当被停用的租户内有"违法账号"、SUPER_ADMIN 想立刻拉黑时，60s 内对方仍在线、仍能继续作恶；
- 敏感词列表的更新也走类似缓存（`SensitiveWordService` 当前每次都 `listAll()`，无缓存，但未来加上 caffeine 后会有同样问题）。

### 受影响位置
- `backend/src/main/java/com/campusforum/tenant/service/TenantService.java#toggleStatus`
- `backend/src/main/java/com/campusforum/tenant/cache/ActiveTenantCache.java#evict`（提供了入口但未被调用）
- `backend/src/main/java/com/campusforum/sensitive/service/SensitiveWordService.java`（未来缓存化时同样需要 evict）

### 触发条件 V(X)
设 X = "SUPER_ADMIN 停用恶意租户 T，T 内的活跃用户继续操作"。

### 影响
- C：低。
- I：中（已被禁用的租户仍可写入数据）。
- A：低。
- 跨租户：是。

### 安全属性 (EARS)
- **WHEN** 租户状态变更 (`toggleStatus` / `update domain` 等)，**THE** 系统 **SHALL** 立即调用 `ActiveTenantCache.evict(id, code)`。
- **WHEN** 租户被停用，**THE** 系统 **SHALL** 调用 `StpUtil.kickout(tenantId="...")` 等价操作，按 tenantId 维度批量踢下线该租户全部用户。
- **WHEN** 敏感词字典更新，**THE** 系统 **SHALL** 立即清理对应租户的字典缓存。

### 保留性 (Preservation)
- 启用/停用功能 API 不变。
- Caffeine cache TTL 仍保留作为兜底。

### 建议修复方向
1. `TenantService#toggleStatus` 调用 `activeTenantCache.evict(id, tenant.getCode())`。
2. 引入 `UserService.kickoutByTenant(long tenantId)`：用 `userMapper` 查 active users，循环 `StpUtil.kickout(userId)`。
3. 敏感词模块加缓存 + 写入时 evict。


---

## 漏洞 20：`PostService#create` 拼接 quotePost 内容时未做 Markdown 转义，可注入 ">" 引文逃逸 [Medium]

### 风险描述
`PostService#create` 在用户引用历史帖子时构造引用 Markdown：

```java
content = "> **" + quotedName + "** 的原帖：\n> " +
        (quoted.getTitle() != null ? "**" + quoted.getTitle() + "**\n> " : "") +
        quoted.getContent().replace("\n", "\n> ") +
        "\n\n" + (content != null ? content : "");
```

漏洞条件：
- `quotedName` 来自 `userMapper.selectById(quoted.getAuthorId()).getNickname()`，nickname 在 `RegisterRequest` 校验仅 `@Size(max=64)`，**没有限制特殊字符**；
- 攻击者将 nickname 改为 `**" 的原帖：</quote><script>alert(1)</script><quote>"**`，然后被人引用；
- 同样 `quoted.getTitle()`、`quoted.getContent()` 也是用户输入，已经经过 SensitiveWordService 但**未经 Markdown 转义**；
- 攻击者构造内容 `# Title\n\n# 我是引用者本人新加的帖子` — 当被引用时，会让接收者看到的"引用块"末尾被截断，伪装成"引用者额外加的话"。

这属于 **Markdown 上下文逃逸**，不是直接 XSS，但配合漏洞 18（HTML 净化缺失）可放大影响。

### 受影响位置
- `backend/src/main/java/com/campusforum/post/service/PostService.java#create`（quotePostId 分支）
- `backend/src/main/java/com/campusforum/user/dto/RegisterRequest.java#nickname`（缺少字符白名单）
- `backend/src/main/java/com/campusforum/user/dto/UpdateProfileRequest.java#nickname`（同上）

### 触发条件 V(X)
设 X = "攻击者注册账号，nickname 含 Markdown 控制字符 + 攻击载荷 + 任意用户引用其帖子"。

### 影响
- C：低。
- I：中（伪装他人发言、引用块截断）。
- A：低。

### 安全属性 (EARS)
- **THE** 用户 nickname **SHALL** 仅允许中英文 + 数字 + 下划线 + 连字符 + 空格，长度 1-32（同 `TAG_PATTERN` 风格）。
- **THE** 引用上下文构造 **SHALL** 在拼接前对 `quotedName`/`title`/`content` 做 Markdown 转义（`> ` 行首符号、`**`、`#` 等）。
- **THE** 系统 **SHALL** 在 nickname 校验后再做相同性检查，避免与已有用户大小写碰撞。

### 保留性 (Preservation)
- 引用块视觉样式不变。
- 现有用户的 nickname 不被强制改名，仅新注册/改名时校验。

### 建议修复方向
1. `RegisterRequest.nickname` 与 `UpdateProfileRequest.nickname` 增加 `@Pattern(regexp = "^[\\w\\u4e00-\\u9fa5\\- ]{1,32}$")`。
2. `PostService.create` 提取 `markdownEscape(...)` 工具函数对 quotedName / title / content 转义。


---

## 漏洞 21：`PostService#viewPost` 浏览计数被任意已登录用户递增，可被刷高显眼度 [Medium]

### 风险描述
`viewPost` 仅在管理员角色下不计数，普通用户每次进入帖子详情都 `+1`：

```java
if (currentUserId != null) {
    String role = (String) StpUtil.getSession().get("role");
    if (!"TENANT_ADMIN".equals(role) && !"SUPER_ADMIN".equals(role)) {
        if (postMapper.incrementViewCount(id) > 0) { ... }
    }
}
```

没有"同一用户 N 分钟内只算 1 次"的去重，攻击者写脚本对自己的帖子轮询 `/api/v1/posts/<id>` 即可在分钟级把 view_count 刷到任意大，影响 trending 排序。

### 受影响位置
- `backend/src/main/java/com/campusforum/post/service/PostService.java#viewPost`
- `backend/src/main/java/com/campusforum/post/mapper/PostMapper.java#incrementViewCount`

### 触发条件 V(X)
设 X = "已登录用户对自己的帖子发起 N 次 GET /api/v1/posts/{id}"。

### 影响
- C：无。
- I：中（trending 排序被污染，热搜榜可控）。
- A：中（DB UPDATE 风暴）。
- 跨租户：否。

### 安全属性 (EARS)
- **THE** 同一 (postId, userId) 的浏览计数 **SHALL** 在 30 分钟内只递增一次，通过 Redis SETNX `post_view:{postId}:{userId}` + 30 分钟 TTL 实现。
- **THE** 匿名访问者按 (postId, ip) 同样去重。
- **THE** 自己浏览自己的帖子 **SHALL** 不计数。
- **THE** 系统 **SHALL** 增加每日单帖最大新增 view 上限（防 DoS）。

### 保留性 (Preservation)
- 真实用户的浏览仍能正确计数。
- trending / essence 排序逻辑不变，仅输入数据更可信。

### 建议修复方向
1. 引入 `PostViewDeduper`（基于 Redis SETNX）。
2. `viewPost` 在自己浏览自己的帖子时跳过 increment。
3. 限流 `[GET /api/v1/posts/{id}]` 配合漏洞 7 的"路由模板"限流粒度调整。


---

## 漏洞 22：`MeiliSearchClient#search` 当 `tenantId == null` 时返回**全租户聚合** [Medium]

### 风险描述
`MeiliSearchClient#search(String index, String query, int limit, Long tenantId)` 在 `tenantId != null` 时追加 `filter: "tenantId = X"`，否则**不加任何 filter**：

```java
if (tenantId != null) {
    body.put("filter", "tenantId = " + tenantId);
}
```

调用方 `SearchService#searchPostsViaMeiliSearch` 通过 `TenantContext.getTenantId()` 拿 tenantId 再传入。`TenantContext` 由 `TenantResolutionFilter` 设置，理论上 `/api/v1/**` 路径都不会为 null —— 但**异步路径** / **WebSocket 触发的搜索回调** / **定时任务** 中 `TenantContext` 可能为 null，此时调用 `meiliSearchClient.search(index, query, limit)` 旧重载（无 tenantId 参数）就会跨租户检索。

更严重的是：`SearchService#searchPostsViaMeiliSearch` 第二行 `Long tid = com.campusforum.tenant.TenantContext.getTenantId(); meiliSearchClient.search(...)` —— 如果 `tid == null`，**没有抛错而是跨租户查全部**。

### 受影响位置
- `backend/src/main/java/com/campusforum/search/service/MeiliSearchClient.java#search`（tenantId == null 静默放行）
- `backend/src/main/java/com/campusforum/search/service/SearchService.java#searchPostsViaMeiliSearch`（不抛错）

### 触发条件 V(X)
设 X = "异步线程或调度任务调用 SearchService.search 时未显式设置 TenantContext"。

### 影响
- C：高（一旦 X 满足，跨租户搜索数据返回）。
- I：低。
- A：低。
- 跨租户：是。

### 安全属性 (EARS)
- **WHEN** `tenantId == null`，**THE** `MeiliSearchClient.search` **SHALL** 抛 `IllegalStateException` 或返回空列表，**绝不**省略 filter。
- **THE** 异步搜索路径 **SHALL** 显式传入 `tenantId`，禁止依赖 ThreadLocal。
- **THE** 系统 **SHALL** 在启动期添加单元测试覆盖 "TenantContext = null 时 search 返回空 / 抛错"。

### 保留性 (Preservation)
- 正常请求路径行为不变。

### 建议修复方向
1. `MeiliSearchClient#search`：`tenantId == null` 时 `log.error` + 返回空列表。
2. `SearchService.searchPostsViaMeiliSearch` 在 tid null 时抛 `IllegalStateException`。
3. 移除 `MeiliSearchClient#search(String index, String query, int limit)` 旧重载，强制调用方显式传 tenantId。


---

## 漏洞 23：`SafeHttpClient` 仅校验 connect 阶段的 host，DNS 重绑定窗口仍存在但未限制 redirect [Medium]

### 风险描述
`SafeHttpClient` 已经在 `prepareConnection` 阶段二次解析 host 校验私网（应对 DNS 重绑定）。但仍有两个残留风险：
1. **HTTP 重定向**：`SimpleClientHttpRequestFactory` 默认 `setFollowRedirects(true)`（`HttpURLConnection.getFollowRedirects()` JVM 全局默认 true）。若上游返回 302 指向 `http://169.254.169.254/`，**新连接的 `prepareConnection`** 不会被 Spring 触发 — 重定向是 `HttpURLConnection` 内部完成的，那一跳没有经过 SafeHttpClient 的 host 校验。
2. **TLS 校验**：`SimpleClientHttpRequestFactory` 默认信任 JVM truststore，但不强制 hostname 校验失败时拒绝 — 实际默认行为是开启的，但缺少显式声明，未来如果换 RestTemplate 实现可能丢失。

### 受影响位置
- `backend/src/main/java/com/campusforum/infra/security/SafeHttpClient.java`

### 触发条件 V(X)
设 X = "AI baseUrl 是公网域名，但服务端返回 302 redirect 到 `http://169.254.169.254/latest/meta-data/`"。

### 影响
- C：高（云元数据接口可拿临时 AK/SK）。
- I：低。
- A：低。
- 跨租户：是（攻击载体在租户 AI 配置）。

### 安全属性 (EARS)
- **THE** SafeHttpClient **SHALL** 显式 `setFollowRedirects(false)`；上游 302/301 直接返回业务层处理，禁止 `HttpURLConnection` 自动跟随。
- **WHEN** 业务确实需要跟随 redirect，**THE** 系统 **SHALL** 手动循环：每次拿到 redirect URL 后再次走 `PrivateNetworkValidator.requirePublic` + `SafeHttpClient` 重新发起。
- **THE** 系统 **SHALL** 显式启用 hostname verifier。

### 保留性 (Preservation)
- AI 调用不依赖 redirect（DeepSeek / OpenAI 兼容接口直连）。

### 建议修复方向
1. `SimpleClientHttpRequestFactory` 改为子类 `setFollowRedirects(false)`。
2. 增加 `RedirectFollowingHttpClient` 仅在显式启用时生效，循环上限 3 次，每跳校验 host。


---

## 漏洞 24：`MimeTypeValidator` 仅在已声明扩展名映射时校验，未声明的扩展名（如 `.txt`/`.json`）静默放行 [Medium]

### 风险描述
`MimeTypeValidator#validate` 的注释明确写着"所有未在 `EXT_TO_MIMES` 中显式声明的扩展名跳过 MIME 校验"。但 `application.yml` 默认 allowed-extensions 列表里包含 `md, markdown` —— Tika 检测会尝试匹配 `text/plain` 或 `text/markdown`；其他**白名单内但未在 EXT_TO_MIMES 中的扩展名（未来可能被加上 `txt/csv/json`）**会跳过 MIME 校验，攻击者把 `.html` 改成 `.csv` 上传仍能绕过。

更直接的问题是：`MimeTypeValidator` 同时需要 `originalFilename` 与扩展名匹配，但 `originalFilename` 是用户控制的字符串（包括 `;` `\u0000` 等），且 `Tika` 的 `RESOURCE_NAME_KEY` 元数据**会被 detector 用作 detection hint** — 如果用户传入恶意 originalFilename 让 Tika 误判 mime，仍可绕过白名单。

### 受影响位置
- `backend/src/main/java/com/campusforum/infra/security/MimeTypeValidator.java#validate`

### 触发条件 V(X)
- V1：未来扩展名白名单加入 `.txt` 但未补 EXT_TO_MIMES → 静默放行。
- V2：用户传 originalFilename = `xxx.png` 文件实际是 zip → Tika 在有 RESOURCE_NAME_KEY hint 时倾向于 image/png 误判。

### 影响
- C：低。
- I：中。
- A：低。

### 安全属性 (EARS)
- **THE** `MimeTypeValidator` **SHALL** 对**白名单内未注册 EXT_TO_MIMES** 的扩展名仍执行 detection 并要求 detected mime ∈ "已知文本/图片/Office/PDF/zip" 集合，不是直接放行。
- **THE** Tika 调用 **SHALL** 不传 RESOURCE_NAME_KEY 元数据，避免被文件名 hint 影响判断（依赖 magic bytes）。
- **THE** 上传链路 **SHALL** 同时校验"原始扩展名 + Tika detected mime + 黑名单"三者，黑名单包含 `application/x-php`、`application/x-msdownload`、`application/x-msdos-program` 等。

### 保留性 (Preservation)
- 现有图片/PDF/Office 上传链路不变。

### 建议修复方向
1. `EXT_TO_MIMES` 改为完整白名单，未注册的扩展名直接拒绝（替代当前"静默放行"策略）。
2. 移除 `meta.set(TikaCoreProperties.RESOURCE_NAME_KEY, ...)`。
3. 增加 `BLOCKED_MIMES` 黑名单常量，detected mime 落入即拒绝。


---

## 漏洞 25：`TenantBindingCheckInterceptor` 仅校验 `X-Tenant-Id` 头，缺少对子域名访问与 session tenantId 偏离的校验 [Medium]

### 风险描述
`TenantBindingCheckInterceptor` 仅在客户端**显式发送**了 `X-Tenant-Id` 头时才比对：

```java
String headerVal = req.getHeader("X-Tenant-Id");
if (headerVal == null) {
    return true;
}
```

这意味着：
- 攻击者**不发该头**就直接放行；
- multi 模式下若攻击者通过修改 hosts 文件将 `evil-tenant.campusforum.com` 指向受害租户域名（中间人 / 内网 DNS 劫持），`TenantResolutionFilter` 会通过子域名解析得到 evil-tenant，把 `TenantContext` 设为 evil 租户，但 `TenantBindingCheckInterceptor` **不会**校验子域名 vs Sa-Token Session tenantId 的一致性。

`MultiTenantResolver` 优先从 Sa-Token Session 取 tenantId，所以**已认证请求**会用 session 中的 tenantId 而非子域名 — 这点是好的，session 有权威性。但**未认证请求**（如 `/api/v1/auth/login`）的 tenantId 完全由子域名决定，攻击者构造 `evil.campusforum.com/api/v1/auth/login` 即可对 evil 租户做 brute force / 用户枚举。

更严重的攻击模式：
- 攻击者已登录到租户 A，session.tenantId = 1；
- 通过修改本机 hosts 让 `tenant-b.campusforum.com → 受害服务器`；
- 浏览器访问 `tenant-b.campusforum.com/api/v1/posts` —— 由于已登录，`MultiTenantResolver` 优先用 session 的 tenant=1，子域名 tenant-b 被忽略；
- **但前端**会显示"tenant-b 的页面"导致用户混淆 → 钓鱼。

### 受影响位置
- `backend/src/main/java/com/campusforum/tenant/filter/TenantResolutionFilter.java`
- `backend/src/main/java/com/campusforum/tenant/interceptor/TenantBindingCheckInterceptor.java`
- `backend/src/main/java/com/campusforum/tenant/resolver/MultiTenantResolver.java`

### 触发条件 V(X)
- V1（multi 模式）：X = "用户已登录 + 访问错误子域名"。会让前端显示混乱（视觉钓鱼）。
- V2（multi 模式）：X = "未登录 + 子域名解析"。攻击者可以借任意子域名暴力破解任意租户。

### 影响
- C：低。
- I：中（视觉钓鱼）。
- A：低。
- 跨租户：是。

### 安全属性 (EARS)
- **WHEN** `MultiTenantResolver` 同时拿到 Sa-Token Session tenantId 与子域名 tenantId 但不一致，**THE** 系统 **SHALL** 拒绝请求并返回 `TENANT_VIOLATION`，记录审计日志。
- **WHEN** 未认证路径（`/api/v1/auth/**`）通过子域名解析租户，**THE** 系统 **SHALL** 仅接受预先注册过的子域名前缀（`tenants.code` 字段），并在审计日志记录"子域名命中租户"事件。
- **THE** 子域名校验 **SHALL** 同时支持子域名小写规范化（`Foo.campusforum.com == foo.campusforum.com`）。

### 保留性 (Preservation)
- standalone 模式不受影响。
- 已登录请求行为不变。

### 建议修复方向
1. `MultiTenantResolver` 同时检测 session 与子域名，不一致直接抛 `TenantNotResolvedException(REASON.TENANT_MISMATCH)`。
2. `TenantBindingCheckInterceptor` 在已登录路径上也校验"前端 URL 子域名 vs Session tenantId" — 通过解析 `Host` 头实现。
3. 子域名解析使用 punycode 规范化，防 IDN 同形钓鱼。


---

## 漏洞 26：`AuditLogService.log` 直接 `httpRequest` 注入 `prototype` Bean，异步路径会拿到错误的 request [Medium]

### 风险描述
`AuditLogService` 通过构造函数注入 `HttpServletRequest`：

```java
private final HttpServletRequest request;
```

Spring 会注入一个 **request-scoped proxy**，调用 `getClientIp()` 时再绑定到当前线程的 request 对象。问题是：
- 若 `auditLogService.log(...)` 在 `@Async` 异步线程调用（例如 `TenantService#asyncReencryptApiKey` 模式扩展到审计），此时**没有当前 request**，proxy 会抛 `IllegalStateException: No thread-bound request found`，被全局 catch 兜底；
- 若被 catch 静默吞掉，审计日志只能写入 `operatorId=...` 但 `ipAddress=null` —— 出现"操作有日志但 IP 为空"，违反审计完整性。

更严重的隐患：审计日志是**逆向调查证据链的核心**，IP 不全意味着事后无法溯源。

### 受影响位置
- `backend/src/main/java/com/campusforum/admin/service/AuditLogService.java`

### 触发条件 V(X)
设 X = "未来某次重构把审计日志放到 @Async 队列异步落库（避免阻塞业务）"。

### 影响
- C：低。
- I：中（审计完整性）。
- A：低。

### 安全属性 (EARS)
- **THE** 审计日志写入 **SHALL** 在调用方**当前 request 上下文**中完成 IP 解析，再传递给 `AuditLogService.log(...)`。
- **THE** `AuditLogService.log(...)` 接口 **SHALL** 接受 `String clientIp` 参数，不再依赖 `HttpServletRequest` 注入。

### 保留性 (Preservation)
- 现有同步审计日志路径不变。

### 建议修复方向
1. 重构 `AuditLogService.log(...)` 增加 `String clientIp` 参数；调用方在 controller 层先解析 IP 再传入。
2. 提供 `AuditContext` 工具类，封装"当前操作者 ID + IP + UA + tenantId"。
3. 异步路径用 TaskDecorator 把 ServletRequestAttributes 复制到子线程（更稳妥的兜底）。


---

## 漏洞 27：`SensitiveWordService` 仅用 `String#contains` 匹配，无法处理同形字 / Unicode 规范化 / 零宽字符 [Medium]

### 风险描述
`getRiskLevel`：

```java
for (SensitiveWord sw : words) {
    if (content.contains(sw.getWord())) {
        maxLevel = Math.max(maxLevel, sw.getLevel());
    }
}
```

攻击者绕过手段：
- 在敏感词中间插入零宽空格（U+200B）/ 零宽连字（U+200D）；
- 用同形字（西里尔字母 `а` 替换拉丁 `a`）；
- 用全角字符替换半角；
- 在每个字之间加 `*`、空格、emoji。

### 受影响位置
- `backend/src/main/java/com/campusforum/sensitive/service/SensitiveWordService.java#getRiskLevel`

### 触发条件 V(X)
设 X = "用户发帖/评论/私信使用上述 obfuscation 手段绕过敏感词"。

### 影响
- C：无。
- I：中（敏感词审核形同虚设）。
- A：低。

### 安全属性 (EARS)
- **THE** 敏感词匹配前 **SHALL** 对内容做 NFKC Unicode 规范化、移除零宽字符、全角转半角、繁简转换、大小写归一。
- **THE** 敏感词字典 **SHALL** 支持正则模式（管理员标记 `is_regex` 字段）以应对常见混淆。
- **THE** 系统 **SHALL** 提供 `SensitiveWordTester` 单元测试覆盖混淆样本。

### 保留性 (Preservation)
- 现有敏感词字典与三级 level 划分保留。

### 建议修复方向
1. 新增 `TextNormalizer.normalize(String content)`，做 NFKC + zero-width strip + 全角转半角。
2. `SensitiveWordService.getRiskLevel` 内部对 `content` 与字典词都先 normalize 再 contains。
3. 性能：用 AC 自动机（Ahaha-Corasick）替代多次 contains，避免 O(N×W)。


---

## 漏洞 28：`GlobalExceptionHandler#handleException` 兜底返回 INTERNAL_ERROR 但不脱敏堆栈 [Low]

### 风险描述
`handleException` 仅 `log.error("Unhandled exception", e)` + 返回 `R.fail(INTERNAL_ERROR)`，本身脱敏到位（不向客户端暴露堆栈）。但：
- 同时 `handleIllegalState` 在异常 message 包含 `"TenantContext is null"` 时返回 `SERVICE_UNAVAILABLE`，这是基于字符串匹配的脆弱兜底 — 未来重构时容易漏；
- log 完整堆栈写入 server.log，攻击者若能拿到日志（漏洞 1/3 的衍生）就能反推内部结构。

### 受影响位置
- `backend/src/main/java/com/campusforum/common/GlobalExceptionHandler.java#handleException, handleIllegalState`

### 触发条件 V(X)
设 X = "运维日志被二次泄漏（如 ELK 公网暴露）"。

### 影响
- C：中（取决于日志暴露面）。
- I：低。
- A：低。

### 安全属性 (EARS)
- **THE** 系统 **SHALL** 区分"业务可见错误"与"内部错误"两类日志级别。
- **THE** `IllegalStateException` 兜底 **SHALL** 改为基于明确异常类型分支，而非字符串匹配。
- **THE** 日志记录 **SHALL** 在 ELK / SkyWalking 端做敏感字段脱敏（不能依赖应用层）。

### 保留性 (Preservation)
- 5xx 响应 contract 不变。

### 建议修复方向
1. 新增专用异常 `TenantContextMissingException`，替换字符串匹配。
2. 文档化"日志中可能出现的敏感信息"，部署 ELK 时配置 grok 脱敏。


---

## 漏洞 29：`TenantHandshakeInterceptor#extractQueryParam` 不解码 URL，特殊字符 ticket 解析错误 [Low]

### 风险描述
```java
for (String kv : query.split("&")) {
    if (kv.startsWith(prefix)) {
        String value = kv.substring(prefix.length());
        if (!value.isEmpty()) return value;
    }
}
```

未做 `URLDecoder.decode(...)`：当 ticket 中包含 `+`、`/`、`=`（base64 padding）这些特殊字符在前端 `encodeURIComponent` 后变成 `%2B`、`%2F`、`%3D`，后端拿到的是百分号编码字符串，然后传给 `signedUrlService.verifyAny(ticket, ...)` —— `verifyAny` 内部 `Base64.getUrlDecoder().decode(parts[1])` 不接受百分号编码 → 校验失败 → 401。

### 受影响位置
- `backend/src/main/java/com/campusforum/tenant/websocket/TenantHandshakeInterceptor.java#extractQueryParam`

### 触发条件 V(X)
设 X = "ticket 字符串中含 `+`、`/`、`=`，前端通过 `encodeURIComponent(ticket)` 编码"。

### 影响
- C：低。
- I：低。
- A：中（影响合法用户握手成功率，看似随机的 401）。

### 安全属性 (EARS)
- **THE** WebSocket 握手参数解析 **SHALL** 使用 `URLDecoder.decode(value, UTF_8)` 还原。
- **THE** ticket 颁发 **SHALL** 在 token 字符串中避免 `+`/`/` 字符（已使用 `Base64.getUrlEncoder()` URL-safe 编码，但 `=` padding 仍存在）。

### 建议修复方向
1. `extractQueryParam` 内部 `URLDecoder.decode(value, StandardCharsets.UTF_8)`。
2. SignedUrlService 颁发 token 时 `withoutPadding()`（已经做了，但 verify 端 `Base64.getUrlDecoder()` 默认接受可选 padding）。


---

## 漏洞 30：前端 `localStorage` 持久化 token，CSP 防 XSS 但不防 XSS 后续 token 偷取 [Low]

### 风险描述
`frontend/src/api/request.ts` 把 token 放在 `localStorage`，配合漏洞 18（XSS 链路）一旦在某处出现 XSS，攻击者通过 `localStorage.getItem('token')` 就能盗取。

最佳实践是把 token 放在 `httpOnly + Secure + SameSite=Lax/Strict` 的 cookie 中，但 Sa-Token 当前架构是 `Authorization` header，迁移到 cookie 会涉及 CSRF 防护改造（CSRF token / double-submit）。

### 受影响位置
- `frontend/src/api/request.ts#interceptors.request.use`
- `frontend/src/stores/auth.ts`

### 触发条件 V(X)
设 X = "前端某条路径出现 XSS"。

### 影响
- C：高（一旦 X 满足）。
- I：低。
- A：低。

### 安全属性 (EARS)
- **THE** 系统 **SHOULD**（建议性）将 token 迁移到 `httpOnly Secure SameSite=Strict` cookie。
- **WHEN** XSS 防护（漏洞 18）完成后，**THE** 系统 **SHALL** 在登录响应中同时设置 cookie 与 header，前端逐步切换到 cookie 模式。
- **THE** 同时 **SHALL** 增加 CSRF token 双提交校验（与现有 SaTokenConfig 集成）。

### 保留性 (Preservation)
- 兼容期内 header + cookie 双模式工作。

### 建议修复方向
1. 长期路线：登录接口在 `Set-Cookie: Authorization=<token>; HttpOnly; Secure; SameSite=Strict; Path=/`；前端 `withCredentials: true`；CORS 改 `allowCredentials: true` 但同时严格收窄 origin。
2. 短期：仅在漏洞 18 解决后再做迁移；当前优先把 XSS 链路堵死。


---

## 漏洞 31：`R.traceId` 来自 `UUID.randomUUID().toString().substring(0, 8)` 每次都是新的，无法跨请求关联 [Low]

### 风险描述
`R<T>` 的 `traceId` 在每次构造时新生成，不跟 SLF4J MDC / Spring Cloud Sleuth / 真实 trace context 关联。攻击者发起请求拿到的 `traceId` 与服务器日志中的 `traceId` 没有联系，运维需要排查时**无法定位日志行**。这本身不是漏洞，但**事故响应能力**降低 = 安全运营弱点。

### 受影响位置
- `backend/src/main/java/com/campusforum/common/R.java`

### 安全属性 (EARS)
- **THE** `R.traceId` **SHALL** 来自 MDC `traceId`（`HttpFilter` 在请求入口生成 + 写入 MDC + 响应头）。
- **THE** 所有日志格式 **SHALL** 包含 `%X{traceId}`。

### 建议修复方向
1. 增加 `MdcTraceIdFilter`：请求入口 set MDC `traceId`、`tenantId`、`userId`，响应头 `X-Trace-Id` 返回。
2. `R` 构造时读取 MDC 中已有 traceId，无则新生成。
3. logback 配置 `[%X{traceId} %X{tenantId} %X{userId}]` pattern。


---

## 漏洞 32：缺少 `crypto_decrypt_legacy_total` / `ssrf_blocked_total` / `mime_mismatch_total` 等监控指标 [Info]

### 风险描述
`deploy/SECURITY.md §8.5` 已经规划了若干安全相关监控指标，但实际代码中未发现 Micrometer 或类似实现。监控缺失意味着：
- 加固生效情况无法度量；
- 真实攻击痕迹无法及时告警。

### 受影响位置
- 全代码库无 `Counter` / `MeterRegistry` 注入。
- `pom.xml` 已引入 `spring-boot-starter-actuator`，但暴露面仅 `health`。

### 触发条件 V(X)
事后型（事故发生后才发现没有数据可查）。

### 安全属性 (EARS)
- **THE** 系统 **SHALL** 在以下事件埋点 Micrometer Counter：
  - `crypto_decrypt_legacy_total{tenant_id}`
  - `crypto_decrypt_failed_total`
  - `ssrf_blocked_total{stage="validator"|"connect"}`
  - `mime_mismatch_total{ext, detected}`
  - `login_lockout_503_total`
  - `ws_legacy_token_used_total`
  - `tenant_violation_total{reason}`
  - `rate_limit_429_total{path}`
- **THE** actuator `prometheus` endpoint **SHALL** 在内网（`security.trusted-proxies` 命中）暴露，外网拒绝。

### 建议修复方向
1. 引入 `micrometer-registry-prometheus` 依赖。
2. 在关键安全组件中注入 `MeterRegistry`，加 Counter。
3. nginx 增加 `/actuator/prometheus` location 仅允许内网 IP 段。


---

## 修复优先级路线图

### 第一阶段（一周内必须完成 — Critical）
按风险与修复成本排序：

1. **漏洞 3（signed-url-secret 弱默认值）** — 修改启动校验逻辑，prod 强制阻断；删除 yml 中默认值。代价：1 PR。
2. **漏洞 2（Knife4j / api-docs 暴露）** — `springdoc.*.enabled` ENV 控制 + nginx location 屏蔽 + `TenantResolutionFilter` 限本机。代价：1 PR。
3. **漏洞 1（CryptoUtils 硬编码密钥）** — 类降级 package-private + 移到 legacy 子包 + 删除 encrypt() + decrypt 失败抛异常。代价：1 PR + 等待历史数据迁移完成后清理。
4. **漏洞 4（SA_TOKEN_JWT_SECRET_KEY 死配置）** — 文档与配置清理；增加 Redis 凭证强度校验。代价：1 PR。

### 第二阶段（两周内 — High）
5. **漏洞 6（MinIO available 截断）** — StorageService 接口扩展 size 参数，三个实现同步修复，加 statObject 回查。代价：2 PR + 集成测试。
6. **漏洞 5（密码变更未踢下线）** — UserService 在 changePassword/resetPassword 调 logoutByLoginId。代价：1 PR + 单测。
7. **漏洞 7（限流 key 不归一）** — 改用 BEST_MATCHING_PATTERN_ATTRIBUTE。代价：1 PR + 集成测试。
8. **漏洞 8（WebSocket legacy token 默认开启）** — 增加强制日期 + 指标埋点 + 文档默认推荐 enforced。代价：1 PR。
9. **漏洞 9（搜索泄漏邮箱）** — 限制只按 nickname LIKE。代价：0.5 PR。
10. **漏洞 10（rate-limit exclude 默认值不当）** — 默认值修正 + 启动校验。代价：0.5 PR。
11. **漏洞 11（邮箱码 fail-open）** — service 层 fail-closed + 常量时间比较 + IP 维度。代价：1 PR。
12. **漏洞 12（OpenAI 全局 key Bean）** — service 解 Bean 化 + 失败显式审计。代价：1 PR。
13. **漏洞 13（导出权限粗 + PII 全字段）** — 拆权限 + 字段脱敏 + 审计 + 配额。代价：1.5 PR。
14. **漏洞 14（dashboard 计数 + ignore-tables 巡检）** — 启动校验 + DashboardVO 增 tenantId。代价：1 PR。
15. **漏洞 15（profile asset URL 错误）** — 引入签名代理接口 + 默认白名单收紧。代价：1 PR。
16. **漏洞 16（私信 Map 接收）** — DTO 化 + 跨租户校验 + 敏感词 + 限流。代价：1 PR。
17. **漏洞 17（admin Map 接收）** — DTO 化 + @Transactional 批量。代价：1 PR。
18. **漏洞 18（HTML 净化与 mention v-html）** — 引入 OWASP Sanitizer + 前端 MentionText 重构。代价：2 PR。

### 第三阶段（一月内 — Medium）
19-27 全部纳入计划，按依赖关系串行：
- 19/22/25 是租户隔离纵深；
- 20/24/27 是输入净化与匹配能力升级；
- 21 是反作弊；
- 23 是 SSRF 纵深；
- 26 是审计完整性。

### 第四阶段（90 天 — Low + Info）
28-32：错误处理、URL decoding、token 持久化迁移、监控埋点。监控埋点应放第二阶段同步完成（虽然标 Info），因为能为后续告警提供观测基础。

### 路线图依赖关系
```
Critical 块 ──┐
              ├─→ 第二阶段 (DTO + 限流 + XSS 净化)
监控埋点 ─────┘                ↓
                          第三阶段 (隔离纵深)
                                ↓
                          第四阶段 (cookie 迁移)
```

---

## 全局 Preservation 总结

无论修复哪一条，都不能破坏以下既有功能：
1. 已认证用户在 `application-dev.yml` 启动后能正常登录、发帖、上传、AI 对话。
2. multi 模式下子域名识别租户 + Sa-Token Session 优先策略不变。
3. 50MB 上传上限、签名 URL 60s TTL、Sa-Token 7d 总时长 + 4h 闲置过期保持。
4. 现有 4 类导出（CSV/XLSX）能力保留，仅做权限/字段/配额调整。
5. 现有限流 fail-closed 敏感路径列表不缩小。
6. CORS allowCredentials=false、CSP frame-ancestors='none' 等加固保持。
7. 现有审计日志结构兼容（仅扩展 + 新事件类型）。
8. v2 AES-GCM 加密链路不被新代码绕过；v1 ECB 仅供历史数据迁移读。

---

## 验收策略

每条漏洞的"修复后必须满足的安全属性"作为 design 阶段的**输入约束**；后续 tasks 阶段会拆分为：
1. **fix-checking**：用 SpringBootTest / vitest 复现该漏洞，确认修复前能成立、修复后被阻断；
2. **preservation-checking**：跑完整后端测试套件 + 前端 vitest，确保正常路径不退化；
3. **regression-checking**：选取仓库内已有的 happy-path 集成测试（如 `ResourceUploadIT`、`AuthLoginIT`）作为回归样本。

---

## 后续阶段衔接

本 `bugfix.md` 完成后，进入：
- **design.md**：每条漏洞给出具体的代码改造方案、新增类与接口签名、配置项变更、前后兼容策略。重点设计：
  - `CryptoUtils` 的 package 收缩与调用方限制；
  - `SecurityStartupValidator` 的 prod-only 严格分支；
  - `StorageService` 接口扩展 size 参数 + 三实现同步；
  - `RateLimitInterceptor` 路由模板拼接；
  - `OwaspSanitizerService` 的 policy 选型；
  - `MicrometerCounter` 全量埋点；
  - WebSocket ticket 强制启用日期 cut-over；
  - 数据库迁移脚本（如 nickname pattern 校验、敏感词字段、audit_log 索引补充）。
- **tasks.md**：把 design.md 拆成可独立提 PR 的最小任务，每个任务带验收标准与依赖关系。

---

## 文档变更记录

| 日期 | 版本 | 修改人 | 摘要 |
|------|------|--------|------|
| 2026-05-22 | v1.0 | 资深安全工程师（Kiro） | 完成项目第二轮安全审计，记录 32 条漏洞 |

