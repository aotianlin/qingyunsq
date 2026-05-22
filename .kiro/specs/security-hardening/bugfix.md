# 安全加固缺陷修复需求文档

## Introduction

本文档记录了通过安全审计在校园论坛项目中发现的 32 项安全缺陷。审计覆盖了后端（Spring Boot 3.3.0 + Sa-Token + MyBatis-Plus）、前端（Vue 3 + TypeScript）以及部署配置（Docker + Nginx）。缺陷涵盖凭证管理（硬编码加密密钥、明文存储重置令牌）、注入攻击（LIKE 通配符注入、跨租户搜索索引污染）、SSRF（DNS 重绑定窗口、AI baseUrl 二次校验缺失）、XSS（iframe 缺 sandbox、URL 字段无校验）、权限提升（角色变更未踢出、TENANT_ADMIN 反向降级 SUPER_ADMIN）、DoS（multipart 大小未限、批量操作无上限）、信息泄露（资源 ID 枚举、UserVO 暴露 email）以及部署加固（缺少安全头、token 走 query string）等多个类别。修复按风险等级与依赖关系分组实施。

## Bug Analysis

### Current Behavior (Defect)

**严重 — 凭证与机密管理**

1.1 WHEN 系统在 `CryptoUtils` 中加解密租户级 AI API Key THEN 系统使用硬编码 16 字节密钥 `"CampusForum@1234"` 与 AES/ECB/PKCS5Padding 模式，且解密失败时回退返回原文，导致密钥从源代码即可获取、ECB 暴露相同明文模式、解密分支可被攻击者用于探测明文残留

1.2 WHEN 用户提交忘记密码请求时 THEN 系统将 32 字节随机重置令牌以明文形式存入 `users.reset_token` 字段，数据库泄漏即可重置任意账号

1.3 WHEN 前端 WebSocket 客户端连接 `/ws/notify` 时 THEN 系统通过 URL query string 传递 Sa-Token 主令牌，导致令牌被记录到 nginx access log、浏览器历史与 Referer 头中

**严重 — 注入与跨租户隔离**

1.4 WHEN 用户创建带标签的帖子时 THEN `UserMapper.selectUserIdsByTagSubscription` 使用 `LIKE CONCAT('%"', #{tag}, '"%')` 查询订阅者，未对 `%` 与 `_` 通配符转义，攻击者传入 `tag = "%"` 可命中全租户用户实现群发垃圾通知与盲注探测

1.5 WHEN 系统调用 `MeiliSearchClient.indexDocument` 索引帖子时 THEN 写入文档不包含 `tenantId` 字段，且 `MeiliSearchClient.search` 不附加 tenant filter，导致 multi-tenant 模式下 A 校用户能搜出 B 校的帖子标题与摘要

1.6 WHEN `SearchService.searchPosts` 走 MySQL FULLTEXT 兜底分支时 THEN 调用 `qw.apply("MATCH... AGAINST({0} ...)", keyword)` 使用了**未经过滤的原始 keyword**而非已 sanitize 的 `safeKeyword`，与代码注释承诺不一致



**严重 — 权限与会话管理**

1.7 WHEN 管理员调用 `UserService.changeRole` 把目标用户角色降级为 USER（无需 SUPER_ADMIN 权限）时 THEN 系统未校验"调用方角色 ≥ 目标当前角色"，TENANT_ADMIN 可将本租户的 SUPER_ADMIN 反向降级为普通用户实现接管

1.8 WHEN `UserService.changeRole` 或 `banUser` 修改用户角色/状态后 THEN 系统未调用 `StpUtil.kickoutByLoginId` 强制下线，且 `AdminStpInterface.getRole` 优先从 Sa-Token Session 读取缓存的 role，导致被降级或封禁的用户在 Token 失效（默认 7 天）前仍持有原权限

1.9 WHEN `AuthController.changePassword` 处理修改密码请求时 THEN 接口使用 `Map<String, String>` 接收 newPassword 参数，未应用 `RegisterRequest` 的 `@Pattern` 强密码规则，攻击者可将密码修改为 `12345678` 等弱口令

