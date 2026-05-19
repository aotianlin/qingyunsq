# Design: tenant-isolation-hardening

## Overview

本设计文档将 `bugfix.md` 中定义的 8 个 Fix Property（F1–F8）与 7 组 Acceptance Criteria 转化为可执行的技术方案，遵循以下原则：

1. **服务端权威（Server-Authoritative Tenant Resolution）**：已认证请求的租户 id 由服务端从 Sa-Token Session 直接读取，客户端的任何自报值仅作为安全审计触发器。
2. **快速失败（Fail Fast on Ambiguity）**：multi 模式下租户解析无法落地为合法 active 租户时立刻拒绝，禁止任何形式的"降级到默认租户"。
3. **零侵入业务层**：所有 21 个业务域的 Service/Mapper 代码无须修改；租户上下文管理保持在 `tenant` 与 `infra` 包内。
4. **standalone/multi 二级抽象**：通过 `TenantResolver` 接口 + 两套实现 `StandaloneTenantResolver` / `MultiTenantResolver`，由 `@ConditionalOnProperty` 自动装配，运行期只有一种实现存活。
5. **可观测性**：所有越权拒绝、租户解析失败、审计事件结构化日志 + 必要时落表 `audit_logs`。

## Glossary

- **租户上下文（TenantContext）**：基于 ThreadLocal 的请求级租户 id 持有器
- **权威来源（Authoritative Source）**：已认证请求中租户 id 的唯一可信来源——Sa-Token Session
- **降级（Fallback to default tenant）**：multi 模式下租户解析失败时静默使用 tenant_id=1 的反模式，本 spec 明确禁止
- **Active 租户**：`tenants` 表中 `status=1` 的记录
- **bootstrap migration**：保证新部署或升级时 `tenants` 表存在 `(id=1, status=1)` 记录的 SQL 迁移

## Bug Details

详细的 Bug Condition 定义、6 个子条件 C1–C6 以及对应代码位置见 `bugfix.md` 的 `## Bug Analysis / ### Current Behavior (Defect)` 章节，本节不重复，仅给出本设计聚焦的核心三类缺陷与设计层应对：

| 缺陷分类 | 子条件 | 设计层应对 |
|----------|--------|-----------|
| HTTP 越权 / 跨租户登录 | C1, C4 | `TenantBindingCheckInterceptor` + `UserService.login` 复合查询 + Session 写入 tenantId |
| 注释/降级语义不一致 | C2, C3 | `TenantLineHandler.getTenantId()` 改为 null 时抛 IllegalStateException + 注释重写 |
| 非典型入口缺失上下文/校验 | C5, C6 | `TenantHandshakeInterceptor` + `ActiveTenantCache` 校验存在性 + `TenantStartupValidator` |

## Expected Behavior

完整 Acceptance Criteria（AC-1.1 ~ AC-7.2）见 `bugfix.md` 的 `## Bug Analysis / ### Expected Behavior (Correct)` 章节。本设计的实现需逐条满足这些 AC，验证方式见本文 `## Testing Strategy`。

## Hypothesized Root Cause

| 缺陷子条件 | 假设根因 |
|------------|----------|
| C1（HTTP 越权） | 缺少"已认证请求的租户必须来自服务端权威来源"的设计原则；`TenantInterceptor` 直接信任客户端 header |
| C2（注释失配） | `TenantLineInnerInterceptor` 由静态 `@Bean` 注册，未与 `tenant.mode` 关联；早期注释未及时更新 |
| C3（异常降级） | `TenantLineHandler.getTenantId()` 在 null 时返回 `1L` 是历史遗留的"防御性编程"，但与租户隔离语义冲突 |
| C4（登录跨租户） | `UserService.login()` 设计时未考虑多租户；email 唯一性约束已经升级为 `(tenant_id, email)` 复合唯一，但查询代码未跟进 |
| C5（WebSocket 缺失上下文） | WebSocket 配置由 `notify` 模块本地添加，未与全局 `TenantInterceptor` 集成；缺少 `HandshakeInterceptor` 抽象 |
| C6（租户存在性未校验） | `TenantInterceptor.resolveTenantId()` 仅做字符串解析，未引入"Active 租户集合"概念 |

## Correctness Properties

修复后系统必须维持的不变量，按属性形式表达，每条都有对应自动化测试。

### Property 1: Authoritative Tenant Source for Authenticated Requests

**Validates: Requirements 1.1, 1.3**

已认证请求中 `TenantContext.getTenantId()` 必须等于 `StpUtil.getSession().get("tenantId")`。客户端任何自报值都不得覆盖该值。

