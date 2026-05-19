# Bugfix: tenant-isolation-hardening

## Introduction

CampusForum 声称提供「行级多租户隔离」，但当前实现存在多个隔离绕过与语义不一致问题：

- 已认证请求的租户上下文完全由客户端 `X-Tenant-Id` 头决定，无服务端二次校验，导致跨租户越权
- `TenantLineInnerInterceptor` 注释声明「仅 multi 模式启用」，实际无条件注册，注释与行为失配
- multi 模式下租户解析失败时静默降级为 `tenant_id=1`，污染默认租户数据
- 用户登录仅按 email 查找，可在租户 A 入口用属于租户 B 的账号登录
- WebSocket 等非标准入口绕过租户拦截器，租户上下文缺失

本 spec 通过 Bug Condition 方法论严格定义这些缺陷，并给出可被自动化测试验证的修复目标。修复目标包括：建立"登录用户的 `tenant_id` 是会话租户上下文唯一权威来源"的不变量；让 standalone/multi 两种模式语义清晰且文档与实现一致；补全 multi 模式下子域名解析、租户存在性校验、WebSocket 握手期租户绑定等机制。

## Bug Analysis

### Current Behavior (Defect)

设系统状态变量：

- `S_user`：当前已认证用户记录，含 `S_user.id`、`S_user.tenant_id`、`S_user.role`
- `S_ctx_tenant`：请求处理期间 `TenantContext.getTenantId()` 实际返回值
- `S_header_tenant`：请求中 `X-Tenant-Id` 头的值
- `S_subdomain_tenant`：从 Host 子域名解析得到的 tenant.code 对应的 id（multi 模式下）
- `S_db_tenant_for_query`：MyBatis-Plus `TenantLineInnerInterceptor` 实际改写到 SQL 中的 `tenant_id` 字面量值
- `mode`：`tenant.mode` 配置，取值 `standalone | multi`
- `T_active`：`tenants` 表中 `status=1` 的所有 tenant id 集合

**Bug Condition C(X)**：满足以下任一子条件即视为缺陷触发。

**C1（HTTP 越权）**

```
mode = multi
∧ S_user ≠ null
∧ S_header_tenant ≠ null
∧ S_header_tenant ≠ S_user.tenant_id
∧ 服务端未拒绝请求
```

证据：
- `frontend/src/api/request.ts` 第 23 行从 `localStorage.getItem('tenantId')` 读取后直接放入请求头
- `backend/src/main/java/com/campusforum/tenant/TenantInterceptor.java` 第 36 行 `Long.parseLong(header)` 后直接 `TenantContext.setTenantId(tenantId)`，无校验
- `backend/src/main/java/com/campusforum/user/service/UserService.java` `login()` 未将 `user.tenant_id` 写入 Sa-Token Session

含义：已登录用户通过修改 `localStorage.tenantId` 即可访问/修改任意其他租户的数据。

**C2（注释与实现失配）**

```
TenantLineInnerInterceptor 的注册条件 ≠ 注释声明
```

证据：
- `backend/src/main/java/com/campusforum/infra/MyBatisPlusConfig.java` 第 28 行注释「多租户 SQL 自动改写（仅 multi 模式启用）」
- 同文件第 29 行 `interceptor.addInnerInterceptor(...)` 无条件注册

含义：维护者依赖注释做修改时会引入错误假设。

**C3（异常租户降级污染）**

```
mode = multi
∧ S_ctx_tenant = null
∧ S_db_tenant_for_query = 1L
```

证据：
- `MyBatisPlusConfig.java` 第 33 行 `return new LongValue(tenantId != null ? tenantId : 1L)`

含义：multi 模式下租户解析失败时静默降级为默认租户 `1L`，导致异常请求的数据被错误归入默认租户，污染数据。

**C4（登录跨租户）**

```
mode = multi
∧ S_subdomain_tenant ≠ null  (或登录请求带 X-Tenant-Id)
∧ 用户提交 email/password 登录
∧ 存在用户 U 满足 U.email = email ∧ U.tenant_id ≠ S_subdomain_tenant
∧ 系统接受 U 登录
```

