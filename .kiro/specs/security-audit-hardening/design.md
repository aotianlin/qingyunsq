# CampusForum 安全审计与加固 Design

## Overview

本文档是 [`bugfix.md`](./bugfix.md) 中 32 条漏洞的技术设计方案，按"主题域"组织而非按漏洞编号——同一主题下的漏洞通常共用基础设施改造（启动校验器、安全过滤器、监控指标），合并设计可减少 PR 数量与回归面。

每个主题块按下列结构推进：
1. **现状概述**：列出涉及的源码位置和当前行为；
2. **目标状态**：用类签名 / 配置示例描述修复后的形态；
3. **关键决策**：在多种实现路径之间的取舍说明；
4. **影响面**：列出对外部 API、数据库、配置的破坏性变更（如有），以及兼容期方案；
5. **测试要点**：fix-checking + preservation-checking + regression-checking 用例点；
6. **覆盖漏洞**：本主题块对应 `bugfix.md` 中的漏洞编号。

整体设计原则：
- **优先做"启动期阻断"而非"运行时检测"**：能在启动期挡掉的弱配置，绝不留到运行时被绕过（漏洞 3 / 10 / 14）；
- **强类型 DTO + Bean Validation**：避免 NPE 与隐式类型转换（漏洞 16 / 17 / 18 / 25）；
- **Filter Chain 链路工具方法**：IP 解析、路径模板提取统一抽到 `infra/web` 包；
- **关键路径必须埋点 Counter**：与 Critical / High 修复同期完成监控（漏洞 32），让加固可度量；
- **保留兼容期**：所有破坏性变更提供 ENV / `legacy-mode` 开关，至少灰度一周再切默认值；
- **变更最小化**：同一文件多处 patch 优先合并到一个 PR；跨模块变更拆 PR；
- **代码注释一律使用中文**（与 `AGENTS.md` 仓库规范一致）。

## Bug Details

> 本设计文档与 [`bugfix.md`](./bugfix.md) 一一对应，覆盖 32 条漏洞。每条漏洞的"风险描述、受影响位置、触发条件 V(X)、影响、安全属性 (EARS)"在 bugfix.md 中已经完整给出，本文不重复，仅在主题章节内引用编号（如 "覆盖漏洞 1, 3, 4"）。

主题分组见下文 `## Architecture → 主题分组`，每个主题块对应 `## Components and Interfaces` 的一个子章节。

## Expected Behavior

> 每条漏洞的"修复后必须满足的安全属性"以 EARS 形式写在 bugfix.md 中。本文在 `## Components and Interfaces → 主题 N → 目标状态` 给出落地形态：包含具体的接口签名、配置项、SQL 与时序变化。修复完成后的全局期望状态在 bugfix.md `### Expected Behavior (Correct)` 已列举，本文不再重复。

## Hypothesized Root Cause

本次审计的 32 条漏洞按根因可归并为 6 类：

1. **凭证治理空缺**：硬编码密钥 + 弱默认值仅 WARN 不阻断（漏洞 1, 3, 4）— 缺乏统一的"prod profile 启动期严格校验"框架。
2. **流式 / 异步上下文丢失**：`InputStream.available()`、Request-scope 注入、TenantContext 在异步线程缺失（漏洞 6, 22, 26）— 基础设施假设了"同步 + Servlet 线程"上下文。
3. **限流 / 文档暴露面建模粗粒度**：URI vs 路由模板、profile vs filter 隔离（漏洞 2, 7, 10）— Spring 默认行为没有按"安全优先"配置。
4. **会话状态机不完整**：密码变更不踢下线、WS legacy token 默认开启（漏洞 5, 8）— 早期为兼容期保留的开关未设置 cutover 强制日期。
5. **输入信任边界模糊**：HTML 净化未启用、Markdown 拼接未转义、用户搜索字段过宽（漏洞 9, 13, 17, 18, 20, 27）— 缺少集中的 sanitize / normalize 基础设施。
6. **可观测性缺失**：监控指标、TraceId 与 MDC、AuditContext 未抽象（漏洞 26, 28, 31, 32）— 项目早期专注业务实现，未沉淀安全运维可见性。

| 根因类别 | 对应主题 |
|---|---|
| 凭证治理 | 主题 1（凭证 + 启动校验） |
| 异步上下文 | 主题 4（存储一致性）、主题 6（多租户）、主题 9（审计） |
| 暴露面建模 | 主题 2（文档）、主题 5（限流） |
| 会话状态机 | 主题 3（会话生命周期） |
| 输入信任边界 | 主题 8（XSS / 净化） |
| 可观测性 | 主题 9（审计 + 监控） |

## Fix Implementation

> 具体的代码改造、新增类与接口签名、配置项变更、数据库迁移脚本、灰度策略均在下方 `## Components and Interfaces`（主题 1-9）+ `## Data Models` + `## Migration Plan` 中按主题分组给出。每个主题块的"目标状态"小节即为该批漏洞的 Fix Implementation 摘要。


## Architecture

### 主题分组

下表把 32 条漏洞按 9 个主题域分组，每组对应后续一个二级章节：

| 主题域 | 覆盖漏洞 | 主要改造对象 |
|---|---|---|
| 1. 凭证管理与启动校验 | 1, 3, 4, 32（监控） | `CryptoUtils`、`SecurityStartupValidator`、`SecurityProperties`、`application*.yml` |
| 2. 文档/管理面暴露 | 2 | `Knife4jConfig`、`TenantResolutionFilter`、`nginx.conf`、新增 `DocAccessFilter` |
| 3. 会话生命周期 | 5, 8, 29, 30 | `UserService`、`TenantHandshakeInterceptor`、前端 `request.ts`/`auth.ts` |
| 4. 文件存储一致性 | 6, 15, 24 | `StorageService` 接口、三个实现类、`MimeTypeValidator`、`UserController#uploadProfileAsset` |
| 5. 限流与暴力破解 | 7, 10, 11, 16, 21 | `RateLimitInterceptor`、`RateLimitProperties`、`EmailVerificationCodeService` |
| 6. 多租户隔离纵深 | 14, 19, 22, 25 | `TenantStartupValidator`、`MyBatisPlusConfig`、`MeiliSearchClient`、`MultiTenantResolver` |
| 7. AI 与 SSRF 纵深 | 12, 23 | `OpenAiCompatService`、`TenantAwareAiService`、`SafeHttpClient` |
| 8. XSS / 输入净化 / 敏感数据 | 9, 13, 17, 18, 20, 27 | 新增 `HtmlSanitizerService`、`TextNormalizer`；多个 DTO；`SearchService`；`ExportService`；前端 `MentionText.vue` |
| 9. 审计与可观测性 | 26, 28, 31, 32 | `AuditLogService`、`GlobalExceptionHandler`、新增 `MdcTraceIdFilter`、`MeterRegistry` 埋点 |

### 新增基础组件总览

```
backend/src/main/java/com/campusforum/
├── infra/
│   ├── security/
│   │   ├── crypto/
│   │   │   ├── CryptoService.java                (现有，扩展)
│   │   │   └── legacy/
│   │   │       └── EcbCryptoUtils.java           (新，承接旧 CryptoUtils 私有逻辑)
│   │   ├── DocAccessFilter.java                  (新，prod 拒绝 swagger/api-docs)
│   │   ├── SecurityStartupValidator.java         (现有，扩展严格分支)
│   │   ├── SafeHttpClient.java                   (现有，禁用 redirect)
│   │   └── RedirectFollower.java                 (新，手动 redirect + 重新校验 host)
│   ├── sanitize/
│   │   ├── HtmlSanitizerService.java             (新，OWASP Sanitizer 包装)
│   │   └── TextNormalizer.java                   (新，NFKC / 零宽 / 全半角)
│   ├── audit/
│   │   ├── AuditContext.java                    (新，封装 IP/UA/userId/tenantId)
│   │   └── AuditLogService.java                  (现有，签名变更)
│   ├── ratelimit/
│   │   ├── RateLimitInterceptor.java             (现有，路径模板)
│   │   └── RouteTemplateExtractor.java           (新，统一从 BEST_MATCHING_PATTERN 取模板)
│   ├── web/
│   │   ├── MdcTraceIdFilter.java                 (新，traceId/MDC 注入)
│   │   └── ClientIpResolver.java                 (新，整合 TrustedProxyResolver 暴露给所有组件)
│   └── metrics/
│       └── SecurityMetrics.java                  (新，Micrometer Counter 集中定义)
├── tenant/
│   ├── TenantStartupValidator.java               (现有，扩展 ignore-tables 校验)
│   └── filter/TenantResolutionFilter.java        (现有，限制 swagger 路径)
└── post/
    └── service/
        ├── MarkdownEscaper.java                  (新，引用块拼接转义)
        └── PostViewDeduper.java                  (新，浏览计数去重)
```


### 配置项总览（新增 / 修改）

```yaml
# application.yml（删除 / 收紧默认值）
security:
  signed-url-secret: ${SIGNED_URL_SECRET:}            # 删除字面默认值
  crypto:
    master-key: ${CRYPTO_MASTER_KEY:}                  # 删除 dev-only 字面默认
    legacy-mode: false
    legacy-cutover-date: 2026-09-01                    # 新，到期后强制不再走 legacy
  ws-ticket:
    enforced: false
    enforced-cutover-date: 2026-07-01                  # 新，到期后强制 enforced=true
  upload:
    real-mime-check: true
    blocked-mime-types:                                # 新
      - application/x-php
      - application/x-msdownload
      - application/x-msdos-program
    allowed-asset-hosts: ${ALLOWED_ASSET_HOSTS:}
    self-hosts:                                        # 新，从 storage 端点推导默认值
      - ${STORAGE_MINIO_ENDPOINT:}
  docs:
    enabled-profiles:                                  # 新
      - dev
      - test

springdoc:
  api-docs:
    enabled: ${SPRINGDOC_ENABLED:false}
  swagger-ui:
    enabled: ${SPRINGDOC_ENABLED:false}

rate-limit:
  exclude-patterns:
    - /actuator/**                                     # 移除 /api/v1/auth/login 默认值
  overrides:
    "[POST /api/v1/messages]":
      max-requests: 30
      window-seconds: 60
    "[GET /api/v1/posts/{id}]":
      max-requests: 120
      window-seconds: 60
    "[GET /api/v1/resources/{id}/download]":
      max-requests: 30
      window-seconds: 60
    "[GET /api/v1/resources/{id}/preview]":
      max-requests: 30
      window-seconds: 60
    "[POST /api/v1/admin/export/users]":
      max-requests: 1
      window-seconds: 60

management:
  endpoints:
    web:
      exposure:
        include: health,prometheus                      # 新增 prometheus
  endpoint:
    health:
      show-details: never
```

### 数据库迁移总览

```
db/migrations/
├── V20260601_01__nickname_pattern.sql          (漏洞 20: nickname 字符白名单 - 仅注释，应用层校验)
├── V20260601_02__messages_ai_risk_level.sql    (漏洞 16: messages 增加 ai_risk_level 字段)
├── V20260601_03__post_view_dedup_index.sql     (漏洞 21: 占位，本期 Redis 实现)
├── V20260601_04__audit_log_extend.sql          (漏洞 13/14/26: audit_log 增加 user_agent 列)
├── V20260601_05__resources_legacy_md5.sql      (漏洞 6: resources.file_md5 标 deprecated)
└── V20260601_06__sensitive_word_regex.sql      (漏洞 27: sensitive_words 增加 is_regex 字段)
```

## Components and Interfaces

详细到每个主题块。下面 9 个主题章节按主题域顺序展开。


---

### 主题 1：凭证管理与启动校验

#### 现状概述
- `CryptoUtils` 公共类持有硬编码 16 字节密钥 `"CampusForum@1234"`，`encrypt()` 与 `decrypt()` 都是 public static，且 `decrypt` 失败静默回退原文。
- `application.yml` 中 `signed-url-secret` 默认值含 `please-override`、`crypto.master-key` 默认 `dev-only-change-me-...`。
- `SecurityStartupValidator#validateSignedUrlSecret` 仅 WARN，不阻断启动。
- `docker-compose.yml` 注入了 `SA_TOKEN_JWT_SECRET_KEY=${JWT_SECRET}`，但项目**未启用** Sa-Token JWT 模式，是死配置。

