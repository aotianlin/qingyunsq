# 安全加固技术设计文档

## Overview

本文档为校园论坛项目的 32 项安全缺陷提供整体修复设计，涵盖凭证管理、认证、限流、SSRF、XSS、文件上传与部署等多个子系统。设计遵循"基础设施先行、配置驱动、fail-closed 优先、向前兼容"四个原则，所有改动通过 feature flag 控制灰度。

## Introduction

本文档为 32 项安全缺陷的修复提供技术设计。修复涉及多个子系统（凭证、认证、限流、SSRF、XSS、文件上传、部署），但许多修复在同一类基础设施上做改造（如 `TrustedProxyResolver`、`PrivateNetworkValidator`、`SecurityProperties`），因此设计上以"基础设施先行 → 业务层应用"的顺序展开，避免重复造轮子。

总体策略：
- **不引入新框架**：继续使用 Sa-Token + MyBatis-Plus + Redis，所有修复在现有抽象层上扩展。
- **配置驱动**：新增能力通过 `SecurityProperties` 等 ConfigurationProperties 暴露，运维通过环境变量调整。
- **fail-closed 优先**：敏感写路径（登录、密码、AI、SSRF 校验）一律 fail-closed；只读列表保留 fail-open。
- **向前兼容**：数据库迁移采用增量 ALTER + 后台异步回填策略，避免一次性长事务锁表。

## Glossary

- **HKDF (HMAC-based Key Derivation Function)**：基于 HMAC 的密钥派生函数，将主密钥派生为多个用途的子密钥
- **AES-GCM**：带认证的对称加密模式，提供机密性与完整性
- **DNS 重绑定 (DNS Rebinding)**：攻击者通过低 TTL DNS 在校验阶段返回公网 IP、连接阶段返回内网 IP 的 SSRF 技巧
- **CSP (Content Security Policy)**：HTTP 响应头，限制页面可加载的脚本/样式/图片来源
- **HSTS (HTTP Strict Transport Security)**：强制浏览器仅通过 HTTPS 访问
- **Fail-closed**：依赖项失败时拒绝请求；与 fail-open（依赖失败时放行）相对
- **Ticket**：一次性短期令牌，用于将主令牌从 URL 中移除（如 WebSocket 握手）
- **TOCTOU (Time-of-check Time-of-use)**：检查与使用之间存在时间差的竞态漏洞类型



## Bug Details

本节按缺陷编号详述每个问题的代码位置、触发条件与影响面。完整缺陷列表参见 `bugfix.md`。

| 缺陷 | 文件 | 触发点 | 影响 |
|------|------|--------|------|
| 1.1 | `common/CryptoUtils.java` | AES/ECB + 硬编码密钥 + 解密失败回退原文 | 凭证泄漏 |
| 1.2 | `user/service/UserService.java#forgotPassword` | 重置 token 明文落库 | 数据库泄漏即重置任意账号 |
| 1.3 | `frontend/composables/useWebSocket.ts` | Sa-Token 走 query string | token 泄漏到 access log/Referer |
| 1.4 | `user/mapper/UserMapper.java#selectUserIdsByTagSubscription` | LIKE 通配符未转义 | 跨用户群发 + 盲注 |
| 1.5 | `search/service/MeiliSearchClient.java` | 索引文档无 tenantId | 跨租户搜索数据泄漏 |
| 1.6 | `search/service/SearchService.java#searchPosts` | FULLTEXT 用未过滤 keyword | 搜索 DoS |
| 1.7 | `user/service/UserService.java#changeRole` | 反向降级未检查权重 | TENANT_ADMIN 接管 SUPER_ADMIN |
| 1.8 | `user/service/UserService.java` + `admin/security/AdminStpInterface.java` | role 缓存 Session 不踢出 | 权限延迟生效 |
| 1.9 | `user/controller/AuthController.java#changePassword` | Map 接收 + 无 @Pattern | 弱密码 |
| 1.10 | `infra/security/PrivateNetworkValidator.java` | 校验阶段与连接阶段两次 DNS | DNS 重绑定 SSRF |
| 1.11 | `resource/controller/ResourceController.java#preview` | 后端 302 到 kkfileview | SSRF + Open Redirect |
| 1.12 | `resource/controller/ResourceController.java` | 404/403 错误码差异 | 资源 ID 枚举 |
| 1.13 | `application.yml` + `ResourceService.java` | 无 multipart 限制 + getBytes() 全读 | OOM DoS |
| 1.14 | `RedisRateLimiter` / `LoginLockoutService` / `UserService.isRateLimited` | Redis 异常 fail-open | 暴力破解绕过 |
| 1.15 | `infra/security/LoginLockoutService.java` | 仅 (tenant,email) 计数 | 针对性 DoS |
| 1.16 | `admin/controller/AdminUserController.java#batchSetStatus` | ids 数组无上限 | N+1 + 长事务 |