**严重 — SSRF 与外部服务**

1.10 WHEN `PrivateNetworkValidator.requirePublic` 校验 AI baseUrl 后调用 RestTemplate 发起请求时 THEN 校验阶段与实际连接阶段两次 DNS 解析之间存在 TOCTOU 窗口，攻击者通过低 TTL 恶意 DNS 让校验阶段返回公网 IP、实际连接阶段返回内网 IP（如 169.254.169.254）

1.11 WHEN `ResourceController.preview` 处理 Office 文档时 THEN 后端直接 302 重定向到 `OFFICE_PREVIEW_URL?url=<downloadUrl>`，未对 `OFFICE_PREVIEW_URL` 做私网校验，且 kkfileview 服务历史存在 SSRF/RCE CVE，应改为前端跳转或限制网络出口

**严重 — 资源访问控制**

1.12 WHEN 攻击者枚举 `/api/v1/resources/{id}` 探测资源 ID 时 THEN 系统对"资源不存在"返回 `RESOURCE_NOT_FOUND (40402)`、对"资源存在但无权访问"返回 `FORBIDDEN (40301)`，错误码差异允许枚举本租户所有资源 ID

1.13 WHEN 后端 `application.yml` 未显式声明 `spring.servlet.multipart.max-file-size` 时 THEN Spring Boot 默认 1MB 限制实际生效但容器层缺乏统一约束，且 `ResourceService.upload` 通过 `file.getBytes()` 将整个文件读入内存计算 MD5，50MB × 并发 N 直接占用堆内存导致 OOM



**高 — 限流与暴力破解**

1.14 WHEN Redis 临时不可用导致 `LoginLockoutService.ensureNotLocked`、`UserService.isRateLimited`、`RedisRateLimiter.tryAcquire` 抛出异常时 THEN 系统统一 fail-open（直接放行），攻击者只需让 Redis 抖动几秒即可绕过登录失败锁定与忘记密码频率限制实施暴力破解

1.15 WHEN `LoginLockoutService` 按 `(tenantId, email)` 计数失败次数 THEN 攻击者能通过连续 5 次错误密码主动锁定任意已知邮箱用户 15 分钟，造成针对性账户拒绝服务

1.16 WHEN `AdminUserController.batchSetStatus` 接收 `ids` 数组时 THEN 接口未限制数组长度，攻击者传入数万 ID 触发数万次单独 SELECT+UPDATE 形成 N+1 查询与长事务持锁

**高 — 审计与溯源**

1.17 WHEN `AuditLogService.getClientIp` 记录操作来源 IP 时 THEN 直接信任 `X-Forwarded-For` 头未走 `TrustedProxyResolver` 校验，与限流器逻辑不一致，攻击者可伪造 X-Forwarded-For 在审计日志中栽赃他人 IP

1.18 WHEN `OpenAiCompatService.chatCompletion` 调用模型服务失败时 THEN 错误响应将上游 HTTP 状态码（如 401）原样回传给用户，攻击者可通过响应推断 API Key 是否有效或额度是否耗尽

**高 — XSS 与跨站脚本**

1.19 WHEN 前端 `Resources.vue` 使用 `<iframe :srcdoc="markdownSrcdoc">` 渲染用户上传的 Markdown 文档时 THEN iframe 缺少 `sandbox` 属性，与父页同源，若 markdown 渲染逻辑存在 corner case 绕过则可在 iframe 中执行任意脚本盗取 localStorage 中的 token

1.20 WHEN 用户调用 `PUT /api/v1/users/me` 更新个人资料时 THEN `UpdateProfileRequest` 的 `avatarUrl`、`profileCoverUrl`、`bio` 字段未做 URL 协议白名单校验与长度限制，攻击者可写入 `javascript:` 协议或超长内容

1.21 WHEN 后端通过 `PostService.toVO` 返回帖子作者信息时 THEN `UserVO.email` 字段被回传到所有公开列表接口（广场、空间、搜索），违反最小披露原则将用户邮箱暴露给同租户所有用户