- **形式化**：∀ request r where `StpUtil.isLogin() == true` ⟹ `TenantContext.getTenantId() == session.get("tenantId")`
- **验证**：集成测试（`TenantHttpIsolationIT.allowAuthenticatedRequestWithoutHeader/WithMatchingHeader`）+ ArchUnit 检查无任何代码路径直接使用 `request.getHeader("X-Tenant-Id")` 改写 TenantContext

### Property 2: Active Tenant Constraint in Multi Mode

**Validates: Requirements 2.1, 2.2, 2.3**

multi 模式下任何成功通过 `TenantResolutionFilter` 的请求，其 `TenantContext` 持有的 tenantId 必属于 `T_active`（即 `tenants.status=1`）。

- **形式化**：mode=multi ∧ `TenantContext.getTenantId() != null` ⟹ tenantId ∈ T_active
- **验证**：集成测试（构造 status=0 租户并尝试通过 X-Tenant-Id 访问，期望被拒）

### Property 3: Standalone Mode Fixed Tenant

**Validates: Requirements 3.1, 3.2**

standalone 模式下任何成功通过 Filter 的请求，`TenantContext.getTenantId()` 必等于 `tenant.standaloneTenantId`（默认 1L），无视 X-Tenant-Id 与子域名。

- **形式化**：mode=standalone ⟹ `TenantContext.getTenantId() == standaloneTenantId`
- **验证**：`StandaloneModeIT` 用任意 X-Tenant-Id 调用接口验证写入数据 tenant_id=1

### Property 4: No Silent Degradation in TenantLine Handler

**Validates: Requirements 3.3, 6.1**

`TenantLineHandler.getTenantId()` 永不返回降级值；TenantContext 为 null 时抛 IllegalStateException。

- **形式化**：`TenantContext.getTenantId() == null` ⟹ `TenantLineHandler.getTenantId()` throws IllegalStateException
- **验证**：单元测试直接调用 handler，不经过 Filter

### Property 5: Login Timing Indistinguishability

**Validates: Requirements 2.4**

同一 `(email, password)` 输入，登录失败响应时间分布在"用户存在但密码错误"与"用户不存在"两种情况下统计上无显著差异。

- **形式化**：|mean(rt_user_not_found) - mean(rt_wrong_password)| / max(mean) < 0.10
- **验证**：`LoginTimingTest` 跑 N=100 次每种情况，比较均值

### Property 6: Audit Log for Tenant Violations

**Validates: Requirements 1.2**

任何 `TENANT_VIOLATION_ATTEMPT` 必在 `audit_logs` 留下一条记录，包含 operator_id、ip、uri、reason。

- **形式化**：∀ rejected request r with reason="header_mismatch_session" ⟹ ∃ audit_logs record where action='TENANT_VIOLATION_ATTEMPT' ∧ operator_id=r.userId
- **验证**：`TenantHttpIsolationIT.rejectCrossTenantHeaderManipulation` 验证拒绝后 audit_logs 多一条

### Property 7: Tenant-Agnostic Tables Skip Rewriting

**Validates: Requirements 4.1**

`tenants`、`achievements`、`sensitive_words` 三张表的查询 SQL 不被 `TenantLineInnerInterceptor` 改写。

- **形式化**：∀ SQL S targeting table t where t ∈ {tenants, achievements, sensitive_words} ⟹ S 中不出现 `tenant_id = ?`
- **验证**：使用 MyBatis 拦截器测试或在测试中开启 SQL 日志验证



## Fix Implementation

### Architecture Overview


### 2.1 关键流程

```
┌─────────────────────────────────────────────────────────────────────┐
│                        HTTP / WebSocket 请求                         │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                ┌──────────────▼──────────────┐
                │  TenantResolutionFilter     │  (Servlet Filter, 优先级最高)
                │  - 调用 TenantResolver       │
                │  - 写 TenantContext          │
                │  - 失败：直接返回错误响应     │
                └──────────────┬──────────────┘
                               │
            ┌──────────────────┴───────────────────┐
            │                                      │
            ▼                                      ▼
   ┌────────────────────┐                ┌────────────────────────┐
   │  Sa-Token          │                │  WebSocket             │
   │  Auth Interceptor  │                │  HandshakeInterceptor  │
   └─────────┬──────────┘                └────────┬───────────────┘
             │                                    │
             ▼                                    ▼
   ┌────────────────────┐                ┌────────────────────────┐
   │ TenantBindingCheck │                │ TenantContext 已就绪    │
   │ Interceptor        │                │ 通过 attributes 传递    │
   │ (已认证→校验绑定)   │                │ 给 WebSocketHandler    │
   └─────────┬──────────┘                └────────────────────────┘
             │
             ▼
   ┌────────────────────┐
   │  Controller/Service │
   │  + MyBatis-Plus     │
   │  TenantLine SQL 改写 │
   └─────────────────────┘
```

### 2.2 模块划分（修改后）

