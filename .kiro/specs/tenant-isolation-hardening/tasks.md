# Implementation Plan: tenant-isolation-hardening

## Overview

本任务清单按 8 个 Phase 实施租户隔离硬化，每个 Phase 独立可验证、可回滚。Phase 1-3 是基础设施层，Phase 4 含一处破坏性迁移（INVALID_CREDENTIALS 替代细分错误码），Phase 5-6 解决 WebSocket 与前端联动，Phase 7-8 是端到端测试与文档。

## Tasks

## 实施原则

- 每个 task 完成后必须 `mvn test` 全绿（除非任务标注「破坏性迁移」）
- 任务编号为 DAG 依赖顺序；同一编号下子项可并行
- 所有源代码注释、commit message、测试名一律使用项目惯用风格（中文注释 + 英文标识符）

## Phase 1 — 配置基础设施（无功能改动）

- [x] 1. 引入 `TenantMode` enum 与 `TenantProperties` 配置类
  - 1.1 在 `tenant` 包下新增 `TenantMode.java`，包含 `STANDALONE`, `MULTI`
  - 1.2 在 `tenant` 包下新增 `TenantProperties.java`，使用 `@ConfigurationProperties(prefix = "tenant")`，字段：`mode`、`standaloneTenantId`、`rootDomain`、`allowHeaderFallback`、内嵌 `Cache`
  - 1.3 在主类或 infra 配置类上加 `@EnableConfigurationProperties(TenantProperties.class)`
  - 1.4 更新 `application.yml`：补全 `standalone-tenant-id`、`root-domain`、`allow-header-fallback`、`cache.*` 字段，注释明确 standalone 与 multi 的语义
  - 1.5 单元测试 `TenantPropertiesBindingTest` 验证 yml 绑定正确

- [x] 2. 错误码扩展
  - 2.1 `ErrorCode` 新增 `INVALID_CREDENTIALS(40101)`、`TENANT_NOT_RESOLVED(40010)`、`TENANT_NOT_FOUND(40011)`
  - 2.2 既有 `WRONG_PASSWORD`、`USER_BANNED` 在登录路径不再使用，但保留枚举值给管理员操作（在 enum 注释中标注）
  - 2.3 `GlobalExceptionHandler` 新增 `@ExceptionHandler(IllegalStateException.class)`，返回 500 + 通用消息（兜底 TenantLineHandler 抛出的状态异常）

- [x] 3. 数据 bootstrap migration
  - 3.1 新增 `db/migrations/V1__bootstrap_default_tenant.sql`：`INSERT ... ON DUPLICATE KEY UPDATE` 保证 `(id=1, code='default', status=1)`
  - 3.2 在 `db/schema.sql` 末尾追加同样的 INSERT，确保新部署初始化时含默认租户
  - 3.3 在 deploy/docker-compose.yml 的 mysql 初始化卷中确认 schema.sql 已挂载（已挂载，仅文档化）

## Phase 2 — 租户解析器与缓存

- [x] 4. 实现 `ActiveTenantCache`
  - 4.1 在 `tenant.cache` 包下新增 `ActiveTenantCache.java`，使用 Caffeine `LoadingCache<Long, Optional<Tenant>>` 与 `LoadingCache<String, Optional<Tenant>>`
  - 4.2 提供 `isActive(long)`、`findIdByCode(String)`、`getCode(long)`、`evict(long, String)` 四个方法
  - 4.3 单元测试 `ActiveTenantCacheTest`：命中/未命中/失效后再读
  - **Validates: Property 2**

- [x] 5. 实现 `TenantResolver` 接口与异常
  - 5.1 新增 `tenant.resolver.TenantResolver` 接口与 `ResolutionResult` record
  - 5.2 新增 `tenant.resolver.TenantNotResolvedException`，附带原因 enum
  - 5.3 新增 `StandaloneTenantResolver`，标注 `@ConditionalOnProperty(name="tenant.mode", havingValue="standalone", matchIfMissing=true)`
  - 5.4 新增 `MultiTenantResolver`，标注 `@ConditionalOnProperty(name="tenant.mode", havingValue="multi")`，按设计文档实现 Sa-Token Session → 子域名 → X-Tenant-Id 优先级
  - 5.5 单元测试覆盖各分支：
    - `StandaloneTenantResolverTest`：忽略 header/host，恒返回 standaloneTenantId
    - `MultiTenantResolverTest`：mock Sa-Token + ActiveTenantCache，覆盖 4 种解析路径与 5 种失败场景

- [x] 6. 启动期校验
  - 6.1 新增 `TenantStartupValidator implements ApplicationRunner`，按设计实现 standalone/multi 两种校验
  - 6.2 集成测试 `TenantStartupValidatorIT`：删除 `tenants` 表中 id=1 的记录，期望 standalone 模式启动失败抛 IllegalStateException