**中 — 文件上传安全**

1.22 WHEN 用户上传扩展名为 `jpg/png/gif/webp` 的图片时 THEN 系统仅按 `originalFilename` 后缀校验，未读取文件 magic bytes 验证真实 MIME 类型，攻击者将 PHP/HTML/EICAR 等文件改名为 `.png` 即可绕过白名单

1.23 WHEN 用户上传 `zip/rar/7z` 压缩包到资源中心时 THEN 系统接受这些扩展名但未来如果增加自动解压会触发 zip-slip 路径穿越攻击，且攻击者可在压缩包中放置恶意载荷诱使其他用户下载

**中 — 业务输入校验**

1.24 WHEN 用户创建帖子或评论且 `content` 字段超过常规长度（数 MB）时 THEN `CreatePostRequest`/`CreateCommentRequest` 缺少 `@Size` 注解，理论上可写入超大内容触发数据库存储与索引服务异常

1.25 WHEN `GlobalExceptionHandler.handleIllegalState` 捕获 `MyBatisPlusConfig.TenantLineHandler` 在 `TenantContext` 为空时抛出的异常 THEN 系统返回 500 状态码，攻击者通过任意绕过 `TenantResolutionFilter` 的入口（如 actuator 路径）即可让接口返回 500 实施 DoS

**中 — 部署与基础设施**

1.26 WHEN nginx 反向代理响应前端请求时 THEN 配置文件 `deploy/nginx/nginx.conf` 缺少 `Content-Security-Policy`、`X-Frame-Options`、`Strict-Transport-Security`、`Referrer-Policy`、`X-Content-Type-Options` 等安全响应头

1.27 WHEN `backend/Dockerfile` 构建镜像时 THEN 容器以 root 用户运行 java 进程，违反最小权限原则，容器逃逸风险升高

1.28 WHEN nginx 反向代理转发请求时 THEN 配置未屏蔽 `/actuator` 路径，外部可探测 Spring Boot 健康端点信息

**中 — 前端与 API 表面**

1.29 WHEN 前端使用 `localStorage.getItem('token')` 持久化 Sa-Token 主令牌时 THEN 任何 XSS 漏洞都可直接读取 token 持续会话劫持，缺乏 HttpOnly Cookie 防护

1.30 WHEN 后端未显式配置 CORS 策略时 THEN 默认拒绝跨域请求看似安全，但生产 nginx 反代如果意外注入 `Access-Control-Allow-Origin: *` 配合 `Allow-Credentials: true` 会形成漏洞，应在后端显式声明白名单

1.31 WHEN `MentionParser` 正则与 `CreatePostRequest.content` 字段共同处理用户输入时 THEN 正则 `@([\w\u4e00-\u9fa5-]{1,30})` 不存在 catastrophic backtracking 风险，但 content 整体无服务端硬上限，配合大量 @mention 仍可放大资源消耗

1.32 WHEN `ResourceService.md5Hex` 用 MD5 计算文件指纹做去重 THEN MD5 抗碰撞已不可信，虽业务上仅用于去重不影响安全决策，但建议替换为 SHA-256 以避免未来被误用作完整性校验



### Expected Behavior (Correct)

**严重 — 凭证与机密管理**

2.1 WHEN 系统加解密租户 AI API Key 时 THEN 系统使用 AES-GCM 模式带随机 IV，密钥从环境变量 `CRYPTO_MASTER_KEY` 派生（HKDF-SHA256），密钥长度强制 ≥ 32 字节，缺失时启动失败；解密失败一律抛错不再回退原文

2.2 WHEN 系统存储密码重置令牌时 THEN 系统将 32 字节随机 token 用 SHA-256 哈希后存入 `users.reset_token`，用户邮件中收到明文 token，校验时对提交值 hash 后比对

2.3 WHEN 前端 WebSocket 客户端连接 `/ws/notify` 时 THEN 前端先调用 `/api/v1/auth/ws-ticket` 获取一次性短期票据（HMAC 签名，TTL=30 秒，userId+tenantId 绑定），用 ticket 替代 Sa-Token 主令牌走 query string