| 缺陷 | 文件 | 触发点 | 影响 |
|------|------|--------|------|
| 1.17 | `admin/service/AuditLogService.java#getClientIp` | 直接读 X-Forwarded-For | 审计日志栽赃 |
| 1.18 | `ai/service/OpenAiCompatService.java#chatCompletion` | 错误响应回传上游状态码 | API Key 探测 |
| 1.19 | `frontend/pages/Resources.vue` | iframe 缺 sandbox | XSS 盗 token |
| 1.20 | `user/dto/UpdateProfileRequest.java` | URL 字段无校验 | XSS / Open Redirect |
| 1.21 | `post/service/PostService.java#toVO` | UserVO 暴露 email | PII 泄漏 |
| 1.22 | `resource/service/ResourceService.java#upload` | 仅扩展名校验 | MIME 伪造 |
| 1.23 | `application.yml#upload.allowed-extensions` | 默认允许压缩包 | 恶意载荷扩散 |
| 1.24 | `post/dto/CreatePostRequest.java` | content 无 @Size | 巨型内容 DoS |
| 1.25 | `common/GlobalExceptionHandler.java` | TenantContext null → 500 | 接口 5xx DoS |
| 1.26 | `deploy/nginx/nginx.conf` | 缺少安全头 | 缺乏纵深防御 |
| 1.27 | `backend/Dockerfile` | root 运行 java | 容器逃逸风险 |
| 1.28 | `deploy/nginx/nginx.conf` | actuator 未屏蔽 | 信息泄漏 |
| 1.29 | `frontend/stores/auth.ts` | localStorage 存 token | XSS 即会话劫持 |
| 1.30 | 后端无 CORS 配置 | 隐式默认 | 反代误配置风险 |
| 1.31 | `post/service/MentionParser.java` | mention 无数量上限 | 通知放大 |
| 1.32 | `resource/service/ResourceService.java#md5Hex` | MD5 抗碰撞失效 | 未来误用风险 |

## Expected Behavior

每个缺陷修复后应满足的可观测行为见 `bugfix.md` 的 `### Expected Behavior (Correct)` 章节（编号 2.1 - 2.32）。本节不再重复列出，仅强调跨缺陷的全局不变量：

1. **不变量 A — 凭证不出域**：任何 API 响应、日志、URL query string 都不应包含 Sa-Token 主令牌、AI API Key 明文、reset token 明文
2. **不变量 B — 跨租户禁止数据流出**：MeiliSearch、SQL、Redis 中所有按用户/帖子/资源维度的查询都包含 tenantId 过滤
3. **不变量 C — 敏感操作 fail-closed**：登录、修改密码、忘记密码、AI 调用在 Redis 不可用时一律拒绝
4. **不变量 D — 错误响应不区分存在性**：资源访问、密码重置、登录失败的错误响应应保持一致避免枚举



## Hypothesized Root Cause

通过代码审计，缺陷的根本原因可归纳为五类：

### A. 早期开发阶段未使用安全默认值

- **1.1** `CryptoUtils` 使用硬编码默认密钥与 ECB 模式，注释 `// 默认的 16 字节密钥（生产环境应配置到外部）` 说明开发者意识到风险但未在生产前替换
- **1.2** Reset token 明文落库是常见的"first iteration"实现，缺少安全 review 阶段
- **1.13** `application.yml` 仅有自定义 `upload.max-file-size: 50MB` 但未对接 Spring 的 `spring.servlet.multipart.max-file-size`，开发者误以为已生效
- **1.27** Dockerfile 默认 root 运行是 base image 默认行为，未做纵深防御调整

### B. 多防御层之间不一致

- **1.17** 限流走 `TrustedProxyResolver`、审计走原始 `X-Forwarded-For`，两条路径解析逻辑不一致
- **1.10** PrivateNetworkValidator 在保存配置时校验，但 RestTemplate 实际连接时不校验，校验点与使用点割裂
- **1.30** WebSocket 显式配置 `setAllowedOrigins`，HTTP 层却无 CORS 配置，跨子系统标准缺失

### C. 错误处理副作用

- **1.1** `CryptoUtils.decrypt` catch 后回退原文是为兼容历史明文数据，但变成探测明文残留的工具
- **1.14** Redis 异常 fail-open 是为保证可用性，但敏感写路径需 fail-closed
- **1.18** AI 调用错误回写上游状态码本意是辅助调试，实际成了 API Key 有效性探测



### D. 信任边界识别不准确

- **1.4** `tag` 字段在前端被视作"可控"但在 SQL 中被 LIKE 拼接时未识别为攻击面
- **1.5** MeiliSearch 在 multi-tenant 模式下被视作"内部组件"，但实际跨租户查询能力未做限制
- **1.7/1.8** `changeRole` 仅检查"是否能提升"未检查"是否能降级"，反向降级路径被忽视
- **1.20** `avatarUrl` 字段被视作"用户自己的资料"未识别为存储型 XSS 与 Open Redirect 入口

### E. 性能 / 可用性优化时引入安全债务

- **1.13** `file.getBytes()` 是为简化 MD5 + 上传两步流程，但放大了内存攻击面
- **1.16** `batchSetStatus` 用 for 循环逐个调用是为复用单个接口逻辑，但缺少批量上限
- **1.31** `MentionParser` 不限数量是为支持长文章，但成为通知放大攻击的入口

## Correctness Properties

实施完成后应满足的形式化属性，作为测试设计的依据：

### Property 1: 加密往返性

`forall p, k. decrypt(encrypt(p, k), k) == p`

**Validates: Requirements 2.1**

CryptoServiceTest 验证。

### Property 2: 密钥强度

`forall startup. masterKey.length >= 32 OR application_fails_to_start`

**Validates: Requirements 2.1**

SecurityStartupValidator 在 `@PostConstruct` 中校验。

### Property 3: 跨租户隔离

`forall search(keyword, tenant_A). result.tenantId == tenant_A`

**Validates: Requirements 2.5**

SearchTenantIsolationIT 验证 MeiliSearch 与 SQL 两条路径。

### Property 4: LIKE 转义安全

`forall tag in {"%", "_", "\\\\"}. subscriber_count(tag) == 0`

**Validates: Requirements 2.4**