## Phase 3 — 拦截器替换与 SQL 改写硬化

- [x] 7. 新增 `TenantResolutionFilter`（替代 TenantInterceptor）
  - 7.1 在 `tenant.filter` 包下新增 `TenantResolutionFilter extends OncePerRequestFilter`，注入 `TenantResolver`
  - 7.2 排除路径：`/actuator/`、`/swagger-ui/`、`/v3/api-docs/`、`/ws/`
  - 7.3 解析失败时直接写 JSON 错误响应（HTTP 400 + `TENANT_NOT_RESOLVED`），不进入 Spring DispatcherServlet
  - 7.4 注册 Filter Bean：`FilterRegistrationBean<TenantResolutionFilter>`，order 设 `Ordered.HIGHEST_PRECEDENCE + 50`
  - 7.5 删除既有 `TenantInterceptor` 类与 `WebMvcConfig` 中 `addInterceptor(tenantInterceptor)` 的注册（保留 WebMvcConfig 类骨架，可能后续还要加别的拦截器）
  - 7.6 集成测试 `TenantResolutionFilterIT`：standalone 模式下 X-Tenant-Id 被忽略；multi 模式下未提供任何来源时 400

- [x] 8. 硬化 `MyBatisPlusConfig.TenantLineHandler`
  - 8.1 修改 handler `getTenantId()`：TenantContext 为 null 时抛 `IllegalStateException`，禁止降级为 1L
  - 8.2 重写注释，明确「无条件注册，由 TenantContext 决定运行期值」+ 与 standalone/multi 的关系
  - 8.3 单元测试 `TenantLineHandlerTest`：null 时抛异常；非 null 时返回正确 LongValue
  - **Validates: Property 4, Property 7**

- [x] 9. 已认证请求绑定校验
  - 9.1 在 `tenant.audit` 包下新增 `TenantAuditService`，提供 `recordViolationAttempt(...)` 方法，写入 `audit_logs` 表
  - 9.2 在 `tenant.interceptor` 包下新增 `TenantBindingCheckInterceptor implements HandlerInterceptor`，按设计文档检测 X-Tenant-Id vs Session 的不一致
  - 9.3 在 `WebMvcConfig` 中注册该拦截器，路径 `/api/v1/**`，排除 `/api/v1/auth/**`
  - 9.4 集成测试 `TenantBindingCheckIT`：用户 A 携带 tenantB 的 X-Tenant-Id → 403 + audit_logs 多一条
  - **Validates: Property 1, Property 6**

## Phase 4 — 登录改造（含破坏性迁移）

- [x] 10. `UserService.login` 改造
  - 10.1 改 `LambdaQueryWrapper`：增加 `eq(User::getTenantId, TenantContext.getTenantId())`
  - 10.2 失败分支统一抛 `BusinessException(ErrorCode.INVALID_CREDENTIALS)`，不再使用 `USER_NOT_FOUND`/`WRONG_PASSWORD`/`USER_BANNED`
  - 10.3 引入固定 `DUMMY_BCRYPT_HASH` 常量；用户不存在时仍执行 `BCrypt.checkpw(req.getPassword(), DUMMY_BCRYPT_HASH)` 防时序攻击
  - 10.4 登录成功时把 `tenantId` 写入 Sa-Token Session：`session.set("tenantId", user.getTenantId())`，同时写 `tenantCode`
  - 10.5 `UserVO` 增加 `tenantId` 与 `tenantCode` 字段，登录响应携带

- [x] 11. **破坏性迁移**：更新既有测试
  - 11.1 `UserServiceTest.shouldRejectWrongPassword`：断言改为 `INVALID_CREDENTIALS`
  - 11.2 删除/合并依赖 `USER_NOT_FOUND`/`WRONG_PASSWORD` 的细分登录测试，新增 `shouldReturnInvalidCredentialsForAllLoginFailures` 覆盖 4 种失败场景
  - 11.3 提交说明在 commit message 中明确：BREAKING CHANGE - login error codes unified

- [x] 12. 登录时序攻击缓解验证
  - 12.1 新增 `LoginTimingTest`：循环 N=100 次，分别测试"邮箱不存在"和"邮箱存在但密码错"两种情况，记录响应时间
  - 12.2 断言：`|mean(rt_a) - mean(rt_b)| / max(mean) < 0.10`
  - **Validates: Property 5**

- [x] 13. AuthController 修复（兼顾 OS-4，最小必要项）
  - 13.1 `SaTokenConfig` 排除路径增加 `/api/v1/auth/forgot-password`、`/api/v1/auth/reset-password`、`/api/v1/auth/me` 中无需登录的部分（`/me` 是登录态接口，需保留拦截）
  - 13.2 验证 forgot-password 在未登录情况下返回 200（既有测试若有则补全）
  - **Note**: 完整的 SaToken/Tenant 拦截路径对齐由 `auth-and-interceptor-paths` spec 处理；本 task 仅修复 forgot/reset 这两个会被本 spec 改造影响的路径