#### 目标状态 — `CryptoUtils` 收缩

新位置 `infra/security/crypto/legacy/EcbCryptoUtils.java`，类与方法都是 package-private，删除 `encrypt()` 仅保留解密：

```java
package com.campusforum.infra.security.crypto.legacy;

@Deprecated(forRemoval = true)
final class EcbCryptoUtils {
    private static final String DEFAULT_KEY = "CampusForum@1234";

    private EcbCryptoUtils() {}

    /** 解密旧 ECB 密文。失败抛 CryptoException，**不再回退原文**。 */
    static String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isBlank()) {
            throw new CryptoException("旧密文为空");
        }
        try {
            // ... ECB 解密逻辑
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new CryptoException("旧密文解密失败");
        }
    }
}
```

`CryptoService.decryptLegacyEcb` 转发到此类，并调用 `securityMetrics.cryptoDecryptLegacy(tenantId)`。


#### 目标状态 — `SecurityStartupValidator` 严格分支

```java
@Component
@Order(20)
@RequiredArgsConstructor
public class SecurityStartupValidator implements ApplicationRunner {

    private static final int MIN_MASTER_KEY_BYTES = 32;
    private static final List<String> FORBIDDEN_DEFAULT_TOKENS = List.of(
            "please-override", "dev-only-change-me", "ChangeMe", "minioadmin"
    );

    private final SecurityProperties props;
    private final Environment env;
    private final RateLimitProperties rateLimitProperties;

    @Override
    public void run(ApplicationArguments args) {
        boolean isProd = Arrays.asList(env.getActiveProfiles()).contains("prod");
        validateCrypto(isProd);
        validateSignedUrlSecret(isProd);
        validateRedisPassword(isProd);
        validateRateLimitExcludePatterns();   // 漏洞 10
        validateLegacyCutoverDates(isProd);   // 漏洞 1, 8 兼容期截止日期
    }

    private void validateCrypto(boolean isProd) {
        var cfg = props.getCrypto();
        if (cfg.isLegacyMode()) {
            log.warn("crypto legacy-mode ENABLED — 仅紧急回滚使用");
            return;
        }
        String key = cfg.getMasterKey();
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("security.crypto.master-key 未配置");
        }
        int len = key.getBytes(StandardCharsets.UTF_8).length;
        if (len < MIN_MASTER_KEY_BYTES) {
            throw new IllegalStateException("master-key 长度不足 " + MIN_MASTER_KEY_BYTES + " 字节");
        }
        if (isProd && containsForbiddenToken(key)) {
            throw new IllegalStateException("生产环境 master-key 包含弱默认值 token");
        }
    }

    private void validateSignedUrlSecret(boolean isProd) {
        String s = props.getSignedUrlSecret();
        if (s == null || s.isBlank()) {
            throw new IllegalStateException("security.signed-url-secret 未配置");
        }
        if (isProd && containsForbiddenToken(s)) {
            throw new IllegalStateException("生产环境 signed-url-secret 仍为默认值");
        }
        if (s.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("signed-url-secret 长度不足 32 字节");
        }
    }

    private void validateRedisPassword(boolean isProd) {
        if (!isProd) return;
        String pwd = env.getProperty("spring.data.redis.password", "");
        if (pwd.length() < 16 || containsForbiddenToken(pwd)) {
            throw new IllegalStateException("生产 Redis 密码强度不足或仍为默认");
        }
    }

    private boolean containsForbiddenToken(String value) {
        return FORBIDDEN_DEFAULT_TOKENS.stream().anyMatch(value::contains);
    }
}
```

`application.yml` 字面默认值清理：删除 `signed-url-secret`、`crypto.master-key` 的字面默认值，改为 `${... :}`，让缺失变成空字符串触发上面校验。

#### 关键决策

| 决策 | 选项 | 选择 | 理由 |
|---|---|---|---|
| `CryptoUtils` 处置 | (a) 删除 (b) package-private (c) 加警告 | b | 仍需读取历史密文；private 化阻断新调用方。 |
| `encrypt()` 方法 | (a) 保留 (b) 抛 UnsupportedOperationException (c) 删除 | c | 兼容期没有任何代码路径需要写入新 ECB 密文。 |
| 启动校验 | (a) 仅 WARN (b) prod 抛错 dev WARN (c) 全 profile 抛错 | b | 开发环境零摩擦；生产强制阻断。 |
| Redis 密码强度校验 | (a) 应用层 (b) 仅文档约定 | a | 应用层兜底，避免运维误用 `123456`。 |

#### 影响面
- **破坏性**：未配置 `CRYPTO_MASTER_KEY` 或仍用默认值的 prod 部署会启动失败 — 这是**期望行为**。
- **数据库**：无变更。
- **兼容期**：`legacy-mode=true` 仍可绕过新校验作为紧急回滚。

#### 测试要点
- **fix-checking**：prod profile + master-key = "dev-only-change-me-..." → 启动失败；signed-url-secret 长度 < 32 → 启动失败；redis password = "123456" → 启动失败；调用 `EcbCryptoUtils.decrypt` 抛 CryptoException 时**不返回原文**。
- **preservation-checking**：dev profile + 默认值 → 启动正常 + WARN 日志；现有 v2 GCM 加解密链路通过；`resolveAiCredentials` 异步 re-encrypt 不受影响。
- **regression-checking**：完整 SpringBootTest 启动套件保持绿。

#### 覆盖漏洞
- **1** 旧 ECB 密钥
- **3** signed-url-secret 弱默认仅 WARN
- **4** SA_TOKEN_JWT_SECRET_KEY 死配置（文档清理 + Redis 密码强度校验）
- **32** 监控埋点 crypto_decrypt_legacy_total


---

### 主题 2：文档/管理面暴露收紧

#### 现状概述
- `Knife4jConfig` 在所有 profile 都注册了 v1 OpenAPI 分组，扫描 `/api/v1/**`。
- `SaTokenConfig` 拦截器仅作用于 `/api/v1/**`，swagger / api-docs 不在范围。
- `TenantResolutionFilter#isExcluded` 显式放行 `/swagger-ui/`、`/v3/api-docs/`。
- `nginx.conf` 仅屏蔽 `/actuator/`，未屏蔽文档路径。

#### 目标状态 — 新增 `DocAccessFilter`

```java
@Component
@RequiredArgsConstructor
public class DocAccessFilter extends OncePerRequestFilter {

    private static final List<String> DOC_PATH_PREFIXES = List.of(
            "/swagger-ui/", "/swagger-ui.html",
            "/v3/api-docs", "/swagger-resources",
            "/doc.html", "/webjars/"
    );

    private final SecurityProperties props;
    private final Environment env;
    private final TrustedProxyResolver trustedProxyResolver;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        String uri = req.getRequestURI();
        if (DOC_PATH_PREFIXES.stream().noneMatch(uri::startsWith)) {
            chain.doFilter(req, res);
            return;
        }
        Set<String> activeProfiles = Set.of(env.getActiveProfiles());
        Set<String> docEnabled = new HashSet<>(props.getDocs().getEnabledProfiles());
        boolean profileAllows = activeProfiles.stream().anyMatch(docEnabled::contains);

        // 即使 profile 允许，仍要求来源是可信代理
        boolean fromTrusted = trustedProxyResolver.isFromTrustedProxy(req.getRemoteAddr());

        if (!profileAllows || !fromTrusted) {
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        chain.doFilter(req, res);
    }
}
```

注：`TrustedProxyResolver#isFromTrustedProxy` 当前是 private，需要改为 public。

#### 目标状态 — `TenantResolutionFilter` 与 nginx 调整

```java
private boolean isExcluded(HttpServletRequest req) {
    String uri = req.getRequestURI();
    if (uri.startsWith("/actuator/") || uri.startsWith("/swagger-ui/")
            || uri.startsWith("/v3/api-docs") || uri.startsWith("/webjars/")) {
        return true;  // 由 DocAccessFilter 在更早阶段裁决
    }
    return uri.startsWith("/ws/");
}
```

`nginx.conf` 双重屏蔽：

```
location ~ ^/(swagger-ui|v3/api-docs|swagger-resources|doc\.html|webjars)/ {
    return 404;
}
```

`application.yml` 配置：

```yaml
springdoc:
  api-docs:
    enabled: ${SPRINGDOC_ENABLED:false}
  swagger-ui:
    enabled: ${SPRINGDOC_ENABLED:false}

security:
  docs:
    enabled-profiles:
      - dev
      - test
```

#### 关键决策

| 决策 | 选项 | 选择 | 理由 |
|---|---|---|---|
| 如何禁用 docs | (a) springdoc 配置项 (b) 自定义 Filter (c) 删除 Knife4jConfig Bean | a + b 双保险 | 配置项让 prod 不创建 endpoints；Filter 是兜底，应对运维误覆盖配置项。 |
| 是否完全删除依赖 | (a) 保留 (b) provided (c) 删除 | a | 依赖被多个开发链路使用，运行时 endpoints 关闭即可。 |

#### 影响面
- **破坏性**：现有"打开生产 swagger 看接口"的运维习惯失效，需要走文档站或本地 dev profile。
- **ENV 新增**：`SPRINGDOC_ENABLED`（默认 false）。

#### 测试要点
- **fix-checking**：prod profile + 任意外部 IP 请求 `/v3/api-docs` → 404；即便 `SPRINGDOC_ENABLED=true` 但来源 IP 不在 trusted-proxies → 404；dev profile + localhost 请求 `/swagger-ui/index.html` → 200。
- **preservation-checking**：controller 上 `@Operation`/`@Parameter` 注解仍可被扫描；业务接口正常工作。

#### 覆盖漏洞
- **2** Knife4j / api-docs 暴露


---

### 主题 3：会话生命周期收紧

#### 现状概述
- `UserService#changePassword` / `resetPassword` 仅更新 password_hash，**没有 kickout**。
- `TenantHandshakeInterceptor#beforeHandshake` 默认走 legacy token 分支，token 暴露在 URL。
- `TenantHandshakeInterceptor#extractQueryParam` 不做 URL decode，导致含 `+`/`/` 的 ticket 解析失败。
- 前端 token 存 `localStorage`，依赖 XSS 防护。

#### 目标状态 — `UserService` 敏感变更统一处理

```java
public class UserService {

    /** 敏感凭证变更后统一调用：踢下线全部活跃 token + 写审计 + 监控埋点。 */
    private void invalidateAllSessions(Long userId, String action) {
        try {
            StpUtil.logoutByLoginId(userId);
        } catch (Exception e) {
            log.warn("logoutByLoginId failed for user {}: {}", userId, e.getMessage());
        }
        auditLogService.log(action, "user", userId, "all sessions invalidated");
        securityMetrics.sessionForcedLogout(action);
    }

    @Transactional
    public void changePassword(Long userId, String oldPwd, String newPwd) {
        // ... 原有校验
        user.setPasswordHash(BCrypt.hashpw(newPwd, BCrypt.gensalt(10)));
        userMapper.updateById(user);
        invalidateAllSessions(userId, "PASSWORD_CHANGE");  // 新增
    }

    @Transactional
    public void resetPassword(String email, String emailCode, String newPassword) {
        // ... 原有逻辑
        userMapper.updateById(user);
        invalidateAllSessions(user.getId(), "PASSWORD_RESET");  // 新增
    }
}
```

前端配合：`changePassword` API 调用后由 `useAuthStore().logout()` 主动登出：

```ts
// frontend/src/api/auth.ts
export async function changePassword(payload: ChangePasswordRequest) {
  await request({ method: 'PUT', url: '/auth/password', data: payload });
  useAuthStore().logout();
  router.push('/login');
}
```

#### 目标状态 — WebSocket ticket cutover + URL decode

`SecurityStartupValidator` 增加 cutover 校验：