LikeSqlInjectionIT 验证转义后特殊字符不会触发通配匹配。

### Property 5: 错误响应一致性

`forall existent_id_no_perm, nonexistent_id. status(GET /resources/X) is identical`

**Validates: Requirements 2.12**

ResourceEnumerationIT 验证。

### Property 6: fail-closed 不变量

`forall login_request when redis_down. response.status == 503`

**Validates: Requirements 2.14**

LoginLockoutFailClosedIT 验证。

### Property 7: 角色变更立即生效

`forall changeRole(u, r). within_5_seconds(getUserPermission(u) reflects r)`

**Validates: Requirements 2.7, 2.8**

通过 `StpUtil.kickoutByLoginId` 实现，端到端测试验证。

### Property 8: Reset Token 不可逆

`forall token_hash in DB. cannot_recover_plaintext(token_hash)`

**Validates: Requirements 2.2**

TokenHasher 单向哈希；通过密码学性质保证。

### Property 9: DNS 重绑定免疫

`forall url. resolved_at_validation == resolved_at_connection OR connection_aborted`

**Validates: Requirements 2.10**

SafeHttpClient 在 Socket 连接阶段二次校验，SsrfBlockedIT 验证。

### Property 10: WS 票据一次性与短期

`forall ticket. lifetime(ticket) <= 30s`

**Validates: Requirements 2.3**

WsTicketServiceTest 验证 TTL；一次性消费在 Redis 中通过 SET NX 实现（task 3.5）。



## Fix Implementation

### 1. 基础设施扩展（前置）

#### 1.1 SecurityProperties 扩展

在 `infra/security/SecurityProperties.java` 新增子配置类：

```java
@Data public static class Crypto {
    private String masterKey;  // 来自 ENV CRYPTO_MASTER_KEY，长度 ≥ 32 字节
    private boolean legacyMode = false; // 紧急回滚开关
}

@Data public static class Cors {
    private List<String> allowedOrigins = List.of();
    private List<String> allowedMethods = List.of("GET", "POST", "PUT", "DELETE", "OPTIONS");
}

@Data public static class Upload {
    private boolean realMimeCheck = true;
    private List<String> blockedExtensions = List.of();
    private List<String> allowedAssetHosts = List.of(); // 头像/封面 URL 域名白名单
}

@Data public static class WsTicket {
    private int ttlSeconds = 30;
}
```

`SecurityStartupValidator` 在 `@PostConstruct` 中校验 `crypto.masterKey` 长度，不达标抛 `IllegalStateException` 阻止启动。

#### 1.2 错误码与异常

在 `common/ErrorCode.java` 新增枚举值（保留现有错误码不变）：

```java
SERVICE_UNAVAILABLE(50301, "服务暂时不可用，请稍后重试"),
SSRF_BLOCKED(40010, "禁止指向内网或本机地址"),
MIME_MISMATCH(40011, "文件类型与扩展名不一致"),
RESET_TOKEN_INVALID(40012, "重置令牌无效或已过期"),
BATCH_SIZE_EXCEEDED(40013, "单次最多处理 100 条"),
CRYPTO_FAILURE(50001, "加密服务异常")
```

新增异常类放入 `infra/security/`：`CryptoException`、`SSRFBlockedException`、`MimeMismatchException`，均继承 `RuntimeException`。



### 2. CryptoService（缺陷 1.1）

新增 `infra/security/crypto/CryptoService.java`：

```java
@Component
public class CryptoService {
    private static final String GCM_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private final SecretKeySpec masterKey;

    public CryptoService(SecurityProperties props) {
        byte[] keyBytes = props.getCrypto().getMasterKey().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) throw new IllegalStateException("masterKey 长度需 >= 32");
        this.masterKey = new SecretKeySpec(Arrays.copyOf(keyBytes, 32), "AES");
    }

    public String encrypt(String plaintext, String purpose) {
        SecretKeySpec sub = hkdfDerive(masterKey, purpose);
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance(GCM_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, sub, new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(concat(iv, ct));
    }

    public String decrypt(String ciphertext, String purpose) {
        byte[] all = Base64.getDecoder().decode(ciphertext);
        byte[] iv = Arrays.copyOfRange(all, 0, GCM_IV_LENGTH);
        byte[] ct = Arrays.copyOfRange(all, GCM_IV_LENGTH, all.length);
        SecretKeySpec sub = hkdfDerive(masterKey, purpose);
        Cipher cipher = Cipher.getInstance(GCM_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, sub, new GCMParameterSpec(GCM_TAG_BITS, iv));
        return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
    }
}
```

`TenantService.resolveAiCredentials` 改造：

```java
String enc = credentials.get("apiKey");
Object encVersion = cfg.get("encVersion");
if (encVersion instanceof Number n && n.intValue() == 2) {
    credentials.put("apiKey", cryptoService.decrypt(enc, "tenant-ai-key"));
} else {
    String legacy = CryptoUtils.decrypt(enc); // 旧 ECB
    credentials.put("apiKey", legacy);
    asyncReencryptToV2(tenantId, legacy);  // 异步升级
}
```



### 3. TokenHasher 与 Reset Token 改造（缺陷 1.2）

新增 `common/TokenHasher.java`：

```java
public final class TokenHasher {
    public static String sha256Hex(String input) {
        if (input == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
```

`UserService.forgotPassword` 改造：

```java
byte[] bytes = new byte[32];
secureRandom.nextBytes(bytes);
String plainToken = base64Encoder.encodeToString(bytes);
user.setResetToken(TokenHasher.sha256Hex(plainToken));   // 哈希后落库
user.setResetTokenExpires(LocalDateTime.now().plusMinutes(...));
userMapper.updateById(user);
emailService.sendResetEmail(email, plainToken);          // 邮件发明文
```