```
backend/src/main/java/com/campusforum/tenant/
├── TenantContext.java                  (保留，行为不变)
├── TenantMode.java                     (新增 enum: STANDALONE, MULTI)
├── TenantProperties.java               (新增 @ConfigurationProperties)
├── resolver/
│   ├── TenantResolver.java             (新增接口)
│   ├── StandaloneTenantResolver.java   (新增, mode=standalone)
│   ├── MultiTenantResolver.java        (新增, mode=multi)
│   ├── ResolutionResult.java           (新增 record)
│   └── TenantNotResolvedException.java (新增)
├── filter/
│   └── TenantResolutionFilter.java     (新增, 替代 TenantInterceptor)
├── interceptor/
│   └── TenantBindingCheckInterceptor.java (新增, 校验已认证请求)
├── websocket/
│   └── TenantHandshakeInterceptor.java (新增)
├── cache/
│   └── ActiveTenantCache.java          (新增, Caffeine 短 TTL)
├── audit/
│   └── TenantAuditService.java         (新增, 落 audit_logs)
└── controller/
    ├── TenantInfoController.java       (修改, 增加 /tenant/resolve)
    └── TenantController.java           (保留)
```

### 2.3 关键决策摘要

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 拦截器实现层 | Servlet Filter | 优先级高于 Sa-Token Interceptor，可处理 auth 接口与 WebSocket upgrade 之前的租户解析 |
| 租户来源优先级（multi auth） | 子域名 → X-Tenant-Id | 子域名为生产标准（Slack/Atlassian/Vercel 模式）；header 仅作为开发/移动客户端回退 |
| 租户来源（已认证） | 仅 Sa-Token Session | 单一权威来源；客户端 X-Tenant-Id 仅用于"不一致"检测 |
| standalone tenant id | 硬编码 1L | 与现有数据兼容；启动时校验 `tenants` 表存在 (id=1, status=1) |
| 租户存在性缓存 | Caffeine, maxSize=1024, expireAfterWrite=60s | 平衡数据库压力与租户状态变更延迟 |
| 越权审计 | 同步写 audit_logs + 异步 ELK 风格日志 | 安全事件必须落表可查；同时不阻塞主流程 |
| 错误码语义 | INVALID_CREDENTIALS 替代登录失败的细分错误 | OWASP 推荐：防邮箱枚举与时序攻击 |

## 3. 核心组件详细设计

### 3.1 TenantProperties

`@ConfigurationProperties(prefix = "tenant")` 集中管理租户配置：

```java
@Data
@ConfigurationProperties(prefix = "tenant")
public class TenantProperties {
    /** standalone | multi */
    private TenantMode mode = TenantMode.STANDALONE;

    /** standalone 模式下使用的固定租户 id */
    private long standaloneTenantId = 1L;

    /** multi 模式下用于子域名识别的根域名（如 "campusforum.com"），
     *  Host 形如 "{code}.campusforum.com" 时识别为 code */
    private String rootDomain;

    /** 是否在 multi 模式下接受 X-Tenant-Id 作为 auth 接口的回退来源 */
    private boolean allowHeaderFallback = true;

    /** Active 租户缓存配置 */
    private Cache cache = new Cache();

    @Data
    public static class Cache {
        private int maxSize = 1024;
        private Duration ttl = Duration.ofSeconds(60);
    }
}
```

### 3.2 TenantResolver 接口与两套实现

```java
public interface TenantResolver {
    /**
     * 解析当前请求的租户上下文。
     * 已认证请求：从 Sa-Token Session 读取，忽略客户端自报值（不一致时由 TenantBindingCheckInterceptor 检测）
     * 未认证请求（auth 接口、WebSocket 握手）：按模式特定规则解析
     */
    ResolutionResult resolve(HttpServletRequest request);
}

public record ResolutionResult(long tenantId, Source source, String tenantCode) {
    public enum Source {
        SA_TOKEN_SESSION,    // 已认证，权威来源
        SUBDOMAIN,            // multi 模式 auth 接口，子域名解析
        HEADER,               // multi 模式 auth 接口，X-Tenant-Id 回退
        STANDALONE_FIXED      // standalone 模式
    }
}
```

#### StandaloneTenantResolver（mode=standalone）

```java
@Component
@ConditionalOnProperty(name = "tenant.mode", havingValue = "standalone", matchIfMissing = true)
@RequiredArgsConstructor
public class StandaloneTenantResolver implements TenantResolver {
    private final TenantProperties props;

    @Override
    public ResolutionResult resolve(HttpServletRequest request) {
        // standalone 模式：始终返回 standaloneTenantId
        // 完全忽略 X-Tenant-Id、子域名、Sa-Token Session 中的 tenantId
        return new ResolutionResult(props.getStandaloneTenantId(),
                                    Source.STANDALONE_FIXED, "default");
    }
}
```