**严重 — 注入与跨租户隔离**

2.4 WHEN `findSubscribedUserIds` 按标签筛选订阅用户时 THEN tag 必须通过白名单校验（仅中英数字、`_`、`-`、长度 1-32），LIKE 前对 `\`、`%`、`_` 转义并附加 `ESCAPE '\\'`，SQL 中显式追加 `AND tenant_id = #{tenantId}`

2.5 WHEN 系统索引和搜索 MeiliSearch 文档时 THEN `indexDocument` 写入 `tenantId` 字段，`search` 调用附加 `filter: "tenantId = X"`，MeiliSearch 索引配置 `filterableAttributes` 包含 `tenantId`

2.6 WHEN `SearchService.searchPosts` 调用 MySQL FULLTEXT 时 THEN 使用已经过 `safeKeyword` 而非原始 `keyword`，并对 keyword 长度做硬上限（≤ 64 字符）



**严重 — 权限与会话管理**

2.7 WHEN 管理员调用 `changeRole` 修改任意用户角色时 THEN 系统强制要求"调用方角色权重 ≥ 目标用户当前角色权重"且"调用方角色权重 ≥ 目标新角色权重"，权重定义 `SUPER_ADMIN > TENANT_ADMIN > USER`

2.8 WHEN 用户角色变更或被封禁时 THEN 系统调用 `StpUtil.kickoutByLoginId(userId)` 强制下线，下次登录重建 Session，确保权限变更立即生效

2.9 WHEN 用户调用 `PUT /api/v1/auth/password` 修改密码时 THEN 接口接收 `ChangePasswordRequest` DTO（包含 `@NotBlank` oldPassword、应用与注册相同 `@Pattern` 强密码规则的 newPassword）



**严重 — SSRF 与外部服务**

2.10 WHEN 系统调用外部 AI 服务时 THEN 自定义 `ClientHttpRequestFactory` 在 `Socket.connect()` 拿到的 `InetAddress` 上再次执行 `PrivateNetworkValidator.isBlocked` 校验，连接阶段命中私网即抛 `SecurityException`

2.11 WHEN 用户预览 Office 文档时 THEN 后端 `ResourceController.preview` 不再 302 重定向，改为返回 JSON `{ previewUrl, signedDownloadUrl }`，前端在浏览器内跳转，避免后端发起到 kkfileview 的可达性

**严重 — 资源访问控制**

2.12 WHEN 攻击者枚举资源 ID 时 THEN 系统对"不存在"和"无权访问"统一返回 `RESOURCE_NOT_FOUND (40402)`，避免错误码差异泄漏资源存在性

2.13 WHEN 用户上传文件时 THEN `application.yml` 显式配置 `spring.servlet.multipart.max-file-size: 50MB` 与 `max-request-size: 60MB`，且 `ResourceService.upload` 改为流式 `DigestInputStream` 计算 MD5，不再一次性 `file.getBytes()`



**高 — 限流与暴力破解**

2.14 WHEN Redis 临时不可用时 THEN 登录失败锁定、忘记密码频率限制、登录限流（`POST /api/v1/auth/login`）、注册限流、AI 接口限流改为 fail-closed 返回 503 服务暂时不可用；普通读路径限流保留 fail-open

2.15 WHEN 同一邮箱连续登录失败时 THEN 系统按 `(IP, account)` 双维度计数，IP 维度阈值 20 次/15 分钟、账户维度阈值 10 次/15 分钟，账户锁定缩短为 5 分钟以减小拒绝服务影响

2.16 WHEN 管理员调用 `batchSetStatus` 等批量接口时 THEN 后端硬上限 `ids.size() <= 100`，超过抛 `BAD_REQUEST`

**高 — 审计与溯源**

2.17 WHEN `AuditLogService.log` 记录操作 IP 时 THEN 通过 `TrustedProxyResolver.resolve` 获取真实 IP，与限流器逻辑保持一致

2.18 WHEN AI 接口调用上游失败时 THEN 错误响应统一为通用文案 `"AI 服务暂时不可用，请稍后重试"`，不向客户端暴露上游 HTTP 状态码