`UserService.resetPassword` 改造：

```java
String hashed = TokenHasher.sha256Hex(token);
if (user.getResetToken() == null || !user.getResetToken().equals(hashed)) {
    throw new BusinessException(ErrorCode.RESET_TOKEN_INVALID);
}
```

数据库迁移：`UPDATE users SET reset_token = NULL, reset_token_expires = NULL WHERE reset_token IS NOT NULL` 强制现存 token 失效，列长度 `VARCHAR(128) → VARCHAR(64)`。



### 4. WsTicketService（缺陷 1.3）

新增 `infra/security/WsTicketService.java`：

```java
@Component
public class WsTicketService {
    private final SignedUrlService signedUrlService;  // 复用 HMAC 算法
    private final SecurityProperties props;

    public Ticket issue(long userId, long tenantId) {
        long exp = System.currentTimeMillis() / 1000 + props.getWsTicket().getTtlSeconds();
        // resourceId 字段复用为 tenantId，type 固定 "WS_TICKET"，action 固定 "connect"
        String token = signedUrlService.sign(userId, "WS_TICKET", tenantId, "connect", exp);
        return new Ticket(token, exp);
    }

    public Verified verify(String ticket, long expectedTenantId) {
        return signedUrlService.verify(ticket, "WS_TICKET", expectedTenantId, "connect");
    }
}
```

`AuthController` 新增端点：

```java
@PostMapping("/ws-ticket")
public R<Map<String, Object>> wsTicket() {
    long userId = StpUtil.getLoginIdAsLong();
    long tid = (long) StpUtil.getSession().get("tenantId");
    Ticket t = wsTicketService.issue(userId, tid);
    return R.ok(Map.of("ticket", t.token(), "expiresAt", t.exp()));
}
```

`TenantHandshakeInterceptor.extractToken` 改造：

```java
// 优先 ticket 走 WsTicketService.verify，未提供回退到 token（兼容期）
String ticket = queryParam(request, "ticket");
if (ticket != null) {
    Verified v = wsTicketService.verify(ticket, /* 不在此校验 tenant */ ?);
    if (v == null) { response.setStatusCode(UNAUTHORIZED); return false; }
    attributes.put("userId", v.userId());
    attributes.put("tenantId", v.resourceId());  // 复用 resourceId 作为 tenantId
    return true;
}
// fallback: token query 参数（旧逻辑保留 2 周过渡期）
```

前端 `useWebSocket.ts` 改造：

```ts
async function globalConnect() {
  const ticketResp = await fetch('/api/v1/auth/ws-ticket', {
    method: 'POST',
    headers: { Authorization: localStorage.getItem('token')! },
  }).then(r => r.json());
  const ticket = ticketResp.data.ticket;
  globalWs = new WebSocket(`${proto}//${location.host}/ws/notify?ticket=${ticket}`);
}
```



### 5. LIKE 通配符修复（缺陷 1.4）

`UserMapper.selectUserIdsByTagSubscription` 改造：

```java
@Select("<script>" +
        "SELECT id FROM users WHERE tenant_id = #{tenantId} " +
        "AND tag_subscriptions IS NOT NULL " +
        "AND tag_subscriptions != '' AND tag_subscriptions != '[]' " +
        "AND (" +
        "<foreach collection='tags' item='tag' separator=' OR '>" +
        "tag_subscriptions LIKE CONCAT('%\"', #{tag}, '\"%') ESCAPE '\\\\'" +
        "</foreach>" +
        ")" +
        "</script>")
List<Long> selectUserIdsByTagSubscription(
    @Param("tenantId") Long tenantId,
    @Param("tags") List<String> tags);
```

`UserService.findSubscribedUserIds` 改造：

```java
private static final Pattern TAG_PATTERN = Pattern.compile("^[\\w\\u4e00-\\u9fa5\\-]{1,32}$");