#### MultiTenantResolver（mode=multi）

```java
@Component
@ConditionalOnProperty(name = "tenant.mode", havingValue = "multi")
@RequiredArgsConstructor
public class MultiTenantResolver implements TenantResolver {
    private final TenantProperties props;
    private final ActiveTenantCache cache;

    @Override
    public ResolutionResult resolve(HttpServletRequest request) {
        // 1. 已认证请求：从 Sa-Token Session 读取
        if (StpUtil.isLogin()) {
            Long tid = (Long) StpUtil.getSession().get("tenantId");
            if (tid != null) {
                String code = cache.getCode(tid);  // 也用于响应头
                return new ResolutionResult(tid, Source.SA_TOKEN_SESSION, code);
            }
            // 已认证但 session 没有 tenantId：要求重新登录
            throw new TenantNotResolvedException("session_missing_tenant");
        }

        // 2. 未认证：尝试子域名
        String host = request.getServerName();
        if (props.getRootDomain() != null && host != null
                && host.endsWith("." + props.getRootDomain())) {
            String code = host.substring(0, host.length() - props.getRootDomain().length() - 1);
            Optional<Long> tid = cache.findIdByCode(code);
            if (tid.isPresent()) {
                return new ResolutionResult(tid.get(), Source.SUBDOMAIN, code);
            }
        }

        // 3. 未认证：尝试 X-Tenant-Id 头
        if (props.isAllowHeaderFallback()) {
            String header = request.getHeader("X-Tenant-Id");
            if (header != null) {
                try {
                    long tid = Long.parseLong(header);
                    if (cache.isActive(tid)) {
                        return new ResolutionResult(tid, Source.HEADER, cache.getCode(tid));
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        // 4. 全部失败：拒绝
        throw new TenantNotResolvedException("no_resolver_matched");
    }
}
```

### 3.3 TenantResolutionFilter

替代当前的 `TenantInterceptor`。注册为 Servlet Filter，order 设为 `Ordered.HIGHEST_PRECEDENCE + 50`（比 Sa-Token 拦截器更早，但允许 Spring Security/CORS 等基础设施 Filter 在它之前）。

```java
@Component
@RequiredArgsConstructor
public class TenantResolutionFilter extends OncePerRequestFilter {
    private final TenantResolver resolver;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        // 排除路径：actuator, swagger, ws upgrade（WS 走 HandshakeInterceptor）
        if (isExcluded(req.getRequestURI())) {
            chain.doFilter(req, res);
            return;
        }

        try {
            ResolutionResult result = resolver.resolve(req);
            TenantContext.setTenantId(result.tenantId());
            TenantContext.setTenantCode(result.tenantCode());
            chain.doFilter(req, res);
        } catch (TenantNotResolvedException e) {
            writeError(res, HttpStatus.BAD_REQUEST,
                       ErrorCode.TENANT_NOT_RESOLVED, e.getMessage());
        } finally {
            TenantContext.clear();
        }
    }

    private boolean isExcluded(String uri) {
        return uri.startsWith("/actuator/")
            || uri.startsWith("/swagger-ui/")
            || uri.startsWith("/v3/api-docs/")
            || uri.startsWith("/ws/");  // WebSocket 走 HandshakeInterceptor
    }

    private void writeError(HttpServletResponse res, HttpStatus status,
                            ErrorCode code, String detail) throws IOException {
        res.setStatus(status.value());
        res.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(res.getWriter(), R.fail(code));
    }
}
```

### 3.4 TenantBindingCheckInterceptor

在 Sa-Token 拦截器之后运行，仅对**已认证**请求生效。检测客户端发的 `X-Tenant-Id` 与登录用户 `tenant_id` 是否一致。

```java
@Component
@RequiredArgsConstructor
public class TenantBindingCheckInterceptor implements HandlerInterceptor {
    private final TenantAuditService auditService;

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        if (!StpUtil.isLogin()) return true;

        String headerVal = req.getHeader("X-Tenant-Id");
        if (headerVal == null) return true;  // 不发就不检查

        long claimedByClient;
        try {
            claimedByClient = Long.parseLong(headerVal);
        } catch (NumberFormatException e) {
            // 非法值视作越权尝试
            return reject(req, res, "invalid_tenant_header", headerVal);
        }

        long actual = TenantContext.getTenantId();
        if (claimedByClient != actual) {
            return reject(req, res, "header_mismatch_session",
                          claimedByClient + " vs session " + actual);
        }
        return true;
    }

    private boolean reject(HttpServletRequest req, HttpServletResponse res,
                           String reason, String detail) throws IOException {
        long userId = StpUtil.getLoginIdAsLong();
        long actualTid = TenantContext.getTenantId();
        auditService.recordViolationAttempt(userId, actualTid, req, reason, detail);
        res.setStatus(HttpStatus.FORBIDDEN.value());
        res.setContentType("application/json;charset=UTF-8");
        new ObjectMapper().writeValue(res.getWriter(), R.fail(ErrorCode.TENANT_VIOLATION));
        return false;
    }
}
```

