# [CLAUDE.md](http://CLAUDE.md)

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

CampusForum — 基于多租户开源架构与 AI 增强的高校轻量化学习社群平台。MIT 协议开源。

## 技术栈


| 层     | 选型                                                                   |
| ----- | -------------------------------------------------------------------- |
| 前端    | Vue3 + Composition API + Vite 5 + Naive UI + Pinia + vite-plugin-pwa |
| 后端    | Java 17 + Spring Boot 3.x + MyBatis-Plus + Sa-Token                  |
| 数据库   | MySQL 8.0                                                            |
| 缓存    | Redis 7 + Caffeine（二级缓存）                                             |
| 搜索    | MeiliSearch（MySQL FULLTEXT 兜底）                                       |
| 存储    | MinIO（S3 兼容，预留 OSS 适配）                                               |
| AI    | LangChain4j + OpenAI 兼容协议                                            |
| 部署    | Docker Compose（默认） + K8s Helm（可选）                                    |
| CI/CD | GitHub Actions                                                       |


## 常用命令

**后端**（`backend/`，Spring Boot Maven 项目，JDK 21）：

```bash
export JAVA_HOME=/home/morose/.local/jdk-21.0.2
cd backend
/mnt/d/develop/apache-maven-3.9.4/bin/mvn spring-boot:run   # 启动开发服务器
/mnt/d/develop/apache-maven-3.9.4/bin/mvn test              # 运行全部单元测试
/mnt/d/develop/apache-maven-3.9.4/bin/mvn test -Dtest=XxxTest  # 运行单个测试类
```

**前端**（`frontend/`）：

```bash
cd frontend
npm run dev                      # 启动 Vite 开发服务器
npm run build                    # 生产构建 → dist/
npm run lint                     # ESLint + Prettier
```

**部署**（`deploy/`）：

```bash
cd deploy
cp .env.example .env             # 修改域名、密码、AI Key 等
bash install.sh                  # 一键部署（拉镜像 + 初始化 + 启动）
docker compose up -d             # 或直接 compose 启动
```

## 高层架构

**设计原则**：轻量优先、前后端分离、模块化分层、多租户为一等公民。

**架构模式**：阶段一采用**模块化单体（Modular Monolith）**，后端按 package 划分业务模块，模块间通过 Spring 内部 API 调用、禁止反向依赖。后续可按需拆分为微服务。

### 应用分层

```
Controller（接入层）→ Service（业务编排）→ Domain（领域模型）→ Infra（基础设施）
横向切面：鉴权 / 租户上下文 / 日志 / 限流 / 审计
```

### 后端 Package 划分（`com.campusforum`）


| package    | 职责                                                                                   | 状态  |
| ---------- | ------------------------------------------------------------------------------------ | --- |
| `common`   | 通用组件：BaseEntity, R, BusinessException, ErrorCode, GlobalExceptionHandler             | ✓   |
| `infra`    | 基础设施：MyBatisPlusConfig, CampusMetaObjectHandler, StorageService, LocalStorageService | ✓   |
| `tenant`   | 多租户：TenantContext（ThreadLocal）、TenantInterceptor                                     | ✓   |
| `security` | SaToken 鉴权配置                                                                         | ✓   |
| `user`     | 用户注册/登录/个人资料/管理员封禁/角色变更                                                              | ✓   |
| `space`    | 学习空间 CRUD、成员管理、加入/审核/退出                                                              | ✓   |
| `post`     | 帖子 CRUD、评论（含回复）、点赞/收藏切换                                                              | ✓   |
| `qa`       | 问答扩展：悬赏、采纳回答                                                                         | ✓   |
| `checkin`  | 打卡挑战、每日打卡、连续统计、排行榜                                                                   | ✓   |
| `resource` | 文件上传/下载/列表                                                                           | ✓   |
| `notify`   | 通知创建（点赞/评论/回复/采纳/申请）、列表/已读                                                           | ✓   |
| `search`   | 全局搜索（帖子 FULLTEXT + 用户/资源/空间 LIKE）                                                    | ✓   |
| `admin`    | 管理后台：仪表盘/用户/帖子/空间管理/审计日志 + StpInterface 权限映射                                         | ✓   |
| `ai`       | AI 接口抽象 + MockAiService（摘要/审核/标签推荐/智能问答）                                             | ✓   |


### 多租户设计（核心研究点）

- **双模可切换**：`tenant.mode = standalone | multi`，一键切换独立部署/多租户 SaaS
- **数据隔离**：所有业务表带 `tenant_id`，由 MyBatis-Plus `TenantLineInnerInterceptor` 自动改写 SQL
- **双保险**：SQL 插件注入 + Service 层 `tenant_id` 比对断言
- **缓存隔离**：Redis Key 格式 `{app}:t{tenantId}:{module}:{key}`
- **搜索隔离**：MeiliSearch 索引按租户分片 `post-t1`、`post-t2`
- **文件隔离**：MinIO 按 `tenant-{id}/` 分目录

### 权限模型

三级 RBAC + 资源域校验：平台层（超级管理员）→ 校级（管理员/普通用户）→ 空间级（所有者/管理员/成员）。权限粒度到接口级，以 `domain:resource:action` 命名。

### AI 可插拔设计

`AiService` 接口抽象，通过 `ai.provider` 配置切换实现（`openai` → `ollama` → `mock`）。支持 OpenAI 兼容协议的任何模型（DeepSeek、智谱、通义、本地 Ollama）。

## 工程目录（规划）

