# CampusForum 项目开发指南

欢迎参与 CampusForum（多租户开源高校学习社群平台）的开发！本文档旨在为开发人员提供从环境搭建、代码规范到测试和部署的完整指导。

## 一、 项目架构总览

CampusForum 是一个采用前后端分离架构的现代 Web 应用：

- **后端架构**：基于 Java 17 + Spring Boot 3.x，持久层采用 MyBatis-Plus，权限控制采用 Sa-Token。
- **前端架构**：基于 Vue 3 (Composition API) + Vite 5 + TypeScript 开发，UI 框架采用 Naive UI，状态管理采用 Pinia。
- **数据与存储**：主数据库使用 MySQL 8.0，缓存使用 Redis 7 + Caffeine（二级缓存），搜索支持 MeiliSearch 或 MySQL 兜底，文件存储当前实现为阿里云 OSS（生产默认）与本地磁盘（开发/测试）。
- **核心特色**：
  - **多租户隔离**：利用 MyBatis-Plus 的 `TenantLineInnerInterceptor` 实现底层的行级租户数据隔离。
  - **AI 增强**：集成了兼容 OpenAI 协议的大模型接口，支持帖子摘要、敏感词与风控校验、打卡相关性检测等功能。

---

## 二、 目录结构梳理

整个工程分为以下几个主要目录：

```text
campus-1/
├── backend/            # 后端项目（Maven / Spring Boot）
│   ├── src/main/java/com/campusforum/
│   │   ├── user/       # 用户模块（注册、登录、授权）
│   │   ├── post/       # 帖子模块（增删改查、引用、反应）
│   │   ├── space/      # 学习空间模块（多权限、成员管理）
│   │   ├── tenant/     # 租户模块（租户管理、AI 配置）
│   │   ├── ai/         # AI 摘要、对话、RAG 与 AI 工作台
│   │   ├── checkin/    # 打卡系统模块
│   │   └── ...         # 积分、通知、敏感词等其他业务模块
│   └── src/test/       # 后端单元测试
├── frontend/           # 前端项目（Node.js / Vue3）
│   ├── src/pages/      # 页面视图组件
│   ├── src/api/        # 后端接口封装
│   └── src/stores/     # Pinia 状态管理
├── db/                 # 数据库初始化脚本（如 schema.sql）
├── deploy/             # 部署资源（Docker / Nginx 配置文件）
└── docs/               # 项目文档与技术设计材料
```

---

## 三、 环境准备与启动

### 1. 基础环境依赖
- **Java**：JDK 17
- **Node.js**：v18 或更高版本（推荐使用 npm 或 pnpm）
- **Maven**：3.8+
- **MySQL**：8.0+
- **Redis**：7.0+

### 2. 数据库初始化
1. 创建数据库 `campus_forum`（字符集 `utf8mb4`）。
2. 执行 `db/schema.sql` 导入所有基础表结构。

### 3. 后端启动
进入 `backend/` 目录：
```bash
# 编译项目
mvn clean compile

# 运行项目（推荐在 IDE 中直接启动 CampusForumApplication）
# 命令行启动方式：
mvn spring-boot:run
```
> **注意**：如果使用自定义 JDK 环境（例如在 Windows 下的 WSL 开发环境），可使用环境变量指定，如：
> `JAVA_HOME=/path/to/jdk mvn test`
> 默认 `dev` profile 会连接 `application-dev.yml` 中的 MySQL/Redis 地址。若在本机运行，请通过环境变量覆盖 `SPRING_DATASOURCE_URL`、`REDIS_HOST`、`REDIS_PASSWORD`、`STORAGE_TYPE` 等配置。

### 4. 前端启动
进入 `frontend/` 目录：
```bash
# 安装依赖
npm install

# 启动本地开发服务器
npm run dev -- --host 127.0.0.1
```

---

## 四、 开发规范

### 1. 命名与代码风格
- **后端 (Java)**：
  - 遵循标准 Java 命名规范：类名使用 `PascalCase`，方法/变量名使用 `camelCase`。
  - 强制业务分层：Controller 保持极简，复杂的业务逻辑必须下沉到 Service 层。
  - 模块内包结构通常为：`controller`, `service`, `mapper`, `domain`, `dto`。
- **前端 (Vue/TS)**：
  - Vue 组件文件必须使用 `PascalCase.vue`（如 `UserProfile.vue`）。
  - 组合式函数使用 `useXxx.ts`（如 `useAuth.ts`）。
  - 前端必须通过 `npm run format` (基于 Prettier) 格式化代码。

### 2. 注释规范 (重要)
- 核心代码和复杂业务逻辑**必须编写详细注释**。
- **所有代码注释必须使用中文**。特别是实体类的关键字段、接口和复杂判断条件处。

---

## 五、 测试与提交流程

### 1. 单元测试
- **后端**：使用 Spring Boot Test 和 JUnit 5，测试类位于 `backend/src/test/java` 且命名必须以 `Test` 结尾（如 `UserServiceTest.java`）。针对权限、租户隔离、核心算法务必补充测试用例。
- **前端**：使用 Vitest 进行组件和函数测试，测试文件命名为 `*.test.ts` 或 `*.spec.ts`。

### 2. Git 提交规范 (Conventional Commits)
项目严格遵守约定式提交（Conventional Commits），常用前缀：
- `feat:` 新增功能
- `fix:` 修复 Bug
- `docs:` 文档修改
- `style:` 代码格式调整（不影响逻辑）
- `refactor:` 代码重构
- `test:` 添加或修改测试

### 3. 安全提示
- **绝对不要**将 `.env` 环境变量文件、本地敏感配置、`node_modules/`、`target/`、凭证信息及用户上传的原始附件直接 Commit 进仓库。
- 当前 Sa-Token 使用 Redis 持久化的 tik token，不是 JWT；不要新增 `JWT_SECRET` 这类无效配置。生产部署必须设置 `SIGNED_URL_SECRET`、`CRYPTO_MASTER_KEY`、`CORS_ALLOWED_ORIGINS`、`WS_ALLOWED_ORIGINS`。

---

## 六、 常见机制说明

1. **租户隔离**：
   大部分业务表都带有 `tenant_id`。系统通过 MyBatis Plus 自动进行行级隔离。开发新的 Service 或 Mapper 时，一般不需要手动拼接 `tenant_id`，但遇到多租户全局字典表（配置在 `TENANT_IGNORE_TABLES`）时，需谨慎手动 `LambdaQueryWrapper` 进行租户过滤以防数据泄露。

2. **积分与成就体系**：
   成就采用 JSON 规则引擎配置（在 `AchievementService` 中）。如有新的触发条件，需要拓展并向 `objectMapper` 的反序列化校验中注册对应逻辑。

---
**Happy Coding!**