### 3.5 ActiveTenantCache（Caffeine）

```java
@Component
@RequiredArgsConstructor
public class ActiveTenantCache {
    private final TenantMapper tenantMapper;
    private final TenantProperties props;

    private LoadingCache<Long, Optional<Tenant>> idCache;
    private LoadingCache<String, Optional<Tenant>> codeCache;

    @PostConstruct
    public void init() {
        Caffeine<Object, Object> base = Caffeine.newBuilder()
                .maximumSize(props.getCache().getMaxSize())
                .expireAfterWrite(props.getCache().getTtl());
        idCache = base.build(id ->
                Optional.ofNullable(tenantMapper.selectById(id))
                        .filter(t -> t.getStatus() != null && t.getStatus() == 1));
        codeCache = base.build(code ->
                Optional.ofNullable(tenantMapper.selectOne(
                        new LambdaQueryWrapper<Tenant>()
                                .eq(Tenant::getCode, code)
                                .eq(Tenant::getStatus, 1))));
    }

    public boolean isActive(long tenantId) {
        return idCache.get(tenantId).isPresent();
    }

    public Optional<Long> findIdByCode(String code) {
        return codeCache.get(code).map(Tenant::getId);
    }

    public String getCode(long tenantId) {
        return idCache.get(tenantId).map(Tenant::getCode).orElse(null);
    }

    /** 租户停用/删除时由 TenantService 主动调用 */
    public void evict(long id, String code) {
        idCache.invalidate(id);
        if (code != null) codeCache.invalidate(code);
    }
}
```

### 3.6 TenantHandshakeInterceptor（WebSocket）

```java
@Component
@RequiredArgsConstructor
public class TenantHandshakeInterceptor implements HandshakeInterceptor {
    private final TenantResolver resolver;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler,
                                    Map<String, Object> attributes) {
        // 1. 解析 Sa-Token（前端 query string 或 header 传 token）
        String token = extractToken(request);
        if (token == null || !StpUtil.getLoginIdByToken(token).toString().matches("\\d+")) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        long userId = Long.parseLong(StpUtil.getLoginIdByToken(token).toString());

        // 2. 从 session 取 tenantId（登录时已写入）
        Object tid = StpUtil.getSessionByLoginId(userId).get("tenantId");
        if (tid == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        attributes.put("userId", userId);
        attributes.put("tenantId", ((Number) tid).longValue());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest req, ServerHttpResponse res,
                               WebSocketHandler h, Exception ex) {}

    private String extractToken(ServerHttpRequest req) {
        // 优先从 query string ?token=xxx 取（浏览器原生 ws API 不支持自定义 header）
        String query = req.getURI().getQuery();
        if (query != null) {
            for (String kv : query.split("&")) {
                if (kv.startsWith("token=")) return kv.substring(6);
            }
        }
        List<String> auth = req.getHeaders().get("Authorization");
        return (auth != null && !auth.isEmpty()) ? auth.get(0) : null;
    }
}
```

注册：在现有 WebSocket 配置类中将该 interceptor 加入注册：

```java
registry.addHandler(notifyHandler, "/ws/notify")
        .addInterceptors(tenantHandshakeInterceptor)
        .setAllowedOriginPatterns("*");
```

### 3.7 TenantAuditService

```java
@Service
@RequiredArgsConstructor
public class TenantAuditService {
    private final AuditLogMapper auditLogMapper;

    public void recordViolationAttempt(long userId, long actualTenantId,
                                        HttpServletRequest req, String reason, String detail) {
        AuditLog log = new AuditLog();
        log.setOperatorId(userId);
        log.setAction("TENANT_VIOLATION_ATTEMPT");
        log.setTargetType("TENANT");
        log.setIpAddress(getClientIp(req));
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("uri", req.getRequestURI());
        d.put("method", req.getMethod());
        d.put("reason", reason);
        d.put("detail", detail);
        d.put("actualTenantId", actualTenantId);
        try {
            log.setDetail(new ObjectMapper().writeValueAsString(d));
        } catch (JsonProcessingException ignored) {}
        auditLogMapper.insert(log);
    }

    private String getClientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
```

## 4. UserService.login 改造

### 4.1 改动要点

1. 查询条件由 `email` 改为 `(tenant_id, email)`，租户来自 `TenantContext.getTenantId()`（此时 Filter 已经把 multi 模式的子域名/header 解析结果写入）。
2. 所有失败分支（用户不存在、密码错误、账号封禁、跨租户尝试）统一返回新错误码 `INVALID_CREDENTIALS`，统一文案。
3. 登录成功时把 `tenantId` 与 `tenantCode` 写入 Sa-Token Session，作为后续请求的权威来源。
4. 时序攻击缓解：登录路径中无论用户是否存在都执行一次 BCrypt 校验（用一个固定 dummy hash），保证响应时间近似。