public Set<Long> findSubscribedUserIds(List<String> tags) {
    List<String> safe = tags.stream()
        .filter(t -> TAG_PATTERN.matcher(t).matches())
        .map(t -> t.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_"))
        .toList();
    if (safe.isEmpty()) return Set.of();
    Long tid = TenantContext.getTenantId();
    List<Long> ids = userMapper.selectUserIdsByTagSubscription(tid, safe);
    // ... 其余 Java 层精确匹配逻辑保留
}
```

### 6. MeiliSearch 跨租户隔离（缺陷 1.5）

`MeiliSearchClient` 增加 tenantId 字段写入与 filter：

```java
public void indexDocument(String index, Map<String, Object> document) {
    Long tid = TenantContext.getTenantId();
    if (tid != null) document.put("tenantId", tid);
    indexDocuments(index, List.of(document));
}

public List<Map<String, Object>> search(String index, String query, int limit, Long tenantId) {
    Map<String, Object> body = new HashMap<>();
    body.put("q", query);
    body.put("limit", limit);
    if (tenantId != null) body.put("filter", "tenantId = " + tenantId);
    Map resp = post("/indexes/" + index + "/search", body);
    return (List<Map<String, Object>>) resp.getOrDefault("hits", List.of());
}

private void ensureIndex(String index) {
    // 创建索引后调用
    HttpEntity<List<String>> entity = new HttpEntity<>(List.of("tenantId"), headers);
    restTemplate.exchange(host + "/indexes/" + index + "/settings/filterable-attributes",
                         HttpMethod.PUT, entity, String.class);
}
```

`SearchService.searchPostsViaMeiliSearch` 调用时传入 `TenantContext.getTenantId()`。



### 7. FULLTEXT keyword 修复（缺陷 1.6）

`SearchService.searchPosts` 改造：使用 `safeKeyword` 替换 `keyword`，并在入口做长度截断：

```java
public List<SearchResultVO> search(String keyword, ...) {
    if (keyword == null || keyword.length() > 64) {
        keyword = keyword == null ? "" : keyword.substring(0, 64);
    }
    String safeKeyword = keyword.replaceAll("[^\\p{L}\\p{N}\\s]", "").strip();
    // ... 后续传递 safeKeyword 而非 keyword 到 apply()
    qw.apply("MATCH(title, content) AGAINST({0} IN NATURAL LANGUAGE MODE)", safeKeyword);
}
```

### 8. 角色降级与 kickout（缺陷 1.7、1.8）

`UserService.changeRole` 加入权重校验与强制下线：

```java
private static final Map<String, Integer> ROLE_WEIGHT = Map.of(
    "USER", 1, "TENANT_ADMIN", 2, "SUPER_ADMIN", 3
);

public void changeRole(Long userId, String role) {
    User user = userMapper.selectById(userId);
    if (user == null) throw new BusinessException(USER_NOT_FOUND);
    String callerRole = (String) StpUtil.getSession().get("role");
    int callerW = ROLE_WEIGHT.getOrDefault(callerRole, 0);
    int targetCurrentW = ROLE_WEIGHT.getOrDefault(user.getRole(), 0);
    int targetNewW = ROLE_WEIGHT.getOrDefault(role, 0);
    if (callerW < targetCurrentW || callerW < targetNewW) {
        throw new BusinessException(FORBIDDEN, "权限不足");
    }
    user.setRole(role);
    userMapper.updateById(user);
    StpUtil.kickoutByLoginId(userId);  // 强制下线
}

public void banUser(Long userId) {
    // ... 现有逻辑
    StpUtil.kickoutByLoginId(userId);
}
```

### 9. ChangePasswordRequest（缺陷 1.9）

新增 DTO：

```java
@Data
public class ChangePasswordRequest {
    @NotBlank private String oldPassword;

    @NotBlank
    @Size(min = 8, max = 64, message = "密码长度需 8-64 位")
    @Pattern(
        regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d!@#$%^&*()_+\\-=\\[\\]{};':\",.<>?/\\\\|`~]+$",
        message = "密码必须同时包含字母和数字"
    )
    private String newPassword;
}
```

`AuthController.changePassword(@Valid @RequestBody ChangePasswordRequest req)`。



### 10. SafeHttpClient 防 DNS 重绑定（缺陷 1.10）

新增 `infra/security/SafeHttpClient.java`：

```java
public class SafeHttpClient {
    public static RestTemplate build(int connectMs, int readMs) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection conn, String httpMethod) throws IOException {
                super.prepareConnection(conn, httpMethod);
                String host = conn.getURL().getHost();
                InetAddress[] addrs = InetAddress.getAllByName(host);
                for (InetAddress a : addrs) {
                    if (PrivateNetworkValidator.isBlockedAddress(a)) {
                        throw new SSRFBlockedException("禁止指向内网/本机地址：" + host);
                    }
                }
            }
        };
        f.setConnectTimeout(connectMs);
        f.setReadTimeout(readMs);
        return new RestTemplate(f);
    }
}
```

`PrivateNetworkValidator` 暴露 `public static boolean isBlockedAddress(InetAddress addr)` 包内可见方法。

`OpenAiCompatService.createRestTemplate` 与 `MeiliSearchClient` 构造函数改用 `SafeHttpClient.build()`。

### 11. AI 错误响应脱敏（缺陷 1.18）

`OpenAiCompatService.chatCompletion` 改造：

```java
} catch (RestClientResponseException e) {
    log.warn("OpenAI API rejected: status={}, body chars={}",
            e.getStatusCode().value(), e.getResponseBodyAsString().length());
    return "AI 服务暂时不可用，请稍后重试";  // 不再回传 status code
} catch (ResourceAccessException e) {
    log.warn("OpenAI API connection failed: {}", e.getClass().getSimpleName());
    return "AI 服务暂时不可用，请稍后重试";
} catch (Exception e) {
    log.warn("OpenAI API call failed: {}", e.getClass().getSimpleName());
    return "AI 服务暂时不可用，请稍后重试";
}
```

### 12. Office 预览前端跳转（缺陷 1.11）

`ResourceController.preview` 拆分逻辑：

```java
// Office 文档不再 sendRedirect，改返回 JSON
if (isOfficeFile(fileType)) {
    response.setContentType("application/json;charset=UTF-8");
    SignedUrlService.SignedToken sig = signedUrlService.sign(
        StpUtil.getLoginIdAsLong(), "RESOURCE", id, "download");
    String downloadUrl = "/api/v1/resources/" + id + "/download?sig=" + sig.token();
    Map<String, Object> body = Map.of(
        "kind", "office",
        "previewServiceUrl", previewProperties.getOfficeServiceUrl(),
        "downloadUrl", downloadUrl
    );
    objectMapper.writeValue(response.getWriter(), R.ok(body));
    return;
}
```

前端收到 `kind: "office"` 时调用 `window.open(previewServiceUrl + "?url=" + encodeURIComponent(absoluteUrl(downloadUrl)))`。



### 13. 资源 ID 枚举防御（缺陷 1.12）

`ResourceService.ensureCanAccess` 改造：将 `FORBIDDEN` 替换为 `RESOURCE_NOT_FOUND`：

```java
private void ensureCanAccess(Resource resource) {
    Long currentUserId = currentUserIdOrNull();
    String role = currentRoleOrNull();
    if (!canAccess(resource, currentUserId, role)) {
        throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);  // 原为 FORBIDDEN
    }
}
```

`download(...)`、`preview(...)`、`previewText(...)`、`getById(...)` 等内部抛出的 FORBIDDEN 同样改为 `RESOURCE_NOT_FOUND`。Controller 层的 `UNAUTHORIZED`（未登录场景）保留不变。

### 14. Multipart 限制 + 流式 SHA-256（缺陷 1.13、1.32）

`application.yml` 新增：

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 60MB
```