```java
private void validateLegacyCutoverDates(boolean isProd) {
    if (!isProd) return;
    LocalDate today = LocalDate.now();
    LocalDate wsTicketCutover = props.getWsTicket().getEnforcedCutoverDate();
    if (wsTicketCutover != null && today.isAfter(wsTicketCutover)
            && !props.getWsTicket().isEnforced()) {
        throw new IllegalStateException(
                "WS ticket cutover 已到期 (" + wsTicketCutover + ")，请设置 WS_TICKET_ENFORCED=true");
    }
}
```

`TenantHandshakeInterceptor#verifyByLegacyToken` 入口加监控：

```java
private boolean verifyByLegacyToken(...) {
    securityMetrics.wsLegacyTokenUsed();
    log.warn("WS legacy token path used by ip={}, ua={}",
            request.getRemoteAddress(), request.getHeaders().getFirst("User-Agent"));
    // ... 原逻辑
}
```

`extractQueryParam` 增加 URL decode（漏洞 29 一并修复）：

```java
private String extractQueryParam(ServerHttpRequest request, String name) {
    String query = request.getURI().getRawQuery();
    if (query == null) return null;
    String prefix = name + "=";
    for (String kv : query.split("&")) {
        if (kv.startsWith(prefix)) {
            String value = kv.substring(prefix.length());
            if (value.isEmpty()) return null;
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        }
    }
    return null;
}
```

#### 关键决策

| 决策 | 选项 | 选择 | 理由 |
|---|---|---|---|
| 密码变更后是否注销当前会话 | (a) 仅其他设备 (b) 全部含当前 | b | 安全标准做法；前端主动登出。 |
| ws ticket cutover 实现 | (a) 启动期阻断 (b) 运行时降级 | a + 监控告警 | 启动期阻断防止"忘记切换"。 |
| 前端 token 迁移到 cookie | (a) 本次 (b) 后续 PR | b | 涉及 CSRF 改造，先解决 XSS（漏洞 18）。 |

#### 影响面
- 修改密码 API 200 后，下一次任意请求 401。前端配合主动登出。
- WebSocket legacy 路径在 cutover 日期后启动失败。

#### 测试要点
- **fix-checking**：用户 A 在浏览器 X / Y 都登录 → X 修改密码 → 两个浏览器后续请求都 401；WS 用 `ticket=XXX%2FYYY%3D`（含 URL 编码）解析成功；prod profile + 当前日期 > `ws-ticket.enforced-cutover-date` + `WS_TICKET_ENFORCED=false` → 启动失败。
- **preservation-checking**：普通登录 → 发帖不受影响；WS ticket 模式连接正常。

#### 覆盖漏洞
- **5** 密码变更未踢下线
- **8** WebSocket legacy token 默认开启
- **29** WebSocket query 参数未 URL decode
- **30** 前端 token 持久化（仅记录长期路线，本次不实施）


---

### 主题 4：文件存储一致性

#### 现状概述
- `MinioStorageService#upload` 用 `inputStream.available()` 作为 size，导致大文件被截断。
- `LocalStorageService` / `OssStorageService` 不依赖 size，但接口不一致。
- `UserController#uploadProfileAsset` 在 minio 模式返回错误 URL。
- `MimeTypeValidator` 对未注册扩展名静默放行，且向 Tika 传 `RESOURCE_NAME_KEY` 让文件名 hint 影响判断。

#### 目标状态 — `StorageService` 接口签名变更

```java
public interface StorageService {

    /**
     * 上传文件到对象存储。
     *
     * @param size 文件总字节数（必须 ≥ 0），用于：
     *        - 流式上传 SDK 提供精确 size 避免截断
     *        - 上传完成后 statObject 回查
     */
    String upload(InputStream inputStream, String originalName, String contentType, long size);

    InputStream download(String storageKey);

    void delete(String storageKey);

    /**
     * 颁发短期下载 URL，用于头像/封面等公开访问场景。
     * MinIO/OSS 用 presignedGetObject；Local 用 SignedUrlService 站内代理。
     */
    String issuePublicGetUrl(String storageKey);
}
```

#### 目标状态 — `MinioStorageService#upload` 修正

```java
@Override
public String upload(InputStream inputStream, String originalName, String contentType, long size) {
    String storageKey = buildStorageKey(extractExt(originalName));

    try {
        client.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(storageKey)
                .stream(inputStream, size, -1)   // 显式 size 而非 available()
                .contentType(contentType != null ? contentType : "application/octet-stream")
                .build());

        // 回查校验
        StatObjectResponse stat = client.statObject(StatObjectArgs.builder()
                .bucket(bucket).object(storageKey).build());
        if (stat.size() != size) {
            log.error("MinIO upload size mismatch: expected={}, actual={}, key={}",
                    size, stat.size(), storageKey);
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(storageKey).build());
            throw new BusinessException(ErrorCode.STORAGE_ERROR);
        }
        return storageKey;
    } catch (Exception e) {
        log.error("MinIO upload failed", e);
        throw new BusinessException(ErrorCode.STORAGE_ERROR);
    }
}

@Override
public String issuePublicGetUrl(String storageKey) {
    try {
        return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .bucket(bucket)
                .object(storageKey)
                .method(Method.GET)
                .expiry(securityProperties.getSignedUrlTtlSeconds() * 5, TimeUnit.SECONDS)
                .build());
    } catch (Exception e) {
        throw new BusinessException(ErrorCode.STORAGE_ERROR);
    }
}
```

`LocalStorageService.issuePublicGetUrl`：通过 SignedUrlService 颁发 `/api/v1/users/avatars/<id>?sig=...` 形式的签名 URL。

#### 目标状态 — `UserController#uploadProfileAsset` 重写

```java
@PostMapping("/me/assets")
public R<UserAssetUploadVO> uploadProfileAsset(@RequestParam("file") MultipartFile file) throws IOException {
    StpUtil.checkLogin();
    validateProfileImage(file);
    String storageKey = storageService.upload(
            file.getInputStream(),
            file.getOriginalFilename(),
            file.getContentType(),
            file.getSize());
    String url = storageService.issuePublicGetUrl(storageKey);
    return R.ok(UserAssetUploadVO.builder().url(url).storageKey(storageKey).build());
}
```

#### 目标状态 — `MimeTypeValidator` 严格化

```java
@Component
@RequiredArgsConstructor
public class MimeTypeValidator {

    /** 全局白名单：扩展名 → 允许的 detected MIME 集合。未注册扩展名直接拒绝。 */
    private static final Map<String, Set<String>> EXT_TO_MIMES = Map.ofEntries(/* 同现状 */);

    /** 全局黑名单：detected MIME 命中即拒绝。 */
    private static final Set<String> BLOCKED_MIMES = Set.of(
            "application/x-php",
            "application/x-msdownload",
            "application/x-msdos-program",
            "application/x-bat",
            "application/x-sh",
            "text/x-php",
            "text/x-script.python"
    );

    public void validate(MultipartFile file, String declaredExt) {
        if (!securityProperties.getUpload().isRealMimeCheck()) return;
        String ext = declaredExt.toLowerCase(Locale.ROOT);

        Set<String> allowed = EXT_TO_MIMES.get(ext);
        if (allowed == null) {
            // 改变：未注册扩展名直接拒绝（替代原"静默放行"策略）
            throw new MimeMismatchException("不支持的扩展名：" + ext);
        }

        String detected;
        try (InputStream raw = file.getInputStream();
             TikaInputStream tis = TikaInputStream.get(raw)) {
            // 不再传 RESOURCE_NAME_KEY，避免文件名 hint 干扰
            Metadata meta = new Metadata();
            detected = detector.detect(tis, meta).toString().toLowerCase(Locale.ROOT);
        } catch (IOException e) {
            throw new MimeMismatchException("无法识别文件类型");
        }

        // 黑名单优先于白名单
        if (BLOCKED_MIMES.contains(detected)) {
            securityMetrics.mimeMismatch(ext, detected);
            throw new MimeMismatchException("禁止上传 " + detected + " 类型文件");
        }
        if (!allowed.contains(detected)) {
            securityMetrics.mimeMismatch(ext, detected);
            throw new MimeMismatchException("文件扩展名 ." + ext + " 与实际类型 " + detected + " 不一致");
        }
    }
}
```

#### 目标状态 — `assertHostAllowed` 默认从 self-hosts 推导

```java
// SecurityProperties.Upload
private List<String> selfHosts = new ArrayList<>();   // 新

// UserService
private void assertHostAllowed(String url) {
    if (url == null || url.isBlank()) return;
    var allowed = new HashSet<String>();
    allowed.addAll(securityProperties.getUpload().getAllowedAssetHosts());
    allowed.addAll(securityProperties.getUpload().getSelfHosts());
    if (allowed.isEmpty()) {
        // 改变：空白名单语义不再"全放行"，而是"仅本站存储域名"
        throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "未配置允许的资产域名");
    }
    String host = URI.create(url).getHost();
    if (host == null || !allowed.contains(host)) {
        throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "URL 域名不在允许列表内");
    }
}
```

#### 关键决策

| 决策 | 选项 | 选择 | 理由 |
|---|---|---|---|
| `StorageService.upload` 签名 | (a) 增 size 参数 (b) 改用 MultipartFile (c) byte[] | a | 保持流式语义；不污染抽象层；支持大文件。 |
| 未注册扩展名 | (a) 静默放行 (b) 拒绝 (c) WARN + 放行 | b | 防御纵深，新增扩展名时强制 EXT_TO_MIMES 同步。 |
| 头像 URL 颁发 | (a) presigned (b) 永久 public ACL (c) 站内代理签名 | a + c | minio/oss 用 presigned；local 用站内代理。 |
| Tika 文件名 hint | (a) 传 (b) 不传 | b | magic bytes 已经足够，文件名 hint 反引入误导风险。 |

#### 影响面
- **破坏性 API**：`StorageService.upload` 签名变更，所有调用方需要适配（`ResourceService`、`UserController`）。
- **历史数据**：MinIO 已有的截断文件无法自动修复，由运维识别后重传。

#### 测试要点
- **fix-checking**：上传 5MB / 20MB PDF → MinIO 模式下 SHA-256 与原文件一致；上传 PHP 文件改名 .png → 黑名单拒绝；上传 .txt（未注册扩展名）→ 直接拒绝；头像 URL 返回的 `<img>` 加载成功。
- **preservation-checking**：白名单内扩展名上传正常；去重逻辑（基于 SHA-256）保留。

#### 覆盖漏洞
- **6** MinIO available 截断
- **15** profile asset URL 错误
- **24** MimeTypeValidator 静默放行


---

### 主题 5：限流与暴力破解

#### 现状概述
- `RateLimitInterceptor` 用 `request.getRequestURI()` 做 key，path variable 端点按 ID 分桶。
- `RateLimitProperties#excludePatterns` 默认值含 `/api/v1/auth/login`，与 yml 替换语义结合后是定时炸弹。
- `EmailVerificationCodeService#isRateLimited` Redis 异常时 fail-open。
- 私信 / 浏览计数 / 高成本端点缺单独配额。

#### 目标状态 — `RouteTemplateExtractor`

```java
@Component
public class RouteTemplateExtractor {

    /** 从 HandlerMapping 拿到当前请求的路由模板。失败时返回 raw URI 但同时让限流走更严格的 fallback 分支。 */
    public ExtractResult extract(HttpServletRequest request) {
        String template = (String) request.getAttribute(
                HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (template != null && !template.isBlank()) {
            return new ExtractResult(template, true);
        }
        return new ExtractResult(request.getRequestURI(), false);
    }

    public record ExtractResult(String key, boolean isTemplate) {}
}
```

#### 目标状态 — `RateLimitInterceptor` 改造