### 4.2 改造后骨架

```java
private static final String DUMMY_BCRYPT_HASH =
        "$2a$10$abcdefghijklmnopqrstuvCfA8yY8Vp1jLY7K8ZWzqxRXY3yLY7K8";  // 固定值，仅用于消耗 CPU 时间

public UserVO login(LoginRequest req) {
    long tid = TenantContext.getTenantId();
    User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
            .eq(User::getTenantId, tid)
            .eq(User::getEmail, req.getEmail()));

    boolean ok;
    if (user == null) {
        // 防时序攻击：仍执行一次 BCrypt
        BCrypt.checkpw(req.getPassword(), DUMMY_BCRYPT_HASH);
        ok = false;
    } else if (user.getStatus() == 0) {
        BCrypt.checkpw(req.getPassword(), user.getPasswordHash());
        ok = false;
    } else {
        ok = BCrypt.checkpw(req.getPassword(), user.getPasswordHash());
    }

    if (!ok) {
        throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
    }

    // Sa-Token 登录
    StpUtil.login(user.getId());
    SaSession session = StpUtil.getSession();
    session.set("userId", user.getId());
    session.set("role", user.getRole());
    session.set("tenantId", user.getTenantId());          // 新增：权威租户来源
    session.set("tenantCode", lookupTenantCode(user.getTenantId()));  // 新增

    // …积分奖励、最后登录时间…

    UserVO vo = toVO(user);
    vo.setTenantId(user.getTenantId());                   // 新增
    vo.setTenantCode(session.get("tenantCode"));         // 新增
    return vo;
}
```

### 4.3 错误码扩展

`ErrorCode` 新增：

```java
INVALID_CREDENTIALS(40101, "邮箱或密码错误"),
TENANT_NOT_RESOLVED(40010, "无法识别租户"),
TENANT_NOT_FOUND(40011, "租户不存在或已停用"),
```

`USER_NOT_FOUND`、`WRONG_PASSWORD`、`USER_BANNED` 不删除，但**仅保留给已认证管理员操作使用**（如管理员封禁/查询用户）。`AuthController` 不再返回这三个错误码。

## 5. 配置变更

### 5.1 application.yml（增强）

```yaml
tenant:
  mode: standalone               # standalone | multi
  standalone-tenant-id: 1        # standalone 模式占位 id（必须等于 tenants 表中 status=1 的某条记录）
  root-domain: campusforum.com   # multi 模式子域名解析依据 (如 fudan.campusforum.com)
  allow-header-fallback: true    # multi 模式是否接受 X-Tenant-Id 作为 auth 接口的回退
  cache:
    max-size: 1024
    ttl: 60s
```

### 5.2 注释规范化

`MyBatisPlusConfig` 中 `TenantLineInnerInterceptor` 的注释改为：

```java
// 多租户 SQL 自动改写：始终注册，由 TenantContext.getTenantId() 决定运行期 tenant_id。
// - standalone 模式：所有请求 TenantContext = standaloneTenantId，写入 SQL 的 tenant_id = 该值
// - multi 模式：TenantContext 由 TenantResolutionFilter 根据 Sa-Token Session/子域名/X-Tenant-Id 解析得到
// - TenantContext 为 null 时：直接抛 IllegalStateException，禁止静默降级
```

并改造 handler：

```java
@Override
public Expression getTenantId() {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
        // 修复 C3：禁止降级到 1L
        throw new IllegalStateException(
            "TenantContext is null. This indicates a missing TenantResolutionFilter "
            + "or an entry path that bypassed it (e.g., scheduled task without explicit TenantContext setup).");
    }
    return new LongValue(tenantId);
}
```

## 6. 启动期校验

新增 `TenantStartupValidator`（实现 `ApplicationRunner`），启动时执行：

1. **standalone 模式**：检查 `tenants` 表存在 `(id=standaloneTenantId, status=1)`。若不存在，启动失败并打印明确错误。
2. **multi 模式**：检查 `tenants` 表至少有 1 条 `status=1` 记录，且 `root-domain` 配置非空。