`ResourceService.upload` 改造为流式：

```java
public ResourceVO upload(Long userId, MultipartFile file, UploadResourceRequest req) {
    // ... 扩展名 + MIME 校验
    MessageDigest sha = MessageDigest.getInstance("SHA-256");
    String storageKey;
    try (InputStream in = file.getInputStream();
         DigestInputStream dis = new DigestInputStream(in, sha)) {
        storageKey = storageService.upload(dis, originalName, file.getContentType());
    }
    String sha256 = HexFormat.of().formatHex(sha.digest());

    // 去重：优先 sha256，回退 md5
    Resource existing = resourceMapper.selectOne(new LambdaQueryWrapper<Resource>()
        .eq(Resource::getFileSha256, sha256)
        .eq(Resource::getStatus, 1).last("LIMIT 1"));
    if (existing != null) {
        storageService.delete(storageKey);  // 已重复，删除新上传
        return toVO(existing);
    }
    resource.setFileSha256(sha256);
    // 过渡期：保留 fileMd5 字段不强制写入，已有数据继续可查
    resourceMapper.insert(resource);
}
```

数据库迁移 `V20260522_02__resource_sha256.sql`：

```sql
ALTER TABLE resources ADD COLUMN file_sha256 VARCHAR(64) DEFAULT NULL COMMENT 'SHA-256';
CREATE INDEX idx_resources_file_sha256 ON resources(file_sha256);
```



### 15. 限流与暴力破解（缺陷 1.14、1.15、1.16）

`LoginLockoutService` 改 fail-closed：

```java
public void ensureNotLocked(long tenantId, String email) {
    if (!properties.getLoginLockout().isEnabled() || email == null) return;
    String key = lockKey(tenantId, email);
    try {
        String value = redisTemplate.opsForValue().get(key);
        if (value != null) {
            throw new BusinessException(RATE_LIMITED, "登录失败次数过多，请稍后再试");
        }
    } catch (BusinessException e) {
        throw e;
    } catch (Exception e) {
        log.error("Login lockout check failed (fail-closed)", e);
        throw new BusinessException(SERVICE_UNAVAILABLE);  // 改为 fail-closed
    }
}
```

新增 IP 维度计数：

```java
public void ensureIpNotLocked(String ip) {
    String key = "login_lock_ip:" + ip;
    String v = redisTemplate.opsForValue().get(key);
    if (v != null) throw new BusinessException(RATE_LIMITED, "来自该 IP 的登录失败过多");
}

public void recordIpFailure(String ip) {
    String key = "login_fail_ip:" + ip;
    Long count = redisTemplate.opsForValue().increment(key);
    if (count != null && count == 1) redisTemplate.expire(key, 900, SECONDS);
    if (count != null && count >= 20) {
        redisTemplate.opsForValue().set("login_lock_ip:" + ip, "1", 900, SECONDS);
    }
}
```

`UserService.login` 调用顺序：

```java
String ip = trustedProxyResolver.resolve(request);
loginLockoutService.ensureIpNotLocked(ip);
loginLockoutService.ensureNotLocked(tid, req.getEmail());
// ... 密码校验
if (!ok) {
    loginLockoutService.recordFailure(tid, req.getEmail());
    loginLockoutService.recordIpFailure(ip);
    throw new BusinessException(INVALID_CREDENTIALS);
}
```

`AdminUserController.batchSetStatus` 加上限：

```java
if (ids.size() > 100) {
    throw new BusinessException(BATCH_SIZE_EXCEEDED);
}
```



### 16. 审计日志使用 TrustedProxyResolver（缺陷 1.17）

`AuditLogService` 注入 `TrustedProxyResolver`：

```java
@RequiredArgsConstructor
public class AuditLogService {
    // ... 现有字段
    private final TrustedProxyResolver trustedProxyResolver;

    private String getClientIp() {
        return trustedProxyResolver.resolve(request);
    }
}
```

### 17. iframe sandbox（缺陷 1.19）

`frontend/src/pages/Resources.vue` 修改：

```html
<iframe
    class="markdown-frame"
    :srcdoc="markdownSrcdoc"
    title="Markdown 预览"
    sandbox="allow-popups allow-popups-to-escape-sandbox"
/>

<iframe
    v-else-if="getPreviewKind(selectedResource) === 'pdf'"
    class="preview-frame"
    :src="previewUrl"
    title="PDF 预览"
    sandbox="allow-scripts"
/>
```

### 18. Profile URL 与 Bio 校验（缺陷 1.20）

`UpdateProfileRequest` 修改：

```java
@Pattern(regexp = "^https?://.+", message = "URL 必须以 http(s) 开头")
@Size(max = 500)
private String avatarUrl;

@Pattern(regexp = "^https?://.+", message = "URL 必须以 http(s) 开头")
@Size(max = 500)
private String profileCoverUrl;

@Size(max = 200, message = "个人简介最长 200 字符")
private String bio;
```