## Phase 5 — WebSocket 握手期租户绑定

- [x] 14. 新增 `TenantHandshakeInterceptor`
  - 14.1 在 `tenant.websocket` 包下新增 `TenantHandshakeInterceptor implements HandshakeInterceptor`
  - 14.2 实现 `beforeHandshake`：从 `?token=` query 或 `Authorization` header 取 Sa-Token，验证有效性，从 Sa-Token Session 读出 `tenantId` 写入 attributes
  - 14.3 token 缺失/无效/session 缺 tenantId → 返回 401，拒绝握手
  - 14.4 在 WebSocket 配置类中将其加入 `addHandler("/ws/notify").addInterceptors(tenantHandshakeInterceptor)`
  - **Note**: 本 spec 不解决前端 socket.io vs 后端原生 WS 协议不匹配（OS-2），测试用 `StandardWebSocketClient` 验证后端
  - **Validates: AC-5.1, AC-5.2**

- [x] 15. WebSocket 集成测试
  - 15.1 新增 `WebSocketHandshakeIT`：使用 `StandardWebSocketClient` 携带合法/非法 token 各连接一次
  - 15.2 验证 attributes 中 `userId`、`tenantId` 正确
  - 15.3 验证非法 token 时握手失败（HTTP 401）

## Phase 6 — 前端联动

- [x] 16. 前端请求拦截器去 X-Tenant-Id
  - 16.1 修改 `frontend/src/api/request.ts`：移除从 localStorage 读取 tenantId 并注入 header 的逻辑
  - 16.2 登录响应中收到的 `tenantId`、`tenantCode` 存入 `stores/auth.ts`，仅用于 UI 展示

- [x] 17. 前端登录错误码兼容
  - 17.1 检查 `Login.vue` 是否对登录错误做差异化提示；统一为「邮箱或密码错误」
  - 17.2 `auth.ts` 中相关 mock 数据/类型同步更新

- [x] 18. WebSocket 客户端 token 注入（最小必要项）
  - 18.1 修改 WebSocket 连接 URL 为 `/ws/notify?token={SaToken}`
  - **Note**: 本 spec 仅保证 token 能正确投递，前端 socket.io→原生 WS 切换由 OS-2 spec 处理；如果前端目前用 socket.io 客户端，本 task 仅文档化"待 OS-2 spec 联动"，不强求修通

## Phase 7 — 端到端集成测试

- [x] 19. 测试基础设施
  - 19.1 新增 `BaseIntegrationTest` 抽象类，使用 `@Testcontainers` + 共享 `MySQLContainer`
  - 19.2 提供 `@BeforeEach setTenantContext()` 工具方法，模拟 Filter 写入 TenantContext
  - 19.3 `application-test.yml` 设置 `tenant.mode=standalone`（默认）；通过 `@TestPropertySource` 在 multi 测试中覆盖

- [x] 20. multi 模式 HTTP 越权集成测试
  - 20.1 新增 `TenantHttpIsolationIT`，按设计文档列出的 4 个用例
  - 20.2 包含 audit_logs 写入断言（rejectCrossTenantHeaderManipulation 后 count + 1）
  - **Validates: Property 1, Property 6**

- [x] 21. multi 模式登录用例
  - 21.1 新增 `MultiTenantLoginIT`：覆盖 AC-2.1 ~ AC-2.5
  - 21.2 子域名解析（用 `MockMvc.perform(...).header("Host", "tenantA.test.local")`）
  - 21.3 X-Tenant-Id 回退、缺失（400）、跨租户 email（400 INVALID_CREDENTIALS）
  - **Validates: Property 2**

- [x] 22. standalone 模式回归测试
  - 22.1 新增 `StandaloneModeIT`：客户端发任意 X-Tenant-Id 与 Host，验证写入 tenant_id=1
  - 22.2 验证既有 `TenantIsolationTest` 仍通过（不修改源文件）
  - **Validates: Property 3**

- [x] 23. 字典表非租户隔离回归
  - 23.1 新增 `TenantIgnoreTablesTest`：验证 tenants/achievements/sensitive_words 三张表查询 SQL 不含 tenant_id 条件
  - 23.2 实现方式：使用 P6Spy 或自定义 MyBatis 拦截器记录 SQL，断言不出现 `tenant_id =`
  - **Validates: Property 7**

- [x] 24. WebSocket 握手集成测试
  - 24.1 新增 `WebSocketHandshakeIT`：MockMvc 启动 + `StandardWebSocketClient`
  - 24.2 covers AC-5.1（合法 token → 握手成功 + attributes 正确）
  - 24.3 covers AC-5.2（非法 token → 401）