```java
@Component
@RequiredArgsConstructor
public class TenantStartupValidator implements ApplicationRunner {
    private final TenantProperties props;
    private final TenantMapper tenantMapper;

    @Override
    public void run(ApplicationArguments args) {
        if (props.getMode() == TenantMode.STANDALONE) {
            Tenant t = tenantMapper.selectById(props.getStandaloneTenantId());
            if (t == null || t.getStatus() == null || t.getStatus() != 1) {
                throw new IllegalStateException(
                    "Standalone mode requires tenants table to contain id="
                    + props.getStandaloneTenantId() + " with status=1. "
                    + "Run the bootstrap migration first.");
            }
        } else {
            long activeCount = tenantMapper.selectCount(
                    new LambdaQueryWrapper<Tenant>().eq(Tenant::getStatus, 1));
            if (activeCount == 0) {
                throw new IllegalStateException("Multi mode requires at least one active tenant.");
            }
            if (props.getRootDomain() == null || props.getRootDomain().isBlank()) {
                throw new IllegalStateException("Multi mode requires tenant.root-domain to be set.");
            }
        }
    }
}
```

## 7. 数据迁移

### 7.1 bootstrap migration（`db/migrations/V1__bootstrap_default_tenant.sql`）

```sql
INSERT INTO tenants (id, code, name, status, created_at, updated_at)
VALUES (1, 'default', '默认租户', 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE status = 1;
```

确保现有 standalone 部署在升级时不破坏数据。schema.sql 也加上同样的 INSERT，作为新部署的初始数据。

### 7.2 错误码扩展（不需要 SQL 迁移，仅 Java enum 新增）

## 8. 前端改造

### 8.1 frontend/src/api/request.ts

去除 `localStorage.tenantId` 的读取（已认证请求不需要）。auth 接口在 multi 模式下子域名已经携带租户信息，无需头；纯 API 客户端如有需要可在 `register/login` 之前显式调用 `/api/v1/tenant/resolve?code=xxx` 来锁定本次会话的租户。

```typescript
instance.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = token;
  }
  // 移除：X-Tenant-Id 不再由前端注入
  return config;
});
```

### 8.2 stores/auth.ts

登录响应中 `tenantId` 与 `tenantCode` 存入 Pinia + localStorage，仅用于 UI 展示（如 header 显示当前学校）。

### 8.3 WebSocket 客户端（用于 OS-2 spec 的衔接）

WebSocket 连接 URL 携带 token：`new WebSocket('/ws/notify?token=' + saTokenValue)`。本 spec 仅保证后端握手拦截器正确处理；前端协议替换在 `websocket-protocol-fix` spec 中完成。

## Testing Strategy

### 9.1 测试金字塔

| 层 | 工具 | 覆盖 |
|----|------|------|
| 单元测试 | JUnit 5 + Mockito | TenantResolver 各分支、ActiveTenantCache 命中/失效、UserService.login 错误码统一 |
| 集成测试 | Spring Boot Test + Testcontainers/MySQL + MockMvc | AC-1.x、AC-2.x、AC-3.x、AC-4.x、AC-7.x |
| WebSocket E2E | Spring WebSocket + StandardWebSocketClient | AC-5.x |
| 静态检查 | 自定义 ArchUnit 规则 + grep | AC-6.1（注释/约束一致性） |

### 9.2 关键集成测试场景

#### TenantHttpIsolationIT（multi 模式）

```java
@SpringBootTest
@TestPropertySource(properties = {"tenant.mode=multi", "tenant.root-domain=test.local"})
@Testcontainers
class TenantHttpIsolationIT {
    @Container static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withInitScript("schema.sql");

    @Autowired MockMvc mvc;
    long tenantA, tenantB;
    String tokenA;

    @BeforeEach
    void setUp() { /* 创建两个租户，租户 A 注册一个用户并登录 */ }

    @Test
    void rejectCrossTenantHeaderManipulation() throws Exception {
        mvc.perform(get("/api/v1/users/me")
                .header("Authorization", tokenA)
                .header("X-Tenant-Id", tenantB))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(51001));
        // 同时验证 audit_logs 多了一条 TENANT_VIOLATION_ATTEMPT
    }

    @Test
    void allowAuthenticatedRequestWithoutHeader() throws Exception {
        mvc.perform(get("/api/v1/users/me").header("Authorization", tokenA))
            .andExpect(status().isOk());
    }

    @Test
    void allowAuthenticatedRequestWithMatchingHeader() throws Exception {
        mvc.perform(get("/api/v1/users/me")
                .header("Authorization", tokenA)
                .header("X-Tenant-Id", tenantA))
            .andExpect(status().isOk());
    }

    @Test
    void rejectLoginWithUnknownTenant() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                .contentType(APPLICATION_JSON)
                .content("{\"email\":\"a@x\",\"password\":\"x\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(40010));
    }

    @Test
    void rejectLoginWithCrossTenantEmail() throws Exception {
        // tenantA 入口用 tenantB 的 email 登录，必须失败且返回 INVALID_CREDENTIALS
        mvc.perform(post("/api/v1/auth/login")
                .header("Host", "tenantA-code.test.local")
                .contentType(APPLICATION_JSON)
                .content("{\"email\":\"user-of-b@x\",\"password\":\"correct-pwd-in-b\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(40101));
    }
}
```