```java
@Override
public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (!properties.isEnabled()) return true;

    var extracted = routeTemplateExtractor.extract(request);
    String routeKey = extracted.key();
    String method = request.getMethod();
    String path = request.getRequestURI();

    // 排除路径检查 — 但敏感前缀不可被排除（漏洞 10）
    if (isExcluded(path) && !isSensitivePath(path)) {
        return true;
    }

    String endpointKey = method + " " + routeKey;
    RateLimitProperties.LimitConfig config = properties.getOverrides().get(endpointKey);

    String rateLimitKey;
    if (StpUtil.isLogin()) {
        long userId = StpUtil.getLoginIdAsLong();
        rateLimitKey = "rate_limit:user:" + userId + ":" + routeKey;
        if (config == null) config = properties.getAuthenticated();
    } else {
        String ip = trustedProxyResolver.resolve(request);
        rateLimitKey = "rate_limit:ip:" + ip + ":" + routeKey;
        if (config == null) config = properties.getAnonymous();
    }

    // 模板提取失败时 max-requests 减半作为兜底
    if (!extracted.isTemplate()) {
        config = new RateLimitProperties.LimitConfig(
                Math.max(1, config.getMaxRequests() / 2),
                config.getWindowSeconds());
    }

    long retryAfter = isSensitivePath(path)
            ? rateLimiter.tryAcquireFailClosed(rateLimitKey, config.getMaxRequests(), config.getWindowSeconds())
            : rateLimiter.tryAcquire(rateLimitKey, config.getMaxRequests(), config.getWindowSeconds());

    if (retryAfter > 0) {
        securityMetrics.rateLimit429(routeKey);
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(retryAfter));
        // ... 原有响应逻辑
        return false;
    }
    return true;
}
```

#### 目标状态 — 默认值修正与启动校验

`RateLimitProperties`：

```java
private List<String> excludePatterns = List.of("/actuator/**");  // 不再含 /api/v1/auth/login
```

`SecurityStartupValidator#validateRateLimitExcludePatterns`：

```java
private static final Set<String> SENSITIVE_PATH_PREFIXES = Set.of(
        "/api/v1/auth/login",
        "/api/v1/auth/register",
        "/api/v1/auth/forgot-password",
        "/api/v1/auth/reset-password",
        "/api/v1/auth/ws-ticket",
        "/api/v1/ai/"
);

private void validateRateLimitExcludePatterns() {
    for (String pattern : rateLimitProperties.getExcludePatterns()) {
        for (String sensitive : SENSITIVE_PATH_PREFIXES) {
            if (pattern.startsWith(sensitive)
                    || sensitive.startsWith(pattern.replace("/**", ""))) {
                throw new IllegalStateException(
                        "敏感路径不可被加入 rate-limit.exclude-patterns: " + pattern);
            }
        }
    }
}
```

#### 目标状态 — `EmailVerificationCodeService` fail-closed + 常量时间比较

```java
private boolean isRateLimited(String key) {
    try {
        String countStr = stringRedisTemplate.opsForValue().get(key);
        if (countStr == null) return false;
        return Integer.parseInt(countStr) >= emailProperties.getRateLimitMaxRequests();
    } catch (NumberFormatException e) {
        return true;
    } catch (Exception e) {
        log.error("Email code rate limit check failed (fail-closed): {}", e.getMessage());
        // 改变：fail-closed
        throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE);
    }
}

public void verifyAndConsume(long tenantId, String email, EmailCodeScene scene, String inputCode) {
    String normalizedEmail = normalizeEmail(email);
    String key = codeKey(tenantId, scene, normalizedEmail);
    String stored = stringRedisTemplate.opsForValue().get(key);
    if (stored == null || inputCode == null) {
        throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "验证码无效或已过期");
    }
    // 改变：常量时间比较
    byte[] a = stored.getBytes(StandardCharsets.UTF_8);
    byte[] b = inputCode.trim().getBytes(StandardCharsets.UTF_8);
    if (!MessageDigest.isEqual(a, b)) {
        throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "验证码无效或已过期");
    }
    stringRedisTemplate.delete(key);
}
```

新增 IP 维度计数：

```java
private void checkAndIncrementIpRate(String ip, EmailCodeScene scene) {
    String key = "email_code_rate_ip:" + ip + ":" + scene.name().toLowerCase();
    Long count = stringRedisTemplate.opsForValue().increment(key);
    if (count != null && count == 1) {
        stringRedisTemplate.expire(key, 1, TimeUnit.MINUTES);
    }
    if (count != null && count > 3) {
        throw new BusinessException(ErrorCode.RATE_LIMITED.getCode(), "请求过于频繁");
    }
}
```

#### 目标状态 — `PostViewDeduper`

```java
@Component
@RequiredArgsConstructor
public class PostViewDeduper {

    private static final long DEDUP_TTL_SECONDS = 30 * 60;  // 30 分钟
    private final StringRedisTemplate redis;

    public boolean shouldCount(long postId, Long userId, String ip) {
        String key = userId != null
                ? "post_view:" + postId + ":u:" + userId
                : "post_view:" + postId + ":ip:" + ip;
        Boolean ok = redis.opsForValue().setIfAbsent(key, "1",
                DEDUP_TTL_SECONDS, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(ok);
    }
}
```

`PostService#viewPost` 改造：

```java
public PostVO viewPost(Long id) {
    Post post = postMapper.selectById(id);
    // ... 校验
    Long currentUserId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
    String role = currentUserId == null ? null : (String) StpUtil.getSession().get("role");
    String ip = trustedProxyResolver.resolve(httpRequest);

    if (currentUserId != null
            && !"TENANT_ADMIN".equals(role)
            && !"SUPER_ADMIN".equals(role)
            && !post.getAuthorId().equals(currentUserId)
            && postViewDeduper.shouldCount(id, currentUserId, ip)) {
        if (postMapper.incrementViewCount(id) > 0) {
            post.setViewCount((post.getViewCount() == null ? 0 : post.getViewCount()) + 1);
        }
    }
    return toVO(post, currentUserId);
}
```

#### 关键决策

| 决策 | 选项 | 选择 | 理由 |
|---|---|---|---|
| 路由模板提取失败处理 | (a) 抛错 (b) 退化但更严格 (c) 沿用 raw URI | b | 不希望某条匿名路径意外触发 5xx，但要让兜底更严防止滥用。 |
| 敏感路径 + exclude 冲突 | (a) 启动期阻断 (b) 运行时忽略 | c 二者兼有 | 双保险。 |
| 验证码常量时间比较 | (a) `equals` (b) `MessageDigest.isEqual` | b | 标准做法。 |
| 浏览计数去重 | (a) Redis SETNX (b) DB 唯一索引 | a | DB 写入会成为热点。 |

#### 影响面
- override key 字符串如果被运维硬编码 raw URI 会失效；仓库现有 yml 均用字面端点，无影响。
- Redis 增加 `post_view:*` 键空间（30 分钟 TTL，可控）。

#### 测试要点
- **fix-checking**：对 `/api/v1/posts/1`、`/api/v1/posts/2` 各发 200 次请求 → 第 201 次起两端点都 429（共享桶）；启动时 yml 写入 `exclude-patterns: [/api/v1/auth/login]` → 启动失败；Redis 故障时 `/auth/email-code` 返回 503；同一用户对同一帖子刷 100 次详情页 → view_count 仅 +1。
- **preservation-checking**：现有 override 端点限流仍生效；dev profile 默认值不阻断启动。

#### 覆盖漏洞
- **7** 限流 key 含 path variable
- **10** rate-limit exclude 默认值
- **11** 邮箱码 fail-open + 时序
- **16**（部分）私信限流配置
- **21** 浏览计数去重


---

### 主题 6：多租户隔离纵深

#### 现状概述
- `MyBatisPlusConfig#TENANT_IGNORE_TABLES` 写死，新增表时容易漏。
- `MeiliSearchClient.search` 在 `tenantId == null` 时静默不加 filter。
- `MultiTenantResolver` 在 session 与子域名同时存在时不校验一致性。
- `TenantService.toggleStatus` 不主动 evict 缓存。
- `DashboardVO` 不含 `tenantId/tenantCode`。

#### 目标状态 — `TenantStartupValidator` 扩展

```java
@Component
@Order(10)
@RequiredArgsConstructor
public class TenantStartupValidator implements ApplicationRunner {

    private final DataSource dataSource;
    private final TenantProperties props;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        validateStandaloneTenantExists();
        validateIgnoreTablesHaveNoTenantId();   // 新
        validateBusinessTablesHaveTenantId();   // 新
    }

    /** ignore-tables 中的表必须确实没有 tenant_id 列。 */
    private void validateIgnoreTablesHaveNoTenantId() throws SQLException {
        Set<String> ignoreTables = MyBatisPlusConfig.TENANT_IGNORE_TABLES;
        try (Connection conn = dataSource.getConnection()) {
            for (String table : ignoreTables) {
                if (hasColumn(conn, table, "tenant_id")) {
                    throw new IllegalStateException(
                            "表 " + table + " 在 TENANT_IGNORE_TABLES 中但仍有 tenant_id 列");
                }
            }
        }
    }

    /** 业务表（非 ignore）应有 tenant_id 列；缺失仅 WARN，由开发审视。 */
    private void validateBusinessTablesHaveTenantId() throws SQLException {
        Set<String> ignoreTables = MyBatisPlusConfig.TENANT_IGNORE_TABLES;
        try (Connection conn = dataSource.getConnection();
             ResultSet rs = conn.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String table = rs.getString("TABLE_NAME").toLowerCase();
                if (ignoreTables.contains(table)
                        || table.startsWith("flyway_")
                        || table.startsWith("schema_")) continue;
                if (!hasColumn(conn, table, "tenant_id")) {
                    log.warn("表 {} 没有 tenant_id 列，请确认是否应加入 TENANT_IGNORE_TABLES", table);
                }
            }
        }
    }

    private boolean hasColumn(Connection conn, String table, String column) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, table, column)) {
            return rs.next();
        }
    }
}
```

#### 目标状态 — `MeiliSearchClient.search` tenantId 强制

```java
public List<Map<String, Object>> search(String index, String query, int limit, Long tenantId) {
    if (!active) return List.of();
    if (tenantId == null) {
        log.error("MeiliSearch search called without tenantId, refusing to search index={}", index);
        securityMetrics.tenantViolation("missing_tenant_in_search");
        return List.of();   // 拒绝跨租户搜索
    }
    // ... 原有逻辑
}

// 删除旧重载 search(String, String, int)
```

#### 目标状态 — `MultiTenantResolver` 一致性校验

```java
@Override
public ResolutionResult resolve(HttpServletRequest request) {
    Long sessionTenantId = null;
    if (StpUtil.isLogin()) {
        Object tid = StpUtil.getSession().get("tenantId");
        if (tid instanceof Number n) sessionTenantId = n.longValue();
    }

    Long subdomainTenantId = resolveBySubdomain(request);
    Long headerTenantId = resolveByHeader(request);

    if (sessionTenantId != null) {
        // 已认证：session 优先，但若同时有子域名且不一致 → 拒绝
        if (subdomainTenantId != null && !subdomainTenantId.equals(sessionTenantId)) {
            tenantAuditService.recordViolation(sessionTenantId, request,
                    "session_subdomain_mismatch",
                    "session=" + sessionTenantId + " subdomain=" + subdomainTenantId);
            throw new TenantNotResolvedException(TenantNotResolvedException.Reason.TENANT_MISMATCH);
        }
        return new ResolutionResult(sessionTenantId,
                ResolutionResult.Source.SA_TOKEN_SESSION, cache.getCode(sessionTenantId));
    }

    // 未认证：子域名优先 → header fallback
    if (subdomainTenantId != null) {
        return new ResolutionResult(subdomainTenantId, Source.SUBDOMAIN, cache.getCode(subdomainTenantId));
    }
    if (headerTenantId != null && props.isAllowHeaderFallback()) {
        return new ResolutionResult(headerTenantId, Source.HEADER, cache.getCode(headerTenantId));
    }
    throw new TenantNotResolvedException(TenantNotResolvedException.Reason.NO_RESOLVER_MATCHED);
}

private Long resolveBySubdomain(HttpServletRequest request) {
    String host = request.getServerName();
    if (props.getRootDomain() == null || host == null
            || !host.endsWith("." + props.getRootDomain())) return null;
    String code = host.substring(0, host.length() - props.getRootDomain().length() - 1)
            .toLowerCase(Locale.ROOT);  // 规范化
    return cache.findIdByCode(code).orElse(null);
}
```