- [x] 25. 全量回归
  - 25.1 执行 `mvn test`，确认所有既有 + 新增测试全绿
  - 25.2 修复 task 11 之外的回归（不应该有，若有则记录原因）

## Phase 8 — 文档与 CI

- [x] 26. 注释一致性 ArchUnit 规则
  - 26.1 新增 `tenant.archunit.TenantArchitectureTest`，断言：
    - 没有任何 production 代码（除 TenantBindingCheckInterceptor）调用 `request.getHeader("X-Tenant-Id")` 修改 TenantContext
    - 没有任何代码使用 `TenantContext.setTenantId(1L)` 等硬编码值（除 StandaloneTenantResolver）
  - 26.2 在 CI 中作为测试一部分执行（`mvn test` 已覆盖）
  - **Validates: Property 1**

- [x] 27. CONTRIBUTING.md 增加多租户部署文档
  - 27.1 新增 `## 多租户部署` 章节：standalone 默认行为、multi 切换步骤、子域名 DNS 配置示例、bootstrap migration 注意事项
  - 27.2 引用 `~/.testcontainers.properties` 配置说明（如何指向 VM Docker daemon）

- [x] 28. .gitignore 与示例文件
  - 28.1 在仓库根新增 `.testcontainers.properties.example`，附说明
  - 28.2 `.gitignore` 添加排除项：`.testcontainers.properties`（user-level 不应该误提交，本仓库根也不期望）

- [x] 29. CI workflow 更新
  - 29.1 检查 `.github/workflows/ci.yml`：testcontainers 在 GitHub runner 中天然有 docker，不需要 host override
  - 29.2 在 CI 测试 step 之前确保 `db/migrations/V1__bootstrap_default_tenant.sql` 与 schema.sql 都被加载（schema.sql 已含 INSERT，无需额外步骤）

- [x] 30. PR 描述与变更日志
  - 30.1 PR 描述列出：BREAKING CHANGES（INVALID_CREDENTIALS）、新配置项、迁移步骤
  - 30.2 在 README.md 或 CHANGELOG.md 记录本次安全修复

## 收尾验证

- [x] 31. 完整 AC 走查
  - 31.1 逐条对照 bugfix.md 中 AC-1.1 ~ AC-7.2，确认每条都有自动化测试覆盖且通过
  - 31.2 把走查结果填入 PR 描述

## Task Dependency Graph

```json
{
  "waves": [
    { "wave": 1, "tasks": ["1", "2", "3"], "parallelizable": true },
    { "wave": 2, "tasks": ["4"], "depends_on_waves": [1] },
    { "wave": 3, "tasks": ["5"], "depends_on_waves": [2] },
    { "wave": 4, "tasks": ["6"], "depends_on_waves": [3] },
    { "wave": 5, "tasks": ["7", "8"], "depends_on_waves": [4], "parallelizable": true },
    { "wave": 6, "tasks": ["9"], "depends_on_waves": [5] },
    { "wave": 7, "tasks": ["10"], "depends_on_waves": [6] },
    { "wave": 8, "tasks": ["11", "12", "13"], "depends_on_waves": [7], "parallelizable": true },
    { "wave": 9, "tasks": ["14", "16", "17"], "depends_on_waves": [7], "parallelizable": true },
    { "wave": 10, "tasks": ["15", "18"], "depends_on_waves": [9], "parallelizable": true },
    { "wave": 11, "tasks": ["19"], "depends_on_waves": [8, 10] },
    { "wave": 12, "tasks": ["20", "21", "22", "23", "24"], "depends_on_waves": [11], "parallelizable": true },
    { "wave": 13, "tasks": ["25"], "depends_on_waves": [12] },
    { "wave": 14, "tasks": ["26", "27", "28", "29"], "depends_on_waves": [13], "parallelizable": true },
    { "wave": 15, "tasks": ["30", "31"], "depends_on_waves": [14] }
  ]
}
```

依赖摘要（人类阅读）：

- Phase 1（task 1-3）零依赖，可并行
- Phase 2-3 顺序：cache → resolver → validator → filter/handler → binding check
- Phase 4（登录改造）依赖绑定校验完成
- Phase 5/6（WebSocket、前端）依赖登录改造（需要 Session 中的 tenantId）
- Phase 7（集成测试）必须等核心代码全部就绪
- Phase 8（文档/CI）最后做

## Notes

- Task 11 是显式声明的破坏性迁移，commit 必须带 `BREAKING CHANGE:` 标注
- Task 18 依赖 OS-2 spec，本 spec 内只做最小投递改动
- Task 13 与 OS-4 spec 有交叠，本 spec 仅修复会被本次改造影响到的最小集合
- 所有集成测试默认通过 Testcontainers 在 Ubuntu VM 的 Docker daemon 运行（用户级配置已就绪）