`UserService.updateProfile` 加域名白名单校验：

```java
private void assertHostAllowed(String url) {
    if (url == null) return;
    URI uri = URI.create(url);
    String host = uri.getHost();
    if (host == null || !securityProperties.getUpload().getAllowedAssetHosts().contains(host)) {
        throw new BusinessException(BAD_REQUEST, "URL 域名不在白名单内");
    }
}
```



### 19. PublicUserVO 替换公共场景的 UserVO（缺陷 1.21）

新增 `user/dto/PublicUserVO.java`：

```java
@Data @Builder
public class PublicUserVO {
    private Long id;
    private String nickname;
    private String avatarUrl;

    public static PublicUserVO from(User u) {
        if (u == null) return null;
        return PublicUserVO.builder()
            .id(u.getId()).nickname(u.getNickname()).avatarUrl(u.getAvatarUrl()).build();
    }
}
```

修改各 VO 的 `author/uploader/sender` 字段类型：

| 文件 | 字段 | 旧类型 | 新类型 |
|------|------|--------|--------|
| `PostVO` | `author` | `UserVO` | `PublicUserVO` |
| `CommentVO` | `author` | `UserVO` | `PublicUserVO` |
| `ResourceVO` | `uploader` | `UserVO` | `PublicUserVO` |
| `MessageVO` | `sender`/`receiver` | `UserVO` | `PublicUserVO` |
| `SearchResultVO` | `author` | `UserVO` | `PublicUserVO` |
| `NotificationVO` | `sender` | `UserVO` | `PublicUserVO` |

`UserVO`（含 email）仅保留在 `/api/v1/auth/me`、`/api/v1/users/me`、`/api/v1/users/{id}` 用户主页详情接口中。

### 20. MIME 真实类型校验（缺陷 1.22）

`backend/pom.xml` 新增依赖：

```xml
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-core</artifactId>
    <version>2.9.2</version>
</dependency>
```

新增 `infra/security/MimeTypeValidator.java`：

```java
@Component
public class MimeTypeValidator {
    private final Detector detector = TikaConfig.getDefaultConfig().getDetector();

    private static final Map<String, Set<String>> EXT_TO_MIME = Map.of(
        "jpg",  Set.of("image/jpeg"),
        "jpeg", Set.of("image/jpeg"),
        "png",  Set.of("image/png"),
        "gif",  Set.of("image/gif"),
        "webp", Set.of("image/webp"),
        "pdf",  Set.of("application/pdf"),
        "docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                       "application/zip"),  // DOCX 本身是 ZIP
        // ... 其他映射
    );

    public void validate(MultipartFile file, String declaredExt) throws IOException {
        try (InputStream is = TikaInputStream.get(file.getInputStream())) {
            Metadata md = new Metadata();
            md.set(Metadata.RESOURCE_NAME_KEY, file.getOriginalFilename());
            String detected = detector.detect(is, md).toString();
            Set<String> allowed = EXT_TO_MIME.get(declaredExt.toLowerCase());
            if (allowed != null && !allowed.contains(detected)) {
                throw new MimeMismatchException(
                    "扩展名 ." + declaredExt + " 与实际类型 " + detected + " 不一致");
            }
        }
    }
}
```



### 21. 收紧默认允许扩展名（缺陷 1.23）

`application.yml` 修改：

```yaml
upload:
  max-file-size: 50MB
  # 默认移除 zip,rar,7z；如需开启由租户管理员显式配置
  allowed-extensions: pdf,doc,docx,ppt,pptx,xls,xlsx,jpg,jpeg,png,gif,webp,md,markdown
  optional-extensions: zip,rar,7z  # 仅在租户配置启用时允许
```

`ResourceService` 在校验扩展名时合并允许列表与租户开关。

### 22. 帖子评论长度限制（缺陷 1.24）

```java
public class CreatePostRequest {
    @Size(max = 200) private String title;
    @NotBlank @Size(max = 20000) private String content;
    // ...
}

public class UpdatePostRequest {
    @Size(max = 200) private String title;
    @NotBlank @Size(max = 20000) private String content;
}

public class CreateCommentRequest {
    @NotBlank @Size(max = 5000) private String content;
}

public class UpdateCommentRequest {
    @NotBlank @Size(max = 5000) private String content;
}
```

### 23. TenantContext 缺失返回 503（缺陷 1.25）

`GlobalExceptionHandler.handleIllegalState`：

```java
@ExceptionHandler(IllegalStateException.class)
public ResponseEntity<R<?>> handleIllegalState(IllegalStateException e) {
    log.error("IllegalStateException caught: {}", e.getMessage(), e);
    if (e.getMessage() != null && e.getMessage().contains("TenantContext is null")) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(R.fail(ErrorCode.SERVICE_UNAVAILABLE));
    }
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(R.fail(ErrorCode.INTERNAL_ERROR.getCode(), "服务器内部错误"));
}
```

`TenantResolutionFilter.isExcluded` 收紧 actuator 允许范围：

```java
private boolean isExcluded(HttpServletRequest req) {
    String uri = req.getRequestURI();
    if (uri.startsWith("/actuator/")) {
        // actuator 仅本地访问
        String remote = req.getRemoteAddr();
        return "127.0.0.1".equals(remote) || "::1".equals(remote) || "0:0:0:0:0:0:0:1".equals(remote);
    }
    return uri.startsWith("/swagger-ui/") || uri.startsWith("/v3/api-docs/") || uri.startsWith("/ws/");
}
```