`TenantNotResolvedException.Reason` 增加 `TENANT_MISMATCH` 枚举值。

#### 目标状态 — `TenantService.toggleStatus` 加 evict + kickout

```java
@Transactional
public void toggleStatus(Long id) {
    Tenant tenant = tenantMapper.selectById(id);
    if (tenant == null) throw new BusinessException(40000, "租户不存在");
    int newStatus = tenant.getStatus() == 1 ? 0 : 1;
    tenant.setStatus(newStatus);
    tenantMapper.updateById(tenant);

    // 缓存立即失效
    activeTenantCache.evict(id, tenant.getCode());

    // 停用时把该租户全部活跃用户踢下线
    if (newStatus == 0) {
        kickoutTenantUsers(id);
    }
}

private void kickoutTenantUsers(long tenantId) {
    List<Long> userIds = userMapper.selectList(
            new LambdaQueryWrapper<User>()
                    .eq(User::getStatus, 1)
                    .eq(User::getTenantId, tenantId))
            .stream().map(User::getId).toList();
    for (Long uid : userIds) {
        try { StpUtil.kickout(uid); } catch (Exception ignored) {}
    }
    log.info("Kicked out {} users from tenant {}", userIds.size(), tenantId);
}
```

#### 目标状态 — `DashboardVO` 增加 tenantId/tenantCode

```java
@Data
@Builder
public class DashboardVO {
    private Long tenantId;       // 新
    private String tenantCode;   // 新
    private long userCount;
    // ...
}

// AdminController#dashboard
public R<DashboardVO> dashboard() {
    Long tid = TenantContext.getTenantId();
    String code = activeTenantCache.getCode(tid);
    return R.ok(DashboardVO.builder()
            .tenantId(tid)
            .tenantCode(code)
            // ... 现有字段
            .build());
}
```

#### 关键决策

| 决策 | 选项 | 选择 | 理由 |
|---|---|---|---|
| 启动期 schema 校验 | (a) 应用层 (b) flyway/liquibase | a | 项目未启用 flyway，应用层最直接。 |
| MeiliSearch tenantId 缺失处理 | (a) 抛错 (b) 返回空 | b + 监控 | 抛错可能让搜索 5xx 影响业务，返回空 + 告警更稳妥。 |
| 子域名 vs session 不一致 | (a) 拒绝 (b) 以 session 为准并审计 | a | 视觉钓鱼风险高，直接拒绝。 |

#### 影响面
- 业务表新增时如果忘了 tenant_id，应用启动只 WARN 不阻断；ignore-tables 校验抛错。
- DashboardVO 新增字段，前端兼容。

#### 测试要点
- **fix-checking**：把 `audit_logs` 加进 TENANT_IGNORE_TABLES → 启动失败；用户登录 tenant=1，伪造 Host 头 `tenant2.campusforum.com` → 拒绝；搜索 service 在线程池中调用 → tenantId 缺失返回空；停用租户后 5 秒内该租户用户访问 API → 401。
- **preservation-checking**：standalone 模式不受影响；已认证用户子域名访问自己租户正常。

#### 覆盖漏洞
- **14** dashboard 范围 + ignore-tables 巡检
- **19** 租户停用缓存
- **22** MeiliSearch tenantId 缺失
- **25** 子域名 vs session 一致性


---

### 主题 7：AI 与 SSRF 纵深

#### 现状概述
- `OpenAiCompatService` 是 `@Service @ConditionalOnProperty(ai.provider=openai)`，构造器 `@Value("${ai.api-key}")` 注入全局 key，与 `TenantAwareAiService` 形成混乱。
- `TenantAwareAiService#delegate` 每次 `new OpenAiCompatService(...)`，且解密失败时静默降级 mock。
- `SafeHttpClient` 没禁 redirect，DNS 重绑定可借 302 绕过。

#### 目标状态 — `OpenAiCompatService` 解 Bean 化

```java
package com.campusforum.ai.service;

// 移除 @Service / @ConditionalOnProperty
public class OpenAiCompatService implements AiService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;
    private final String model;

    OpenAiCompatService(String baseUrl, String apiKey, String model) {  // package-private
        this.restTemplate = SafeHttpClient.build(8000, 30000);
        this.objectMapper = new ObjectMapper();
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.apiKey = apiKey;
        this.model = (model == null || model.isBlank()) ? "deepseek-chat" : model;
    }
    // ... 其余逻辑保持
}
```

#### 目标状态 — `TenantAwareAiService` 缓存 + fail-loud

```java
@Primary
@Service
@RequiredArgsConstructor
public class TenantAwareAiService implements AiService {

    private final TenantService tenantService;
    private final MockAiService mockAiService;
    private final AuditLogService auditLogService;
    private final SecurityMetrics securityMetrics;

    /** 按 tenantId 缓存 OpenAiCompatService 实例；updateAiConfig 时 evict。 */
    private final Map<Long, AiClientHolder> clientCache = new ConcurrentHashMap<>();

    private record AiClientHolder(String fingerprint, OpenAiCompatService client) {}

    @Override
    public String summarize(String content) { return delegate().summarize(content); }
    // ... 其他方法

    private AiService delegate() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) return mockAiService;

        Map<String, String> config;
        try {
            config = tenantService.resolveAiCredentials(tenantId);
        } catch (CryptoException e) {
            // 改变：解密失败显式审计 + 让上层感知，不再静默降级
            auditLogService.log("AI_DECRYPT_FAIL", "tenant", tenantId, "apiKey decrypt failed");
            securityMetrics.cryptoDecryptFailed();
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }

        String provider = config.get("provider");
        String apiKey = config.get("apiKey");
        String baseUrl = config.get("baseUrl");
        String model = config.get("model");

        if (!"openai".equalsIgnoreCase(provider) || apiKey == null || apiKey.isBlank()) {
            return mockAiService;
        }

        try {
            PrivateNetworkValidator.requirePublic(baseUrl, true);
        } catch (IllegalArgumentException ex) {
            auditLogService.log("AI_SSRF_BLOCKED", "tenant", tenantId, ex.getMessage());
            securityMetrics.ssrfBlocked("validator");
            return mockAiService;
        }

        // 基于 baseUrl + apiKey + model 生成指纹，配置变更时让缓存失效
        String fingerprint = baseUrl + "|"
                + Hashing.sha256().hashString(apiKey, StandardCharsets.UTF_8) + "|" + model;
        AiClientHolder holder = clientCache.compute(tenantId, (k, existing) -> {
            if (existing != null && existing.fingerprint.equals(fingerprint)) {
                return existing;
            }
            return new AiClientHolder(fingerprint, new OpenAiCompatService(baseUrl, apiKey, model));
        });
        return holder.client();
    }

    /** 配置变更时由 TenantService.updateAiConfig 主动调用。 */
    public void evict(long tenantId) {
        clientCache.remove(tenantId);
    }
}
```

#### 目标状态 — `SafeHttpClient` 禁 redirect

```java
public final class SafeHttpClient {

    public static RestTemplate build(int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod)
                    throws IOException {
                super.prepareConnection(connection, httpMethod);
                // 禁用自动 redirect，避免 302 跳转到内网
                connection.setInstanceFollowRedirects(false);
                String host = connection.getURL().getHost();
                assertHostNotPrivate(host);
            }
        };
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        // 全局兜底
        HttpURLConnection.setFollowRedirects(false);

        return new RestTemplate(factory);
    }
}
```

`tenantService.updateAiConfig` 调用 evict：

```java
@Transactional
public void updateAiConfig(Long tenantId, ...) {
    // ... 原有逻辑
    tenantMapper.updateById(t);
    tenantAwareAiService.evict(tenantId);   // 新
}
```

#### 关键决策

| 决策 | 选项 | 选择 | 理由 |
|---|---|---|---|
| OpenAiCompatService Bean 化处理 | (a) `@Scope("prototype")` (b) 移除 `@Service` (c) 删除 `@ConditionalOnProperty` | b | 不依赖容器，由 TenantAwareAiService 控制生命周期。 |
| AI 解密失败处理 | (a) 静默降级 (b) 抛错 (c) 审计 + 抛错 | c | 让管理员能立刻发现配置异常。 |
| Redirect 处理 | (a) 默认禁用 (b) 允许但每跳验证 | a | 当前 AI 调用链路无 redirect 需求。 |

#### 影响面
- AI 接口在解密失败时返回 50001 而非 mock；前端需展示错误提示。
- 缓存内存：每租户一个 client 实例，远小于现状每请求 new。
- 配置变更即时生效（evict 立即），无须重启。

#### 测试要点
- **fix-checking**：故意破坏 ai_config JSON 让 decrypt 抛异常 → AI 接口返回 50001 + 审计日志有 AI_DECRYPT_FAIL；AI baseUrl 公网域名返回 302 跳转到 169.254.169.254 → 调用方收到 302 状态而非元数据响应；直接 `@Autowired OpenAiCompatService` 失败（Bean 不存在）。
- **preservation-checking**：正常 AI 配置链路工作；mock provider 切换正常。

#### 覆盖漏洞
- **12** OpenAI 全局 key Bean
- **23** SafeHttpClient redirect 校验


---

### 主题 8：XSS / 输入净化 / 敏感数据

#### 现状概述
- `pom.xml` 引入 `owasp-java-html-sanitizer` 但全代码无调用方。
- `frontend/src/utils/mention.ts#renderMentions` 用字符串拼接 HTML。
- `SearchService#searchUsers` LIKE 命中 `email` / `studentNo`。
- `ExportController` 仅 `tenant:dashboard` 守门，导出全字段 PII。
- `AdminUserController` 多接口用 `Map<String, ...>` 接收。
- `PostService#create` 拼接 quotePost 时未做 Markdown 转义。
- `SensitiveWordService.getRiskLevel` 仅 `String#contains`。
- nickname 缺字符白名单。

#### 目标状态 — `HtmlSanitizerService`

```java
package com.campusforum.infra.sanitize;

@Service
public class HtmlSanitizerService {

    private static final PolicyFactory MARKDOWN_POLICY = Sanitizers.FORMATTING
            .and(Sanitizers.LINKS)
            .and(Sanitizers.BLOCKS)
            .and(Sanitizers.IMAGES)
            .and(Sanitizers.TABLES);

    private static final PolicyFactory COMMENT_POLICY = Sanitizers.FORMATTING
            .and(Sanitizers.LINKS);

    /** 帖子正文净化：保留 Markdown 转换后允许的标签，移除 script/onerror/javascript:。 */
    public String sanitizePost(String html) {
        if (html == null) return null;
        return MARKDOWN_POLICY.sanitize(html);
    }

    /** 评论正文净化。 */
    public String sanitizeComment(String html) {
        if (html == null) return null;
        return COMMENT_POLICY.sanitize(html);
    }
}
```

调用点：`PostService#create / updatePost`、`CommentService#create / updateComment`、`MessageService#send` 在写入 DB 前调用。

#### 目标状态 — 前端 `MentionText.vue` 重构

```vue
<script setup lang="ts">
import { computed } from 'vue';
import { parseMentions } from '@/utils/mention';

const props = defineProps<{ text: string }>();
const segments = computed(() => parseMentions(props.text));
</script>

<template>
  <span>
    <template v-for="(seg, i) in segments" :key="i">
      <RouterLink
        v-if="seg.mention"
        :to="{ path: '/search', query: { q: '@' + seg.mention } }"
        class="mention-link"
      >@{{ seg.mention }}</RouterLink>
      <template v-else>{{ seg.text }}</template>
    </template>
  </span>
</template>
```

删除 `mention.ts#renderMentions` 函数（HTML 字符串拼接）。

#### 目标状态 — `TextNormalizer`（敏感词预处理）