```
CampusForum/
├─ backend/                  # Spring Boot 应用
├─ frontend/                 # Vue3 + Vite
├─ deploy/                   # Docker Compose / Helm / 一键脚本
├─ docs/                     # 需求/设计/用户/二次开发文档
├─ db/                       # schema.sql + migrations
├─ .github/                  # CI/CD workflows
├─ LICENSE                   # MIT
└─ README.md
```

## 开发约定

- **提交**：Conventional Commits（`feat:` / `fix:` / `docs:` / `refactor:`）
- **分支策略**：`main`（稳定）← `develop`（集成）← `feature/`* / `fix/*`
- **统一响应格式**：`{ code: 0, message: "ok", data: {...}, traceId: "..." }`
- **错误码**：`0` 成功，`1xxxx` 业务错误，`4xxxx` 客户端错误，`5xxxx` 服务端错误
- **URL 前缀**：`/api/v1/`
- **鉴权 Header**：`Authorization: Bearer <token>`
- **多租户 Header**：`X-Tenant-Id`（仅 SaaS 模式）
- **分页**：游标分页优先（`id < lastId limit n`），避免深翻页 OFFSET
- **时间格式**：ISO 8601

## 当前状态（2026-05-12）

### 已完成


| 模块       | 内容                                            | 测试数 |
| -------- | --------------------------------------------- | --- |
| M1 用户系统  | 注册/登录/个人资料/CAS预留                              | 4   |
| M2 全校广场  | 发帖/帖子流/详情/评论/点赞/游标分页                          | 5   |
| M3 学习空间  | 空间CRUD/成员管理/加入/审核                             | 4   |
| M4 自律打卡  | 挑战创建/每日打卡/连续统计/排行榜                            | 5   |
| M5 资源分享  | 文件上传/分类下载/本地存储                                | 5   |
| M6 问答系统  | 悬赏提问/回答采纳                                     | 4   |
| M7 通知中心  | 点赞/评论/回复/采纳/申请通知+已读                           | 7   |
| M8 管理后台  | 仪表盘/用户/帖子/空间管理/审计日志                           | 5   |
| X4 搜索服务  | MySQL FULLTEXT 全局搜索                           | 5   |
| X3 AI 能力 | AiService接口/MockAiService(摘要/审核/标签/问答)        | 9   |
| X1 多租户   | TenantContext/TenantLineInnerInterceptor/隔离测试 | 4   |
| X5 积分系统  | PointsService 流水记录、登录/发帖/打卡/采纳自动奖励          | 7   |
| 举报系统    | 用户举报创建、管理后台处理/驳回                             | 5   |
| WebSocket | 通知创建后实时推送到在线用户                                | —   |


**总计 69 个测试全部通过，前端构建成功。P0 任务全部完成。**

### 下一步待做（按优先级）

#### 优先级 P1 — 增强功能

1. **成就系统** — 徽章自动触发
  - 已有: `achievements` + `user_achievements` 表
  - 待做: AchievementService 检查触发条件 → 授予徽章 → 个人主页展示
2. **敏感词管理** — 内容自动审核
  - 已有: `sensitive_words` 表
  - 待做: SensitiveWordService 内容过滤 + 管理后台 CRUD
3. **OpenAI Compat 实现** — 真实 AI 接入
  - 已有: AiService 接口 + MockAiService
  - 待做: OpenAiCompatService（LangChain4j 调用 OpenAI/DeepSeek/Ollama）
4. **MeiliSearch 实际集成** — 搜索引擎切换
  - 已有: MySQL FULLTEXT（当前默认）
  - 待做: MeiliSearchService impl, 索引管理, 搜索切换
5. **Knife4j/Swagger API 文档** — 接口自动文档
6. **用户关注系统** — 关注/取关/关注流
  - 待做: follows 表 + FollowController + FollowService
7. **GitHub Actions CI/CD** — 自动化测试与部署

### 关键代码位置


| 文件                                      | 作用                                         |
| --------------------------------------- | ------------------------------------------ |
| `admin/security/AdminStpInterface.java` | SaToken 权限映射（role→permission）              |
| `admin/service/AuditLogService.java`    | 审计日志（记录所有 admin 写操作）                       |
| `points/service/PointsService.java`     | 积分流水 + 余额查询，登录/发帖/打卡/采纳自动奖励             |
| `report/service/ReportService.java`     | 用户举报创建、管理员处理/驳回                            |
| `notify/websocket/SessionRegistry.java` | WebSocket 会话管理，按 userId 推送消息                |
| `infra/MyBatisPlusConfig.java`          | TenantLineInnerInterceptor（自动注入 tenant_id） |
| `tenant/TenantInterceptor.java`         | 请求级租户上下文设置                                 |
| `ai/service/AiService.java`             | AI 接口（可插拔，当前 MockAiService）                |
| `search/service/SearchService.java`     | 统一搜索入口（FULLTEXT + LIKE）                    |
| `infra/CampusMetaObjectHandler.java`    | MyBatis-Plus 自动填充（createdAt/updatedAt）     |


### 数据库表状态


| 表                                    | 状态                              |
| ------------------------------------ | ------------------------------- |
| `users` ~ `notifications`            | 已有 Service/Controller，完整 CRUD   |
| `points_logs`                        | 已实现 PointsService 自动记录          |
| `reports`                            | 已实现 ReportService 创建/处理         |
| `audit_logs`                         | 仅 AdminAuditLogController（只读查询） |
| `sensitive_words`                    | 仅有表，无 Service/Controller        |
| `achievements` + `user_achievements` | 仅有表，无 Service/Controller        |
| `tenants`                            | 仅有表，仅在 standalone 模式使用          |