### 24. nginx 安全头与 actuator 屏蔽（缺陷 1.26、1.28）

`deploy/nginx/nginx.conf` 修改 server 块：

```nginx
server {
    listen 80;
    server_name _;

    add_header Content-Security-Policy "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; connect-src 'self' wss: https:; frame-ancestors 'none'" always;
    add_header X-Frame-Options DENY always;
    add_header X-Content-Type-Options nosniff always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;

    location ~ ^/actuator/ {
        return 404;  # 屏蔽 actuator 外部访问
    }

    location / {
        root /usr/share/nginx/html;
        try_files $uri $uri/ /index.html;
    }

    location /api/ { ... 现有反代不变 }
    location /ws/  { ... 现有反代不变 }
}
```

### 25. Dockerfile 非 root 运行（缺陷 1.27）

`backend/Dockerfile`：

```dockerfile
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S appuser && adduser -S -G appuser appuser
WORKDIR /app
COPY target/*.jar app.jar
RUN chown -R appuser:appuser /app
USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "-Djava.security.egd=file:/dev/./urandom", "app.jar"]
```

### 26. 显式 CORS 配置（缺陷 1.30）

新增 `infra/security/CorsConfig.java`：

```java
@Configuration
@RequiredArgsConstructor
public class CorsConfig implements WebMvcConfigurer {
    private final SecurityProperties props;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origins = props.getCors().getAllowedOrigins();
        if (origins.isEmpty()) return;
        registry.addMapping("/api/**")
            .allowedOrigins(origins.toArray(new String[0]))
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .exposedHeaders("Retry-After")
            .allowCredentials(false)  // token 走 header 不需要 cookie
            .maxAge(3600);
    }
}
```

`application.yml`：

```yaml
security:
  cors:
    allowed-origins:
      - http://localhost:3000
      - http://127.0.0.1:3000
```

`application-prod.yml`：`allowed-origins: ${CORS_ALLOWED_ORIGINS}`（无默认）。



### 27. Mention 数量限制（缺陷 1.31）

`MentionParser.extract` 修改：

```java
private static final int MAX_MENTIONS = 20;

public static Set<String> extract(String content) {
    if (content == null || content.isBlank()) return Set.of();
    Set<String> names = new LinkedHashSet<>();
    Matcher m = MENTION_PATTERN.matcher(content);
    while (m.find() && names.size() < MAX_MENTIONS) {
        names.add(m.group(1));
    }
    return names;
}
```

### Wave 28: 灰度与回滚

所有改动通过 feature flag 控制：

| Flag | 默认值 | 含义 |
|------|--------|------|
| `security.crypto.legacy-mode` | `false` | true 时跳过新加密，沿用旧 ECB（紧急回滚） |
| `security.upload.real-mime-check` | `true` | false 时仅扩展名校验 |
| `security.ws-ticket.enforced` | `false` | true 时拒绝 token 走 query string，强制 ticket |
| `security.login-lockout.enabled` | `true` | false 时关闭登录锁定 |

灰度策略：
1. 第一周：所有 flag 保持默认，新代码上线（双轨运行）
2. 第二周：观察异常率，无异常则将 `ws-ticket.enforced` 切到 true
3. 第三周：删除 `CryptoUtils.encrypt`，仅保留 `decrypt` 用于历史数据兼容
4. 第四周：删除旧 token query string 兼容代码

回滚策略：
- 单点配置回滚：将对应 feature flag 改回保守值，无需重新部署代码
- 大版本回滚：每一类修复都对应独立 git commit，可单独 revert



## Testing Strategy

**单元测试**：

- `CryptoServiceTest`：加密/解密往返、HKDF 密钥分域、相同明文密文不同（IV 随机）、密钥过短启动失败、tag 篡改解密失败
- `TokenHasherTest`：null 输入返回 null、SHA-256 长度 64 hex、相同输入相同输出
- `WsTicketServiceTest`：签发立即校验通过、过期校验失败、tenantId 篡改失败
- `MimeTypeValidatorTest`：jpg 改名 .png 拒绝、合法 .docx 通过、空文件拒绝
- `LoginLockoutServiceTest`：fail-closed 路径返回 503、IP 锁定独立于账户锁定
- `MentionParserTest`：超过 20 个 mention 截断、合法 mention 全部识别

**集成测试**：

- `SearchTenantIsolationIT`：在 multi 模式索引租户 A 的帖子，租户 B 用户搜索返回空
- `LikeSqlInjectionIT`：tag = "%" 触发订阅查询，验证不会命中其他用户
- `WsHandshakeIT`：ticket 通过、过期、篡改三种场景；旧 token 兼容期通过、`ws-ticket.enforced=true` 后拒绝
- `ResourceEnumerationIT`：不存在 ID 与无权 ID 响应码相同（均为 404）
- `LoginLockoutFailClosedIT`：mock Redis 不可用，登录请求返回 503
- `SsrfBlockedIT`：mock DNS 返回内网 IP，SafeHttpClient 抛 SSRFBlockedException

**端到端测试**：

- `curl -I https://<domain>/` 验证 CSP/HSTS/X-Frame-Options/X-Content-Type-Options/Referrer-Policy 五项必现
- 管理员页保存 AI baseUrl `http://169.254.169.254` 验证拒绝
- 密码重置：申请 → 邮件 → 重置成功 → 复用同 token 失败
- WebSocket 连接：登录 → 获取 ticket → 连接成功 → 30 秒后用同 ticket 失败
- 上传伪造文件：将 PHP 脚本改名 .png 上传应被拒绝