```java
package com.campusforum.infra.sanitize;

public final class TextNormalizer {

    private static final Pattern ZERO_WIDTH = Pattern.compile(
            "[\\u200B\\u200C\\u200D\\uFEFF\\u2060]");

    private TextNormalizer() {}

    /** NFKC + 移除零宽 + 全角转半角 + 小写。 */
    public static String normalize(String input) {
        if (input == null) return "";
        String s = Normalizer.normalize(input, Normalizer.Form.NFKC);
        s = ZERO_WIDTH.matcher(s).replaceAll("");
        s = toHalfWidth(s);
        return s.toLowerCase(Locale.ROOT);
    }

    private static String toHalfWidth(String s) {
        char[] chars = s.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (c == 0x3000) chars[i] = ' ';
            else if (c >= 0xFF01 && c <= 0xFF5E) chars[i] = (char) (c - 0xFEE0);
        }
        return new String(chars);
    }
}
```


`SensitiveWordService.getRiskLevel` 改造：

```java
public int getRiskLevel(String content) {
    if (content == null) return 0;
    String normalized = TextNormalizer.normalize(content);
    int maxLevel = 0;
    for (SensitiveWord sw : listAll()) {
        String word = TextNormalizer.normalize(sw.getWord());
        if (Boolean.TRUE.equals(sw.getIsRegex())) {
            if (Pattern.compile(word).matcher(normalized).find()) {
                maxLevel = Math.max(maxLevel, sw.getLevel());
            }
        } else if (normalized.contains(word)) {
            maxLevel = Math.max(maxLevel, sw.getLevel());
        }
    }
    return maxLevel;
}
```

DB 迁移 `V20260601_06`：`sensitive_words` 增加 `is_regex TINYINT NOT NULL DEFAULT 0` 列。

#### 目标状态 — `SearchService.searchUsers` 收紧

```java
private List<SearchResultVO> searchUsers(String keyword, Long cursor, int limit) {
    if (keyword == null || keyword.length() < 2) return List.of();
    // 拒绝邮箱 / 学号格式，避免按 PII 枚举
    if (keyword.contains("@") || keyword.matches("^\\d{8,}$")) {
        return List.of();
    }

    LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<>();
    qw.eq(User::getStatus, 1);
    qw.like(User::getNickname, keyword);   // 仅按 nickname
    if (cursor != null) qw.lt(User::getId, cursor);
    qw.orderByDesc(User::getId);
    qw.last("LIMIT " + limit);

    return userMapper.selectList(qw).stream().map(u -> SearchResultVO.builder()
            .type("USER")
            .id(u.getId())
            .title(u.getNickname())
            .description(u.getCollege() != null ? u.getCollege() : "")
            .author(PublicUserVO.builder()
                    .id(u.getId())
                    .nickname(u.getNickname())
                    .avatarUrl(u.getAvatarUrl())
                    .build())
            .build()).toList();
}
```

同时检查 `PublicUserVO`：仅保留 `id, nickname, avatarUrl, role, college`，移除 `studentNo` 与 `email` 字段（若存在）。


#### 目标状态 — Export 权限与脱敏

`AdminStpInterface.TENANT_ADMIN_PERMISSIONS` 增加：

```java
"tenant:export:users",
"tenant:export:posts",
"tenant:export:audit",
"tenant:export:reports"
```

`ExportController` 拆为 4 个细粒度方法：

```java
@PostMapping("/users")
@SaCheckPermission("tenant:export:users")
public void exportUsers(@RequestParam(defaultValue = "csv") String format,
                        @RequestParam(defaultValue = "false") boolean fullPii,
                        HttpServletResponse response) {
    if (fullPii && !"SUPER_ADMIN".equals(currentRole())) {
        throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    auditLogService.log("EXPORT_USERS", "user", null,
            "format=" + format + ", fullPii=" + fullPii);
    exportService.exportUsers(format, fullPii, response.getOutputStream());
}
```

`ExportService.exportUsers` 增加 `fullPii` 参数：默认 `false` 时对 email / studentNo 做掩码：

```java
private String maskEmail(String email) {
    if (email == null) return "";
    int at = email.indexOf('@');
    if (at <= 1) return "***";
    return email.charAt(0) + "***" + email.substring(at);
}

private String maskStudentNo(String no) {
    if (no == null || no.length() < 4) return "***";
    return no.substring(0, 4) + "***" + no.substring(no.length() - 1);
}
```

行数上限：`ExportService` 增加 `MAX_ROWS = 50_000` 常量，循环计数到达即抛 `BATCH_SIZE_EXCEEDED`。

限流配置已在 Architecture 给出（每端点 1/min）。


#### 目标状态 — Admin DTO 化

```java
@Data
public class ChangeRoleRequest {
    @NotBlank
    @Pattern(regexp = "^(USER|TENANT_ADMIN)$", message = "无效角色")
    private String role;
}

@Data
public class BatchUpdateUserStatusRequest {
    @NotEmpty
    @Size(max = 100, message = "单次最多 100 条")
    private List<@NotNull Long> ids;

    @NotNull
    @Min(0) @Max(1)
    private Integer status;
}
```

`AdminUserController` 改造：

```java
@PutMapping("/{id}/role")
@SaCheckPermission("tenant:user:role")
public R<Void> changeRole(@PathVariable Long id, @Valid @RequestBody ChangeRoleRequest req) {
    userService.changeRole(id, req.getRole());
    auditLogService.log("USER_ROLE_CHANGE", "user", id, "role=" + req.getRole());
    return R.ok();
}

@PutMapping("/batch-status")
@SaCheckPermission("tenant:user:ban")
public R<Void> batchSetStatus(@Valid @RequestBody BatchUpdateUserStatusRequest req) {
    Set<Long> uniqueIds = new LinkedHashSet<>(req.getIds());
    userService.batchSetStatus(uniqueIds, req.getStatus());
    auditLogService.log("USER_BATCH_STATUS", "user", null,
            "status=" + req.getStatus() + ", count=" + uniqueIds.size());
    return R.ok();
}
```

`UserService.batchSetStatus` 整体事务化：

```java
@Transactional
public void batchSetStatus(Set<Long> ids, int status) {
    for (Long id : ids) {
        if (status == 0) banUser(id);
        else unbanUser(id);
    }
}
```

`MessageController#send` 同样改用 `SendMessageRequest` DTO：

```java
@Data
public class SendMessageRequest {
    @NotNull private Long receiverId;
    @Size(max = 2000) private String content;
    @Pattern(regexp = "^$|^https?://.+") @Size(max = 500)
    private String imageUrl;
}
```

`MessageService#send` 增加跨租户显式断言 + 内容净化 + 敏感词。


#### 目标状态 — `MarkdownEscaper` + nickname 白名单

```java
package com.campusforum.post.service;

public final class MarkdownEscaper {
    private MarkdownEscaper() {}

    /** 转义 Markdown 控制字符，避免引用块拼接被恶意 nickname/title 注入。 */
    public static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("`", "\\`")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("#", "\\#")
                .replace("+", "\\+")
                .replace("-", "\\-")
                .replace("!", "\\!")
                .replace(">", "\\>");
    }
}
```

`PostService#create` 在引用块拼接前调用：

```java
String quotedName = MarkdownEscaper.escape(quotedAuthor.getNickname());
String quotedTitle = MarkdownEscaper.escape(quoted.getTitle());
String quotedBody = MarkdownEscaper.escape(quoted.getContent()).replace("\n", "\n> ");
content = "> **" + quotedName + "** 的原帖：\n> "
        + (quotedTitle != null ? "**" + quotedTitle + "**\n> " : "")
        + quotedBody + "\n\n" + (content != null ? content : "");
```

DTO 增加 nickname 白名单：

```java
// RegisterRequest, UpdateProfileRequest
@NotBlank
@Size(max = 32)
@Pattern(regexp = "^[\\w\\u4e00-\\u9fa5\\- ]{1,32}$",
        message = "昵称仅允许中英文/数字/下划线/连字符/空格")
private String nickname;
```

#### 关键决策

| 决策 | 选项 | 选择 | 理由 |
|---|---|---|---|
| Sanitize 时机 | (a) 写入前 (b) 渲染前 | a | 写入时净化让 DB 内容可信，避免每次渲染重复计算与不一致风险。 |
| Markdown 转义粒度 | (a) 全部控制字符 (b) 仅 `>`、`*` | a | 完全转义防御范围更广。 |
| Export 字段脱敏默认 | (a) 默认明文 (b) 默认脱敏 + fullPii 显式开启 | b | 遵循"最小披露"原则。 |
| 用户搜索字段 | (a) 仅 nickname (b) nickname + studentNo（管理员）| a + 管理员走专用接口 | 公共搜索严格收紧。 |

#### 影响面
- 私信、评论、帖子、admin role/batch 接口的 request body 结构变严格（缺字段 / 非法字符返回 400）。
- 数据库：`sensitive_words.is_regex`、`messages.ai_risk_level` 列新增。
- 前端：`MentionText.vue` 重构。

#### 测试要点
- **fix-checking**：帖子内容 `<script>alert(1)</script>` → DB 落库时已被 sanitizer 移除；用户昵称含 `**` 或 `>` → 注册 / 改名 400；搜索 `@163.com` → 返回空；敏感词字典含 "测试"，发帖含带零宽空格的"测试"→ 命中 level；普通管理员调 `/admin/export/users?fullPii=true` → 403；管理员 `changeRole` body `{}` → 400（不再 500）。
- **preservation-checking**：正常 markdown 帖子（标题、正文、引用）渲染不退化；现有 mention 渲染样式保持。

#### 覆盖漏洞
- **9** 搜索泄漏邮箱
- **13** 导出权限粗
- **17** admin Map 接收
- **18** HTML 净化与 mention v-html
- **20** 引用块未转义 + nickname 白名单
- **27** 敏感词归一化 + 正则


---

### 主题 9：审计与可观测性

#### 现状概述
- `AuditLogService` 通过 request-scoped `HttpServletRequest` 注入解析 IP，异步路径会失败。
- `GlobalExceptionHandler` 用字符串匹配 `"TenantContext is null"` 做兜底分类。
- `R.traceId` 是每次新生成的 UUID，与日志 MDC 不关联。
- 全代码无 Micrometer Counter 埋点。

#### 目标状态 — `AuditContext` + `AuditLogService` 签名重构

```java
package com.campusforum.infra.audit;

@Getter
@Builder
public class AuditContext {
    private final Long operatorId;
    private final Long tenantId;
    private final String clientIp;
    private final String userAgent;

    public static AuditContext from(HttpServletRequest req,
                                    TrustedProxyResolver resolver,
                                    Long operatorId,
                                    Long tenantId) {
        return AuditContext.builder()
                .operatorId(operatorId)
                .tenantId(tenantId)
                .clientIp(resolver.resolve(req))
                .userAgent(req.getHeader("User-Agent"))
                .build();
    }
}
```

`AuditLogService` 接受显式 context：

```java
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogMapper auditLogMapper;

    @Transactional
    public void log(AuditContext ctx, String action, String targetType, Long targetId, String detail) {
        AuditLog entry = new AuditLog();
        entry.setOperatorId(ctx.getOperatorId());
        entry.setAction(action);
        entry.setTargetType(targetType);
        entry.setTargetId(targetId);
        entry.setDetail(detail);
        entry.setIpAddress(ctx.getClientIp());
        entry.setUserAgent(ctx.getUserAgent());
        auditLogMapper.insert(entry);
    }

    /** 兼容旧调用方：用 RequestContextHolder + MDC 兜底。 */
    @Deprecated
    @Transactional
    public void log(String action, String targetType, Long targetId, String detail) {
        log(currentRequestContext(), action, targetType, targetId, detail);
    }

    private AuditContext currentRequestContext() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes sra)) {
            return AuditContext.builder()
                    .operatorId(parseLong(MDC.get("userId")))
                    .tenantId(parseLong(MDC.get("tenantId")))
                    .clientIp(MDC.get("clientIp"))
                    .build();
        }
        // ... 解析 request
        return AuditContext.from(sra.getRequest(), trustedProxyResolver,
                StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null,
                TenantContext.getTenantId());
    }
}
```