**高 — XSS 与跨站脚本**

2.19 WHEN 前端 `Resources.vue` 渲染 Markdown 预览时 THEN `<iframe>` 添加 `sandbox="allow-popups allow-popups-to-escape-sandbox"`（不允许 scripts、不允许 same-origin），即使 markdown 转义被绕过也无法读取父页 localStorage

2.20 WHEN 用户更新个人资料时 THEN `UpdateProfileRequest` 对 `avatarUrl`、`profileCoverUrl` 字段做协议白名单校验（仅 `https?://` + 域名白名单），`bio` 字段加 `@Size(max = 200)` 限制并强制纯文本

2.21 WHEN 后端返回帖子作者 / 评论作者 / 资源上传者信息时 THEN 公共场景使用新的 `PublicUserVO`（仅 id/nickname/avatarUrl）替代 `UserVO`，`UserVO.email` 仅在 `/api/v1/auth/me`、`/api/v1/users/me` 等本人接口中返回



**中 — 文件上传安全**

2.22 WHEN 用户上传文件时 THEN 后端使用 Apache Tika 检测真实 MIME 类型，与扩展名做交叉验证，不一致时拒绝上传

2.23 WHEN 资源中心展示压缩包资源时 THEN 后端响应头强制 `Content-Disposition: attachment` 与 `X-Content-Type-Options: nosniff`，且 `application.yml` 默认从 `allowed-extensions` 中移除 `zip/rar/7z`，需要时由租户管理员显式开启

**中 — 业务输入校验**

2.24 WHEN 用户创建帖子或评论时 THEN `CreatePostRequest.content` 添加 `@Size(max = 20000)`、`title` 添加 `@Size(max = 200)`、`CreateCommentRequest.content` 添加 `@Size(max = 5000)`

2.25 WHEN `TenantContext` 缺失时 THEN `GlobalExceptionHandler.handleIllegalState` 返回 503 `SERVICE_UNAVAILABLE` 而非 500，并仅记录服务端日志不暴露内部细节；同时在 `TenantResolutionFilter.isExcluded` 中收紧 actuator 路径仅允许 localhost 访问



**中 — 部署与基础设施**

2.26 WHEN nginx 响应前端请求时 THEN 配置文件添加 `add_header Content-Security-Policy "default-src 'self'; ..."`、`X-Frame-Options DENY`、`Strict-Transport-Security "max-age=31536000; includeSubDomains"`、`Referrer-Policy "strict-origin-when-cross-origin"`、`X-Content-Type-Options nosniff`

2.27 WHEN Docker 镜像启动时 THEN `Dockerfile` 创建非 root 用户（如 `appuser` UID 1000）并以该用户运行 java 进程

2.28 WHEN 外部请求访问 `/actuator/**` 路径时 THEN nginx 返回 404，仅内网访问通过反代直连 8080 端口的 actuator



**中 — 前端与 API 表面**

2.29 WHEN 前端持久化登录态时 THEN 短期内沿用 localStorage 但通过 CSP `script-src 'self'` 缩小 XSS 攻击面（独立任务 2.26），后续路线图（不在本 spec 范围）改为 HttpOnly Cookie + CSRF Token

2.30 WHEN 后端处理跨域请求时 THEN 显式声明 `WebMvcConfigurer.addCorsMappings`，`allowedOrigins` 来自 `cors.allowed-origins` 配置（默认仅前端域名），`allowCredentials = false`（因 token 走 header 不需要 cookie）

2.31 WHEN 用户提交帖子或评论包含大量 @mention 时 THEN 服务端在 service 层对 mention 数量限制（≤ 20 个），超过部分静默丢弃只取前 20 个去发通知

2.32 WHEN `ResourceService` 计算文件指纹用于去重时 THEN 改为 SHA-256 替代 MD5，并在 `Resource` 实体中将字段重命名为 `fileSha256`（数据库 schema 同步迁移），避免后续被误用作完整性校验



### Unchanged Behavior (Regression Prevention)