证据：
- `UserService.java` `login()` 仅以 `LambdaQueryWrapper<User>().eq(User::getEmail, req.getEmail())` 查询，未约束 `tenant_id`
- 数据库 `uk_tenant_email (tenant_id, email)` 允许同 email 在不同租户存在

含义：用户能在租户 A 入口用属于租户 B 的账号登录，破坏租户边界。

**C5（绕过 HTTP 拦截器的入口缺失租户上下文）**

```
连接路径 ∈ { WebSocket /ws/notify, 文件下载等 }
∧ S_ctx_tenant = null
∧ 业务逻辑被执行
```

证据：
- `WebMvcConfig.java` 仅对 `/api/**` 注册 `TenantInterceptor`
- `NotifyWebSocketHandler.java` 从 session attributes 取 userId 但没有任何握手拦截器写入这些属性

含义：非典型入口未建立租户上下文，导致空指针或越权数据访问。

**C6（租户存在性校验缺失）**

```
mode = multi
∧ (S_header_tenant 或 S_subdomain_tenant) ∉ T_active
∧ 服务端继续处理请求
```

证据：
- `TenantInterceptor.resolveTenantId()` 仅做 `Long.parseLong`，不查 `tenants` 表

含义：客户端可指定不存在或已停用的 tenant id，服务端仍按其执行业务。

### Expected Behavior (Correct)

修复后系统必须满足以下不变量与 acceptance criteria。

**F1（单一权威来源）**：对于已认证请求，`TenantContext.getTenantId()` 必须等于该会话登录用户的 `tenant_id`。任何 HTTP 头或客户端自报的租户 id 都不得覆盖该值。

**F2（认证-租户绑定校验）**：若已认证请求中 `X-Tenant-Id` 头存在且与登录用户的 `tenant_id` 不一致，视为越权尝试，返回 `403 FORBIDDEN`（错误码 `TENANT_VIOLATION = 51001`），并写入 `audit_logs`。

**F3（登录前租户解析显式化）**：multi 模式下 auth 接口必须按以下顺序解析租户：

1. 子域名（`{tenantCode}.{rootDomain}`，从 `tenants.code` 反查 id）
2. `X-Tenant-Id` 头（仅 auth 接口接受，且必须存在于 `T_active`）
3. 解析失败：返回 `400 BAD_REQUEST`（错误码新增 `TENANT_NOT_RESOLVED`），不允许降级到 `tenant_id=1`

**F4（standalone 模式语义清晰）**：standalone 模式下 `TenantContext.getTenantId()` 始终返回 `1L`；客户端发送的 `X-Tenant-Id` 一律忽略；`TenantLineInnerInterceptor` 仍然工作，语义为「单租户实例的占位 id」。该语义在 `application.yml` 注释 + `MyBatisPlusConfig` JavaDoc 中明确说明。

**F5（multi 模式登录跨租户拒绝 + 统一错误响应）**：multi 模式下 `UserService.login()` 必须基于 `(tenant_id, email)` 复合查询；目标租户由请求上下文确定。所有登录失败场景（用户不存在、密码错误、账号封禁、跨租户尝试）统一返回 `INVALID_CREDENTIALS`、统一文案、近似响应时间，遵循 OWASP 认证最佳实践，避免账号枚举与时序攻击。

**F6（WebSocket 握手期租户绑定）**：WebSocket 握手必须在 `HandshakeInterceptor` 中解析 Sa-Token 登录态，并把 `userId` 与 `tenantId` 写入 `WebSocketSession` attributes；后续消息处理可从 attributes 取出 tenantId 建立 `TenantContext`。

**F7（租户存在性校验）**：multi 模式下任何来源的 tenant id 在被采纳为 `S_ctx_tenant` 之前，必须命中 `T_active`；不命中时拒绝请求。该校验结果可缓存（Caffeine 短 TTL）以避免每个请求查库。

**F8（注释与实现一致）**：`MyBatisPlusConfig` 中关于 `TenantLineInnerInterceptor` 的注释必须准确描述「无条件注册，由 TenantContext 控制实际值」。

**Acceptance Criteria（EARS 格式）**