#### 目标状态 — `MdcTraceIdFilter`

```java
package com.campusforum.infra.web;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcTraceIdFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String inbound = req.getHeader(HEADER);
        String traceId = (inbound != null && inbound.matches("^[a-zA-Z0-9-]{8,64}$"))
                ? inbound
                : UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        try {
            MDC.put("traceId", traceId);
            res.setHeader(HEADER, traceId);
            chain.doFilter(req, res);
        } finally {
            MDC.remove("traceId");
            MDC.remove("tenantId");
            MDC.remove("userId");
        }
    }
}
```

`TenantResolutionFilter` 在解析后写入 MDC：`MDC.put("tenantId", String.valueOf(result.tenantId()))`。

`R` 构造时读取 MDC：

```java
private R(int code, String message, T data) {
    this.code = code;
    this.message = message;
    this.data = data;
    String mdcTrace = MDC.get("traceId");
    this.traceId = mdcTrace != null ? mdcTrace
            : UUID.randomUUID().toString().substring(0, 8);
}
```

logback pattern：`%d{ISO8601} [%thread] [%X{traceId} %X{tenantId} %X{userId}] %-5level %logger{36} - %msg%n`

#### 目标状态 — `GlobalExceptionHandler` 异常类型化

```java
package com.campusforum.tenant;

public class TenantContextMissingException extends RuntimeException {
    public TenantContextMissingException(String detail) {
        super("TenantContext is null: " + detail);
    }
}
```

`MyBatisPlusConfig#getTenantId()` 抛 `TenantContextMissingException` 替代 `IllegalStateException`。

`GlobalExceptionHandler` 替换字符串匹配：

```java
@ExceptionHandler(TenantContextMissingException.class)
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public R<?> handleTenantContextMissing(TenantContextMissingException e) {
    log.error("TenantContext missing: {}", e.getMessage());
    return R.fail(ErrorCode.SERVICE_UNAVAILABLE);
}

@ExceptionHandler(IllegalStateException.class)
public ResponseEntity<R<?>> handleIllegalState(IllegalStateException e) {
    log.error("IllegalStateException: {}", e.getMessage(), e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(R.fail(ErrorCode.INTERNAL_ERROR));
}
```


#### 目标状态 — `SecurityMetrics` 集中埋点

```java
package com.campusforum.infra.metrics;

@Component
@RequiredArgsConstructor
public class SecurityMetrics {

    private final MeterRegistry registry;

    /** v1 ECB 解密次数（按 tenantId 维度，便于评估迁移完成度）。 */
    public void cryptoDecryptLegacy(long tenantId) {
        registry.counter("crypto_decrypt_legacy_total",
                "tenant_id", String.valueOf(tenantId)).increment();
    }

    public void cryptoDecryptFailed() {
        registry.counter("crypto_decrypt_failed_total").increment();
    }

    public void ssrfBlocked(String stage) {
        registry.counter("ssrf_blocked_total", "stage", stage).increment();
    }

    public void mimeMismatch(String ext, String detected) {
        registry.counter("mime_mismatch_total",
                "ext", ext, "detected", detected).increment();
    }

    public void loginLockout503() {
        registry.counter("login_lockout_503_total").increment();
    }

    public void wsLegacyTokenUsed() {
        registry.counter("ws_legacy_token_used_total").increment();
    }

    public void tenantViolation(String reason) {
        registry.counter("tenant_violation_total", "reason", reason).increment();
    }

    public void rateLimit429(String routeTemplate) {
        registry.counter("rate_limit_429_total",
                "route", routeTemplate).increment();
    }

    public void sessionForcedLogout(String action) {
        registry.counter("session_forced_logout_total", "action", action).increment();
    }
}
```

依赖：`pom.xml` 增加 `micrometer-registry-prometheus`。`management.endpoints.web.exposure.include` 增加 `prometheus`，`nginx.conf` 增加 location 仅允许内网：

```
location ~ ^/actuator/prometheus$ {
    allow 10.0.0.0/8;
    allow 172.16.0.0/12;
    allow 192.168.0.0/16;
    allow 127.0.0.1;
    deny all;
    proxy_pass http://app:8080;
}
```

#### 关键决策

| 决策 | 选项 | 选择 | 理由 |
|---|---|---|---|
| AuditContext 注入方式 | (a) 显式参数 (b) ThreadLocal | a + 兼容旧签名 | 显式参数最稳，旧签名作废止过渡。 |
| TraceId 实现 | (a) 自实现 MDC Filter (b) Spring Cloud Sleuth | a | 项目无 Cloud 依赖；自实现成本低。 |
| Prometheus endpoint 暴露 | (a) 完全不暴露 (b) 内网放行 | b | 监控必备；nginx 控制源 IP。 |

#### 影响面
- 响应头 `X-Trace-Id` 新增；前端可上报排查问题时附带。
- 数据库：`audit_log.user_agent` 列新增。
- actuator 新增 prometheus endpoint；nginx 新增 location。

#### 测试要点
- **fix-checking**：异步线程调用 `auditLogService.log(ctx, ...)` 不再依赖 request；一次请求中应用日志、`R.traceId`、响应头 `X-Trace-Id` 三处 traceId 完全一致；prometheus endpoint 从外部 IP 访问 → 404；从内网 → 200。
- **preservation-checking**：现有审计日志写入路径继续工作；现有日志格式 pattern 升级后旧日志解析工具仍能匹配（按需协调）。

#### 覆盖漏洞
- **26** AuditLogService request 注入
- **28** GlobalExceptionHandler 字符串匹配
- **31** R.traceId 与 MDC 关联
- **32** 监控埋点


---

## Data Models

### 数据库迁移脚本一览

```
db/migrations/
├── V20260601_01__nickname_pattern.sql        (注释，应用层校验，无 DDL)
├── V20260601_02__messages_ai_risk_level.sql  (messages 增加 ai_risk_level 列)
├── V20260601_03__post_view_dedup.sql         (Redis 实现，无 DDL；本脚本仅占位)
├── V20260601_04__audit_log_extend.sql        (audit_log 增加 user_agent 列)
├── V20260601_05__resources_legacy_md5.sql    (resources.file_md5 注释 deprecated)
├── V20260601_06__sensitive_word_regex.sql    (sensitive_words 增加 is_regex 列)
└── V20260601_07__nickname_audit.sql          (查询非法 nickname 历史数据，仅 SELECT)
```

`V20260601_02`：

```sql
ALTER TABLE messages
  ADD COLUMN ai_risk_level TINYINT NOT NULL DEFAULT 0
  COMMENT '0=安全 1=疑似 2=违规'
  AFTER image_url;
```

`V20260601_04`：

```sql
ALTER TABLE audit_log
  ADD COLUMN user_agent VARCHAR(255) NULL AFTER ip_address;
ALTER TABLE audit_log
  ADD INDEX idx_audit_log_action_created (action, created_at);
```

`V20260601_06`：

```sql
ALTER TABLE sensitive_words
  ADD COLUMN is_regex TINYINT NOT NULL DEFAULT 0
  COMMENT '0=普通词 1=正则表达式（管理员需测试通过后启用）'
  AFTER level;
```

`V20260601_07`（仅供 SUPER_ADMIN 灰度执行，**非自动**）：

```sql
-- 用于发现历史 nickname 含非白名单字符的账号
SELECT id, nickname FROM users
WHERE nickname REGEXP '[^[:alnum:]\\u4e00-\\u9fa5 _-]'
  AND status = 1;
```

### 实体字段变更

| 实体 | 字段 | 变更 | 兼容期 |
|---|---|---|---|
| `Message` | `aiRiskLevel: int` | 新增 | 立即 |
| `AuditLog` | `userAgent: String` | 新增 | 立即（旧记录为 null） |
| `SensitiveWord` | `isRegex: Boolean` | 新增 | 立即（默认 false） |
| `User` | `nickname` | 校验更严 | 已有用户不强制改名 |
| `Resource` | `fileMd5: String` | 标记 `@Deprecated` | 历史数据完成迁移到 `fileSha256` 后删列 |

### 新增/修改 ConfigurationProperties

```java
// SecurityProperties
private Crypto crypto = new Crypto();
private Cors cors = new Cors();
private Upload upload = new Upload();
private WsTicket wsTicket = new WsTicket();
private Docs docs = new Docs();             // 新

@Data
public static class Docs {
    private List<String> enabledProfiles = List.of("dev", "test");
}

@Data
public static class Crypto {
    private String masterKey;
    private boolean legacyMode = false;
    private LocalDate legacyCutoverDate;     // 新
}

@Data
public static class WsTicket {
    private int ttlSeconds = 30;
    private boolean enforced = false;
    private LocalDate enforcedCutoverDate;   // 新
}

@Data
public static class Upload {
    private boolean realMimeCheck = true;
    private List<String> blockedExtensions = new ArrayList<>();
    private List<String> blockedMimeTypes = new ArrayList<>();   // 新
    private List<String> allowedAssetHosts = new ArrayList<>();
    private List<String> selfHosts = new ArrayList<>();          // 新
}
```


---

## Error Handling

### 新增/扩展异常类型

| 异常 | HTTP | ErrorCode | 触发场景 |
|---|---|---|---|
| `TenantContextMissingException` | 503 | SERVICE_UNAVAILABLE | TenantLineInnerInterceptor 取不到租户 |
| `CryptoException` | 500 | CRYPTO_FAILURE | AES-GCM / 旧 ECB 解密失败（已存在，扩展使用面） |
| `MimeMismatchException` | 400 | MIME_MISMATCH | 未注册扩展名 / 黑名单 MIME / 检测不一致 |
| `SSRFBlockedException` | 400 | SSRF_BLOCKED | validator 或 connect 阶段命中私网 |
| `BusinessException(ErrorCode.SERVICE_UNAVAILABLE)` | 503 | SERVICE_UNAVAILABLE | 限流敏感路径 fail-closed、邮件 fail-closed |
| `BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE)` | 500 | AI_SERVICE_UNAVAILABLE | AI 解密失败 |
| `BusinessException(ErrorCode.BATCH_SIZE_EXCEEDED)` | 400 | BATCH_SIZE_EXCEEDED | 批量操作超过 100 条 |

### 错误码新增

```java
// ErrorCode.java（新增）
TENANT_MISMATCH(40012, "租户上下文不一致"),
DOC_ACCESS_DENIED(40013, "接口文档不可访问"),
EXPORT_FORBIDDEN(40014, "无导出权限"),
WEAK_CONFIG(50011, "服务器配置不安全，请联系运维"),     // 仅启动期日志
```

### 防信息泄漏统一格则

- `GlobalExceptionHandler.handleException` 兜底仅返回 `INTERNAL_ERROR`，不附加 stack trace。
- 所有"用户不存在 / 密码错误 / token 无效"统一返回 `INVALID_CREDENTIALS`，避免侧信道枚举。
- 对管理员后台的内部错误**仍**返回脱敏文本，由审计日志和 `traceId` 协助定位。
- AI 上游错误统一脱敏（已实现，保持）。


---

## Testing Strategy

### 测试金字塔

| 层级 | 框架 | 用途 |
|---|---|---|
| 单元测试 | JUnit 5 + Mockito | DTO 校验、`HtmlSanitizerService`、`TextNormalizer`、`MarkdownEscaper`、`PrivateNetworkValidator`、`SecurityStartupValidator` 各分支 |
| 集成测试 | SpringBootTest + Testcontainers (MySQL + Redis) | 限流模板共享桶、跨租户访问拒绝、`MimeTypeValidator` 真实文件、`UserService` 密码改/重置后踢下线 |
| 端到端 | Vitest + Playwright（前端）/ MockMvc（后端） | XSS 净化、admin export PII 脱敏、WebSocket ticket cutover |
| 启动校验 | SpringBootTest with `@ActiveProfiles("prod")` | prod profile 配置缺失抛错 |

### 关键测试用例（命名建议）