**凭证与机密管理**

3.1 WHEN 租户管理员配置正确的 OpenAI/DeepSeek API Key 时 THEN 系统 SHALL CONTINUE TO 在 AI 调用时正确解密并使用密钥发起请求

3.2 WHEN 用户通过有效未过期的重置令牌重置密码时 THEN 系统 SHALL CONTINUE TO 完成密码重置并使旧令牌立即失效

3.3 WHEN 已登录用户连接 WebSocket 时 THEN 系统 SHALL CONTINUE TO 推送通知与消息，仅认证机制由 token 改为 ticket

**注入与跨租户隔离**

3.4 WHEN 用户使用合法标签订阅与发帖时 THEN 系统 SHALL CONTINUE TO 正确将标签订阅通知发送给同租户的订阅者

3.5 WHEN 用户使用关键字搜索时 THEN 系统 SHALL CONTINUE TO 正确返回本租户内的相关帖子、用户、资源、空间结果



**权限与会话管理**

3.6 WHEN SUPER_ADMIN 提升用户为 TENANT_ADMIN 时 THEN 系统 SHALL CONTINUE TO 成功修改角色

3.7 WHEN 用户使用强密码（含字母与数字、长度 ≥ 8）修改密码时 THEN 系统 SHALL CONTINUE TO 成功更新密码并保留登录态

3.8 WHEN 用户角色未变更时 THEN 系统 SHALL CONTINUE TO 不强制下线，正常会话不受影响

**SSRF 与外部服务**

3.9 WHEN AI baseUrl 指向公网正常域名（如 `https://api.deepseek.com/v1`）时 THEN 系统 SHALL CONTINUE TO 正常调用上游 AI 服务

3.10 WHEN 用户预览 PDF 或图片资源时 THEN 系统 SHALL CONTINUE TO 流式返回内容，不受 Office 预览改造影响

**资源访问与上传**

3.11 WHEN 用户上传 ≤ 50MB 的合法文件时 THEN 系统 SHALL CONTINUE TO 成功保存到 MinIO/本地存储并创建资源记录

3.12 WHEN 同一用户重复上传同一文件时 THEN 系统 SHALL CONTINUE TO 通过指纹去重返回已有资源（指纹算法从 MD5 切换为 SHA-256，过渡期同时支持）

3.13 WHEN 用户访问公开（PUBLIC）资源时 THEN 系统 SHALL CONTINUE TO 允许下载、预览，错误码统一仅影响枚举攻击者体验



**限流与暴力破解**

3.14 WHEN Redis 正常工作时 THEN 系统 SHALL CONTINUE TO 按现有阈值执行登录限流、忘记密码限流、AI 接口限流

3.15 WHEN 用户在阈值内正常登录时 THEN 系统 SHALL CONTINUE TO 不锁定账户，登录成功后正常颁发 token

**审计、XSS、信息泄露**

3.16 WHEN 管理员在可信代理后访问后台时 THEN 系统 SHALL CONTINUE TO 在审计日志中记录真实客户端 IP（与限流器解析一致）

3.17 WHEN 用户预览合法 Markdown 文档时 THEN 系统 SHALL CONTINUE TO 在 sandboxed iframe 中正确渲染标题、列表、引用、代码块

3.18 WHEN 用户上传指向本站存储域名的头像 URL 时 THEN 系统 SHALL CONTINUE TO 接受并保存

3.19 WHEN 帖子详情页 / 评论列表展示作者信息时 THEN 系统 SHALL CONTINUE TO 显示昵称与头像，仅 email 字段从公共响应中移除

**部署、CORS、其他加固**

3.20 WHEN 前端从配置的允许域名访问后端 API 时 THEN 系统 SHALL CONTINUE TO 正常响应跨域请求

3.21 WHEN 用户上传合法图片或 DOCX 文档时 THEN 系统 SHALL CONTINUE TO 通过 MIME 检测，不被误拦截

3.22 WHEN 用户发布合法长度的帖子或评论时 THEN 系统 SHALL CONTINUE TO 成功创建（长度限制内的内容不受影响）