#### 6.1 已认证请求的租户绑定（针对 C1, F1, F2）

**AC-1.1** When `tenant.mode=multi` 且已登录用户 A（`tenant_id=T_A`）发起任意非 auth 请求，the system shall set `TenantContext` 为 `T_A` 而非任何客户端自报值。

**AC-1.2** When `tenant.mode=multi` 且请求头 `X-Tenant-Id=T_B` 与登录用户的 `tenant_id=T_A` 不一致 (T_A ≠ T_B)，the system shall 返回 HTTP `403`、响应体 `code=51001 (TENANT_VIOLATION)`，并在 `audit_logs` 表插入一条 `action='TENANT_VIOLATION_ATTEMPT'` 记录。

**AC-1.3** When 已认证请求未携带 `X-Tenant-Id`，the system shall 不视为错误，直接使用登录用户的 `tenant_id` 作为上下文。

#### 6.2 登录前租户解析（针对 C4, C6, F3, F5, F7）

**AC-2.1** When `tenant.mode=multi` 且 auth 请求的 Host 子域名为 `{code}.example.com` 且 `tenants.code=code, status=1` 存在，the system shall 解析 `S_ctx_tenant` 为对应 tenant id。

**AC-2.2** When `tenant.mode=multi` 且 auth 请求 Host 无可识别子域名但携带 `X-Tenant-Id=T` 且 `T ∈ T_active`，the system shall 接受 T 作为本次 auth 的 `S_ctx_tenant`。

**AC-2.3** When `tenant.mode=multi` 且 auth 请求既无可识别子域名也未提供合法 `X-Tenant-Id`（缺失或不在 `T_active`），the system shall 返回 HTTP `400`、错误码 `TENANT_NOT_RESOLVED`。

**AC-2.4** When 用户提交 `(email, password)` 登录，无论失败原因是 a) 当前租户下 email 不存在 b) 密码错误 c) 该 email 仅在其他租户存在 d) 账号被封禁，the system shall 返回 HTTP `400`、统一错误码 `INVALID_CREDENTIALS`、统一消息 `"邮箱或密码错误"`；响应、日志、响应时间均不得泄露具体失败原因（按 OWASP 认证指南，防邮箱枚举与时序攻击）。原 `USER_NOT_FOUND`、`WRONG_PASSWORD`、`USER_BANNED` 错误码在登录路径下不再使用，仅保留给已认证的管理员操作（如管理员查询用户、封禁用户）。

**AC-2.5** When 登录成功，the system shall 将 `S_user.tenant_id` 写入 Sa-Token Session 的 `tenantId` 属性。

#### 6.3 standalone 模式（针对 P1, F4）

**AC-3.1** When `tenant.mode=standalone`，the system shall 对所有请求（含 auth）设置 `TenantContext.getTenantId()=1L`，无视任何 `X-Tenant-Id` 头与子域名。

**AC-3.2** When `tenant.mode=standalone` 且通过任意 Service 写入业务数据，the system shall 持久化 `tenant_id=1`。

**AC-3.3** `application.yml` 与 `MyBatisPlusConfig` 中关于多租户的注释必须明确说明 standalone 与 multi 两种模式的语义差异。

#### 6.4 字典表与系统表（针对 P4）

**AC-4.1** Where 表名 ∈ `{tenants, achievements, sensitive_words}`，`TenantLineInnerInterceptor` shall 不向 SQL 注入 `tenant_id` 条件。

#### 6.5 WebSocket 与非标准入口（针对 C5, F6）

**AC-5.1** When 客户端建立 `/ws/notify` 连接且携带有效 Sa-Token，the system shall 在握手阶段把 `userId` 与 `tenantId` 写入 `WebSocketSession` attributes。

**AC-5.2** When `/ws/notify` 握手未携带或携带无效 token，the system shall 拒绝握手（返回 HTTP `401` 或关闭连接）。

#### 6.6 注释与实现一致性（针对 C2, F8）

**AC-6.1** 修复后 `MyBatisPlusConfig.java` 中关于 `TenantLineInnerInterceptor` 的所有注释必须与代码行为一致；通过代码审查或自动化校验脚本验证。