```
backend/src/test/java/com/campusforum/security/
├── crypto/
│   ├── EcbCryptoUtilsTest.java                  # decrypt 失败抛异常，不返回原文
│   └── CryptoServiceLegacyMigrationTest.java    # 解密 v1 后异步重加密为 v2
├── startup/
│   ├── SecurityStartupValidatorProdTest.java    # prod profile + 弱默认值 → 启动失败
│   └── TenantStartupValidatorTest.java          # ignore-tables schema 校验
├── docs/
│   └── DocAccessFilterTest.java                 # prod 禁用、dev 内网放行
├── ratelimit/
│   ├── RouteTemplateExtractorTest.java
│   └── RateLimitInterceptorRouteTemplateIT.java # /posts/{id} 共享桶
├── ai/
│   ├── TenantAwareAiServiceCacheTest.java       # 配置变更后 evict
│   └── SafeHttpClientNoRedirectTest.java
├── tenant/
│   ├── MultiTenantResolverMismatchTest.java     # session vs subdomain 拒绝
│   └── ActiveTenantCacheEvictTest.java          # toggleStatus 立即失效
├── upload/
│   ├── MinioUploadLargeFileIT.java              # 20MB 不截断
│   └── MimeTypeValidatorBlocklistTest.java
├── xss/
│   ├── HtmlSanitizerServiceTest.java
│   └── MarkdownEscaperTest.java
├── audit/
│   ├── AuditContextAsyncTest.java
│   └── MdcTraceIdFilterTest.java
└── session/
    ├── PasswordChangeKickoutTest.java
    └── WsTicketEnforcedCutoverTest.java
```

### 兼容性回归

每次合入主线前必须跑：
- 后端：`mvn test`（含 Testcontainers 集成测试套）。
- 前端：`npm run test` + `npm run build`（vue-tsc 类型检查）。
- 启动冒烟：`docker compose -f deploy/docker-compose.yml up -d` + 健康检查 + 一组关键 API 探测。


---

## Migration Plan

### 灰度策略

加固分四批合并，每批包含一个 feature flag 用于紧急回滚：

| 批次 | 主题 | Feature Flag | 默认值 |
|---|---|---|---|
| 1 | 主题 1 + 主题 2 + 主题 9（监控埋点） | `CRYPTO_LEGACY_MODE`、`SPRINGDOC_ENABLED` | legacy=false / docs=false |
| 2 | 主题 3 + 主题 5 + 主题 8（敏感数据） | `WS_TICKET_ENFORCED`、`UPLOAD_REAL_MIME_CHECK`、`SANITIZER_ENABLED` | enforced=false / sanitizer=true |
| 3 | 主题 4 + 主题 6 + 主题 7 | `STORAGE_STAT_VERIFY`、`AI_DECRYPT_FAIL_LOUD` | 全 true |
| 4 | 主题 8（XSS 前端 + admin DTO） + 主题 9（traceId） | 无 flag，纯净化 | 立即生效 |

### 部署顺序与回滚

```
   ┌──────────────┐       ┌──────────────┐
   │  批次 1 部署  │  → 观察 7 天 →   │  批次 2 部署  │
   └──────────────┘       └──────────────┘
                                  ↓
              失败时通过 ENV 翻转 flag 即可回滚
                                  ↓
   ┌──────────────┐       ┌──────────────┐
   │  批次 3 部署  │  → 观察 14 天 →  │  批次 4 部署  │
   └──────────────┘       └──────────────┘
```

### 截止日期

- WS legacy token 强制 cutover：批次 2 部署后 30 天 → `WS_TICKET_ENFORCED=true` 默认。
- v1 ECB 兼容期 cutover：批次 1 部署后 90 天 → 删除 `EcbCryptoUtils`、删除 `CryptoService.decryptLegacyEcb`。
- 旧 `MentionParser.renderMentions` 函数：批次 4 合并后立即删除（无运行时 flag）。

### 数据迁移

- `messages.ai_risk_level`、`audit_log.user_agent`、`sensitive_words.is_regex` 默认值即可；无需 backfill。
- `users.nickname` 不强制改名；后台保留旧昵称的脏数据，但禁止新创建/改名引入非法字符。
- `resources.file_md5` 列**仅标记 deprecated**，待 100% 资源都有 `file_sha256` 后再删（后续小版本）。

### 兼容性矩阵

| 组件 | 旧行为 | 新行为 | 兼容期 |
|---|---|---|---|
| `CryptoUtils` 公共 API | public encrypt/decrypt | package-private decrypt（仅 EcbCryptoUtils） | 即时 |
| `application.yml` 弱默认值 | 警告通过 | 启动失败（prod） | 即时 |
| `StorageService.upload` 签名 | 3 参 | 4 参（增 size） | 即时（破坏性，所有调用方同 PR） |
| Sa-Token 修改密码 | 不踢下线 | 立即踢下线 | 即时 |
| WS handshake | 优先 ticket → legacy token | 同上，但 cutover 后强制 ticket | 30 天 |
| `MeiliSearchClient.search` 旧重载 | 存在 | 删除 | 即时 |
| `R.traceId` | 每次新生成 | 与 MDC 关联 | 即时（前端不需变更） |
| 限流 key | raw URI | 路由模板 | 即时 |


---

## Glossary

| 术语 | 含义 |
|---|---|
| **fail-closed** | 上游基础设施（Redis / DB）异常时，限流 / 鉴权类组件选择"拒绝请求"而非"放行"。本项目敏感写路径（auth/* + ai/*）默认 fail-closed。 |
| **fail-open** | 异常时放行。仅普通读路径允许，避免基础设施抖动让全站不可用。 |
| **EARS** | Easy Approach to Requirements Syntax，需求 / 安全属性的结构化书写规范（WHEN/WHERE/WHILE/IF/THEN/THE/SHALL）。 |
| **Bug 条件 V(X)** | 触发漏洞所需的前置条件集合 X 与具体输入。本项目用作复现脚本的输入约束。 |
| **Preservation** | 修复 bug 后必须保持不变的既有合理行为。与 fix-checking 配对作为回归验证。 |
| **HKDF** | RFC 5869 定义的 KDF，从主密钥派生场景化子密钥（按 purpose 分域）。本项目用于 AES-GCM 子密钥派生。 |
| **CryptoService.purpose** | HKDF info 字段值，区分加密用途。当前用 `tenant-ai-key`，未来可扩展。 |
| **路由模板 / Route Template** | Spring HandlerMapping 暴露的 `BEST_MATCHING_PATTERN_ATTRIBUTE`，含 path variable（如 `/api/v1/posts/{id}`）。本项目限流 key 改用此值代替原始 URI。 |
| **WS Ticket** | WebSocket 一次性票据，由 SignedUrlService HMAC 签名，TTL 30 秒；用于替代将 Sa-Token 主令牌写入 URL query。 |
| **Cutover Date** | 兼容期截止日期。到期后 feature flag 强制切换到新行为，运维需提前完成迁移。 |
| **Trusted Proxy** | 可信反向代理 IP 段。仅当请求来源 IP 命中 `security.trusted-proxies` 时，应用才采信 X-Forwarded-For。 |
| **Self Hosts** | 本站存储域名集合，由 `STORAGE_MINIO_ENDPOINT` 自动推导。用作头像/封面 URL 默认白名单。 |
| **encVersion** | 租户 `ai_config` JSON 内嵌的加密版本号。`1` = 旧 ECB，`2` = AES-GCM + HKDF。 |
| **Sanitize** | 清洗 HTML 输入，移除 `<script>` / event handler / `javascript:` URL 等危险载荷。本项目用 OWASP Java HTML Sanitizer。 |
| **Normalize** | 文本归一化。NFKC + 移除零宽 + 全角转半角 + 小写。用于敏感词匹配预处理。 |
| **MDC** | SLF4J 的 Mapped Diagnostic Context，用于让日志携带 traceId / tenantId / userId。 |


---

## Correctness Properties

下列属性是后续 PBT 测试的输入约束，描述"对任意合法输入，系统必须满足的不变量"。每条属性可对应一个或多个 jqwik / fast-check 测试。

### Property 1: Sanitizer 输出永远安全

**Validates: Requirements 18.1**（对应 bugfix.md 漏洞 18 的安全属性）

对任意字符串 `s`：

```
∀ s ∈ String,
   sanitizePost(s) does not contain "<script", "onerror=", "javascript:"  (case-insensitive)
   AND sanitizePost(sanitizePost(s)) == sanitizePost(s)   (幂等)
```

### Property 2: Markdown 引用块不被注入逃逸

**Validates: Requirements 20.1**（bugfix.md 漏洞 20）

对任意 nickname `n` 与帖子内容 `c`：

```
∀ n ∈ ValidNickname, ∀ c ∈ String,
   render(quoteBlock(escape(n), escape(c)))
   匹配引用块模式 "^> .*$"，且 quoteBlock 之后无任意字符越过引用边界。
```

### Property 3: 限流路由模板共享桶

**Validates: Requirements 7.1**（bugfix.md 漏洞 7）

设 `T` 为某个含 path variable 的路由模板，`maxR` 为其窗口配额：

```
∀ N ∈ ℕ, ∀ {id_1..id_N} ⊆ Long,
   total_requests_in_window(T, {id_1..id_N}) ≤ maxR
   即：N 个不同 id 的请求总数受 maxR 约束，而非 N×maxR。
```

### Property 4: 租户隔离

**Validates: Requirements 14.1, 22.1, 25.1**（bugfix.md 漏洞 14, 22, 25）

```
∀ user u ∈ tenant A, ∀ resource r ∈ tenant B (A ≠ B),
   request(u → r) → response.status ∈ {403, 404}
   且 response.body 不包含 r 的任何字段。
```

### Property 5: 签名 URL 互逆

**Validates: Requirements 3.1**（bugfix.md 漏洞 3 — signed-url-secret 强度）

```
∀ userId, type, resourceId, action, exp ∈ valid range,
   verify(sign(userId, type, resourceId, action, exp), type, resourceId, action) ≠ null
   ∧ verify 在 exp + 1 秒后返回 null。
```

### Property 6: 加密互逆

**Validates: Requirements 1.1**（bugfix.md 漏洞 1 — 加密回退原文风险）

```
∀ plaintext ∈ String, ∀ purpose ∈ NonEmptyString,
   decrypt(encrypt(plaintext, purpose), purpose) == plaintext
   ∧ decrypt(encrypt(plaintext, p1), p2) throws CryptoException  (p1 ≠ p2)
```

### Property 7: MIME 黑名单不可绕过

**Validates: Requirements 24.1**（bugfix.md 漏洞 24）

```
∀ file ∈ MultipartFile,
   detected_mime(file) ∈ BLOCKED_MIMES
   → MimeTypeValidator.validate throws MimeMismatchException
   (不论 originalFilename 与扩展名为何)
```

### Property 8: 密码变更踢下线

**Validates: Requirements 5.1**（bugfix.md 漏洞 5）

```
∀ user u, ∀ token t held by u,
   changePassword(u, ...) → ∀ subsequent request with t : response.status == 401
```

### Property 9: MDC TraceId 与响应一致

**Validates: Requirements 31.1**（bugfix.md 漏洞 31）

```
∀ request,
   response.header("X-Trace-Id") == MDC.get("traceId") within request scope
   ∧ R.traceId in response body == response.header("X-Trace-Id")
```

### Property 10: 会话状态机

**Validates: Requirements 5.1, 8.1**（bugfix.md 漏洞 5, 8）

```
state ∈ {Anonymous, LoggedIn, Banned, Kicked}
transitions:
   login(valid)         : Anonymous → LoggedIn
   ban(self)            : LoggedIn → Banned   (kickout)
   changePassword(self) : LoggedIn → Kicked   (kickout)
   logout()             : LoggedIn → Anonymous
∀ state s ∈ {Banned, Kicked}, request with old token → 401
```

这些属性将在 `tasks.md` 中映射为具体的 PBT 任务（jqwik / fast-check 框架），与每个修复 PR 同步合入。