#### StandaloneModeIT

验证 AC-3.x：standalone 模式下任何 `X-Tenant-Id`/Host 都被忽略，所有写入 `tenant_id=1`。

#### TenantBindingTimingTest

验证 AC-2.4 时序攻击缓解：
- 同一 email 不存在 vs 存在但密码错误，响应时间均值差异在 ±5% 内。

#### WebSocketHandshakeIT

```java
@Test
void rejectHandshakeWithoutToken() { ... }
@Test
void acceptHandshakeWithValidToken_attributesContainTenantId() { ... }
```

### 9.3 既有 18 个 Service 测试的迁移

由于现在 `TenantContext` 必须在请求开始前就被设置，纯 Service 单元测试可能在 setUp 时丢失上下文。需要：

- 在 `BaseIntegrationTest`（新增）中提供 `@BeforeEach setTenantContext()`，模拟 Filter 行为
- 现有 `TenantIsolationTest` 的写法（手动 `TenantContext.setTenantId(2L)`）仍然有效，无需改动
- `UserServiceTest` 中的 `shouldRejectWrongPassword`、`shouldLoginWithCorrectPassword` 中错误码断言需要从 `WRONG_PASSWORD` 改为 `INVALID_CREDENTIALS`，并合并"邮箱不存在"测试（这是 spec 显式接受的破坏性变更，记入 tasks 的迁移项）

## 10. 风险与边界

### 10.1 风险点

| 风险 | 缓解 |
|------|------|
| 启动期校验失败导致服务无法启动 | 错误信息明确，运维文档提供 bootstrap migration 步骤 |
| Caffeine 缓存导致租户停用后短暂仍可访问（最长 60s） | 在 `TenantService.toggleStatus()` 中主动调用 `ActiveTenantCache.evict()` |
| `IllegalStateException` from MyBatisPlusConfig 暴露给前端 | `GlobalExceptionHandler` 增加 `@ExceptionHandler(IllegalStateException.class)` 兜底返回 500 + 通用消息 |
| 既有运行中的 standalone 部署升级时 `tenants` 表为空 | bootstrap migration 自动 INSERT (id=1) |
| 后台定时任务无 HTTP 请求，TenantContext 未设置导致 SQL 异常 | 后续 spec 处理（`scheduled-task-tenant-context`）；本 spec 仅记录该情况会触发 IllegalStateException 而非数据污染，是预期行为 |
| 子域名解析在 dev 环境（localhost）不工作 | dev 环境保持 standalone；multi 模式仅用于 staging/prod |

### 10.2 性能影响

- Filter 增加一次缓存查找：缓存命中 O(1)，未命中 1 次 DB 查询，60s TTL 后自动刷新
- Sa-Token Session 读取：原本就是每请求一次，无新增开销
- BCrypt dummy 校验：仅在登录失败路径，单次 ~80ms，与正常路径一致

### 10.3 兼容性

- standalone 模式（默认）：用户行为完全不变，对外 API 无变化（除登录错误码外）
- multi 模式：是新功能，无既有用户被影响
- 错误码 `INVALID_CREDENTIALS` 是 breaking change，但当前代码库与文档仍处于早期阶段，可接受

## 11. 实现顺序与回归保护

实现严格按以下顺序，每步独立可验证：

1. **基础设施层**：`TenantMode` enum、`TenantProperties`、`TenantResolver` 接口与两个实现、`ActiveTenantCache`、`TenantStartupValidator`、错误码扩展、bootstrap migration
2. **替换拦截器**：新增 `TenantResolutionFilter`（OncePerRequestFilter），删除原 `TenantInterceptor`，调整 `WebMvcConfig`；`MyBatisPlusConfig` 注释/handler 同步更新
3. **绑定校验**：`TenantBindingCheckInterceptor` 注册在 Sa-Token 之后；`TenantAuditService` 实现
4. **登录改造**：`UserService.login()` 复合查询 + 统一错误码 + Session 写入 tenantId
5. **WebSocket 握手**：`TenantHandshakeInterceptor` + WebSocket 配置注册
6. **前端联动**：`request.ts` 移除 `X-Tenant-Id` 注入，登录响应携带 tenantId/Code
7. **测试**：Testcontainers 集成测试、WebSocket 集成测试，确认所有 AC + 既有测试通过
8. **文档与 CI**：注释一致性校验脚本、CONTRIBUTING.md 增加 multi 模式部署说明

每一步完成后立即跑 `mvn test`，确认 P5（既有测试）依然通过；除非该步明确包含已声明的破坏性迁移（如 `INVALID_CREDENTIALS` 替换 `WRONG_PASSWORD`），否则不得有失败。