#### 6.7 既有功能保留（针对 P2, P3, P5, P6）

**AC-7.1** 修复完成后，`backend/src/test` 下既有 18 个 Service 测试以及 `TenantIsolationTest` 全部通过（必要时按显式迁移步骤更新）。

**AC-7.2** 前端 `localStorage.tenantId` 不再作为认证后请求的租户来源；登录响应体携带当前用户的 `tenantId` 与 `tenantCode`，前端只用于展示。

### Unchanged Behavior (Regression Prevention)

修复方案必须保留以下系统属性。任何破坏这些属性的修改都视为回归，必须在测试中显式覆盖。

**P1（standalone 模式向后兼容）**：当 `tenant.mode=standalone` 时，所有持久化记录的 `tenant_id` 仍等于 `1L`，所有查询结果与现有行为一致；现有 21 个业务域的代码无需修改即可继续工作。

**P2（自动租户字段填充）**：`BaseEntity` 与现有 mapper 在写入数据时无需手动赋值 `tenant_id`，仍由框架/拦截器自动注入。

**P3（auth 接口可用）**：`/api/v1/auth/register`、`/api/v1/auth/login`、`/api/v1/auth/forgot-password`、`/api/v1/auth/reset-password` 在用户未登录的状态下仍可访问；其中 standalone 模式下不需要客户端提供租户信息，multi 模式下租户信息从可信来源（子域名 / 已注册的 X-Tenant-Id）解析。

**P4（系统/字典表全局可见）**：`tenants`、`achievements`、`sensitive_words` 三张表保持租户无关（继续在 `TENANT_IGNORE_TABLES` 列表中）。

**P5（既有 18 个 Service 集成测试通过）**：现有 `backend/src/test` 下的所有测试（特别是 `TenantIsolationTest`）在修复后必须继续通过，或经过最小、明确的迁移后通过。

**P6（前端体验）**：登录后用户无需手动维护 `localStorage.tenantId`；前端不再依赖 `X-Tenant-Id` 自报租户；服务端响应中可携带当前租户信息供 UI 展示。

## Out of Scope

以下问题虽然存在或相关，但属于其他 spec 的范畴，不在本次修复内：

- **OS-1（并发计数器）**：积分、点赞、评论数等"先查后写"导致的丢更新——由 spec `atomic-counter-fix` 处理
- **OS-2（WebSocket 协议不一致）**：前端 `socket.io-client` 与后端原生 WebSocket 的协议层不兼容——由 spec `websocket-protocol-fix` 处理；本 spec 仅处理 WebSocket **握手期租户上下文建立**这一隔离维度
- **OS-3（dev 配置硬编码密码）**：`application-dev.yml` 中明文密码与私网 IP——由 spec `dev-config-secrets-cleanup` 处理
- **OS-4（auth 接口误拦截）**：`forgot/reset-password` 在 `SaTokenConfig` 中未排除——由 spec `auth-and-interceptor-paths` 处理；本 spec 在调整拦截器时若顺手能修则记录注意点，但不作为本 spec 的验收项

## Assumptions and Dependencies

- **A-1**：`tenants` 表在测试与生产环境中存在且 `(id=1, code='default', status=1)` 已预置，否则 standalone 模式下的现有数据无法定位归属。修复实现需确保该约束（migration 脚本或启动时校验）。
- **A-2**：Testcontainers 测试通过 `~/.testcontainers.properties` 指向 Ubuntu VM 的 Docker daemon（已配置）。
- **A-3**：Sa-Token Redis 后端可用（dev 环境的 Redis 已就绪）。

## Verification Strategy

1. **集成测试（Testcontainers + MySQL）**：覆盖 AC-1.x、AC-2.x、AC-3.x、AC-4.x。
2. **WebSocket 端到端测试**：使用 Spring 内置 `WebSocketStompClient` 或原生 `StandardWebSocketClient`，覆盖 AC-5.x。
3. **回归测试**：执行既有测试集合，验证 P1–P6。
4. **静态检查**：CI 中加入注释一致性校验脚本（grep 关键字 + 文档审查），覆盖 AC-6.1。

具体测试用例与实现路径在 `design.md` 中展开。
