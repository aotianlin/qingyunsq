# Technical Design Document

## Overview

本文档描述 CampusForum 平台综合增强的技术设计方案，涵盖 12 项功能改进的架构设计、数据模型变更、API 接口定义和关键实现细节。

## Architecture

### 系统架构概览

```
┌─────────────┐     ┌──────────────┐     ┌─────────────┐
│   Nginx     │────▶│  Spring Boot │────▶│   MySQL 8   │
│  (前端静态)  │     │   App (x N)  │     │  (主数据库)  │
└─────────────┘     └──────┬───────┘     └─────────────┘
                           │
              ┌────────────┼────────────┐
              ▼            ▼            ▼
        ┌──────────┐ ┌──────────┐ ┌──────────┐
        │  Redis 7 │ │Meilisearch│ │  MinIO   │
        │(会话/限流/│ │ (全文搜索) │ │(对象存储) │
        │ WS广播)  │ │           │ │          │
        └──────────┘ └──────────┘ └──────────┘
              ▲
              │ pub/sub
        ┌─────┴─────┐
        │ SMTP 邮件  │
        │   服务器   │
        └───────────┘
```

### 新增/变更组件

| 组件 | 类型 | 说明 |
|------|------|------|
| EmailService | 新增 | SMTP 邮件发送，替换 mock 实现 |
| RateLimitInterceptor | 新增 | Redis 滑动窗口限流拦截器 |
| RedisWebSocketBroadcaster | 新增 | Redis pub/sub WebSocket 集群广播 |
| SearchSyncListener | 新增 | 事件驱动搜索索引同步 |
| ExportService | 新增 | 流式数据导出（CSV/XLSX） |
| PostController.update | 变更 | 新增 PUT 端点 |
| CommentController.update | 变更 | 新增 PUT 端点 |
| UserController.favorites | 变更 | 新增收藏列表端点 |
| NotifyController.batchRead | 变更 | 新增批量已读端点 |
| MessageController.readAll | 变更 | 新增全部已读端点 |
| ResourceController.preview | 变更 | 新增预览端点 |

## Components and Interfaces

### 1. 邮件服务（Email Service）

**包路径**: `com.campusforum.infra.email`

**类设计**:
- `EmailService` (接口) — 定义 sendResetEmail 方法
- `SmtpEmailService` (实现) — 使用 Spring Boot Mail Starter 发送邮件
- `EmailProperties` — SMTP 配置属性类
- `EmailTemplate` — HTML 模板渲染工具

**配置新增** (application.yml):
```yaml
spring:
  mail:
    host: ${SMTP_HOST:smtp.example.com}
    port: ${SMTP_PORT:587}
    username: ${SMTP_USERNAME:}
    password: ${SMTP_PASSWORD:}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true
    
email:
  from: ${EMAIL_FROM:noreply@campusforum.com}
  reset-link-base: ${RESET_LINK_BASE:http://localhost:3000/reset-password}
```

**依赖新增** (pom.xml):
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-mail</artifactId>
</dependency>
```

### 2. 帖子编辑（Post Update）

**变更文件**: `PostController.java`, `PostService.java`

**新增端点**:
```
PUT /api/v1/posts/{id}
Request Body: UpdatePostRequest { title, content, attachments, topics, tags }
Response: R<PostVO>
```

**业务逻辑**:
1. 验证当前用户 == post.authorId
2. 执行敏感词过滤 → 更新 ai_risk_level
3. 若 risk_level >= 2 → status = 2 (隐藏)
4. 更新数据库记录
5. 发布 PostUpdatedEvent（触发搜索索引同步）

### 3. 收藏列表（Favorites List）

**变更文件**: `UserController.java`, 新增 `FavoriteService.java`

**新增端点**:
```
GET /api/v1/users/me/favorites?targetType=POST&cursor=123&limit=20
Response: R<List<FavoriteVO>>
```

**SQL 查询逻辑**:
```sql
SELECT r.*, p.title, p.content, res.file_name
FROM reactions r
LEFT JOIN posts p ON r.target_type='POST' AND r.target_id=p.id AND p.deleted=0
LEFT JOIN resources res ON r.target_type='RESOURCE' AND r.target_id=res.id AND res.deleted=0
WHERE r.user_id=? AND r.type='COLLECT' AND r.tenant_id=?
  AND (r.target_type=? OR ?='ALL')
  AND r.id < ?
ORDER BY r.id DESC
LIMIT ?
```

### 4. 通知批量已读

**变更文件**: `NotifyController.java`, `NotifyService.java`

**新增端点**:
```
PUT /api/v1/notifications/batch-read
Request Body: { "ids": [1, 2, 3] }
Response: R<Map<String,Integer>> // { "count": 3 }
```

**实现**: 使用 MyBatis-Plus 的 `update` + `LambdaUpdateWrapper` 批量更新 is_read=1。

### 5. 私信全部已读

**变更文件**: `MessageController.java`, `MessageService.java`

**新增端点**:
```
PUT /api/v1/messages/read-all
Response: R<Map<String,Integer>> // { "count": 15 }
```

**SQL**:
```sql
UPDATE messages SET is_read=1 WHERE receiver_id=? AND is_read=0 AND tenant_id=?
```

### 6. 评论编辑

**变更文件**: `CommentController.java`, `CommentService.java`

**新增端点**:
```
PUT /api/v1/comments/{id}
Request Body: { "content": "更新后的内容" }
Response: R<CommentVO>
```

**业务逻辑**: 与帖子编辑类似 — 验证作者身份 → 敏感词过滤 → 更新。

### 7. 通用 API 限流（Rate Limiter）

**包路径**: `com.campusforum.infra.ratelimit`

**类设计**:
- `RateLimitInterceptor` — Spring MVC HandlerInterceptor
- `RateLimitProperties` — 配置属性
- `RedisRateLimiter` — Redis 滑动窗口算法实现

**算法**: Redis Sorted Set 滑动窗口
```
Key: rate_limit:{userId 或 IP}:{endpoint}
Score: 请求时间戳(ms)
Member: 请求唯一ID

ZREMRANGEBYSCORE key 0 (now - windowMs)
ZCARD key → currentCount
if currentCount >= limit → 429
else ZADD key now requestId; EXPIRE key windowSeconds
```

**配置新增**:
```yaml
rate-limit:
  enabled: true
  default:
    authenticated:
      max-requests: 200
      window-seconds: 60
    anonymous:
      max-requests: 100
      window-seconds: 60
  overrides:
    "POST /api/v1/posts": { max-requests: 10, window-seconds: 60 }
    "POST /api/v1/ai/**": { max-requests: 5, window-seconds: 60 }
  exclude-patterns:
    - "/actuator/**"
    - "/api/v1/auth/login"
```

### 8. 文件在线预览

**变更文件**: `ResourceController.java`, `ResourceService.java`

**新增端点**:
```
GET /api/v1/resources/{id}/preview
Response: 文件流 (inline) 或 302 重定向
```

**实现策略**:
| 文件类型 | 处理方式 |
|---------|---------|
| PDF | 直接流式返回，Content-Type: application/pdf |
| 图片 (jpg/png/gif/webp) | 直接流式返回，对应 Content-Type |
| Office (doc/docx/ppt/pptx/xls/xlsx) | 302 重定向到预览服务（如 OnlyOffice 或 KKFileView） |

**配置新增**:
```yaml
preview:
  office-service-url: ${OFFICE_PREVIEW_URL:http://localhost:8012/onlinePreview}
  max-preview-size: 50MB
```

### 9. 管理后台数据导出

**包路径**: `com.campusforum.admin.export`

**类设计**:
- `ExportController` — 暴露导出端点
- `ExportService` — 导出逻辑编排
- `CsvExporter` — CSV 流式写入
- `XlsxExporter` — XLSX 流式写入（Apache POI SXSSFWorkbook）

**新增端点**:
```
POST /api/v1/admin/export/{dataType}?format=csv
Response: 文件流下载 (Content-Disposition: attachment)
```

**依赖新增** (pom.xml):
```xml
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.2.5</version>
</dependency>
```

**流式处理**: 使用 MyBatis-Plus 的 `selectList` 分批查询（每批 1000 条），逐批写入 OutputStream，避免 OOM。

### 10. WebSocket 集群广播

**包路径**: `com.campusforum.infra.websocket`

**类设计**:
- `WebSocketBroadcaster` (接口) — 定义 broadcast 方法
- `RedisWebSocketBroadcaster` (实现) — Redis pub/sub 广播
- `LocalWebSocketBroadcaster` (实现) — 单机回退
- `BroadcastMessage` — 广播消息 DTO (userId, eventType, payload)

**Redis 频道**: `campusforum:ws:broadcast`

**流程**:
```
发送通知 → WebSocketBroadcaster.broadcast(msg)
         → Redis PUBLISH "campusforum:ws:broadcast" JSON(msg)
         → 所有实例的 RedisMessageListener 收到
         → 查找本地 userId 对应的 WebSocket Session
         → session.sendMessage(payload)
```

**降级策略**: Redis 不可用时自动切换到 LocalWebSocketBroadcaster，仅投递本地会话。

### 11. 搜索索引同步

**包路径**: `com.campusforum.infra.search`

**类设计**:
- `SearchSyncListener` — 监听 Spring ApplicationEvent
- `PostIndexEvent` / `ResourceIndexEvent` — 领域事件
- `MeilisearchSyncService` — 执行索引 CRUD
- `SearchReindexController` — 管理员手动重建索引端点

**事件流**:
```
PostService.create/update/delete
  → applicationEventPublisher.publishEvent(new PostIndexEvent(...))
  → SearchSyncListener.onPostEvent(event)
  → MeilisearchSyncService.indexPost(post) / deletePost(id)
```

**重试机制**: 使用 Spring Retry（@Retryable），最多 3 次，指数退避 1s → 2s → 4s。

**新增端点**:
```
POST /api/v1/admin/search/reindex
Response: R<Map<String,Integer>> // { "indexed": 5000 }
```

**依赖新增** (pom.xml):
```xml
<dependency>
    <groupId>org.springframework.retry</groupId>
    <artifactId>spring-retry</artifactId>
</dependency>
```

### 12. 前端国际化（i18n）

**技术选型**: `vue-i18n` v9

**目录结构**:
```
frontend/src/
  locales/
    zh-CN.json    # 简体中文（默认）
    en-US.json    # 英文
    index.ts      # i18n 实例配置
  components/
    LanguageSwitcher.vue  # 语言切换组件
```

**依赖新增** (package.json):
```json
"vue-i18n": "^9.14.0"
```

**初始化逻辑** (locales/index.ts):
```typescript
import { createI18n } from 'vue-i18n'
import zhCN from './zh-CN.json'
import enUS from './en-US.json'

const savedLocale = localStorage.getItem('locale')
const browserLocale = navigator.language.startsWith('en') ? 'en-US' : 'zh-CN'

export const i18n = createI18n({
  legacy: false,
  locale: savedLocale || browserLocale,
  fallbackLocale: 'zh-CN',
  messages: { 'zh-CN': zhCN, 'en-US': enUS }
})
```

## Data Models

### 数据库变更

本次增强不需要新增表，仅需对 `comments` 表新增一个字段：

```sql
-- comments 表新增 updated_at 字段（支持评论编辑时间记录）
ALTER TABLE comments ADD COLUMN updated_at DATETIME DEFAULT NULL COMMENT '最后编辑时间' AFTER created_at;
```

### 新增 DTO/VO 类

| 类名 | 包路径 | 用途 |
|------|--------|------|
| `UpdatePostRequest` | post.dto | 帖子编辑请求体 |
| `UpdateCommentRequest` | post.dto | 评论编辑请求体 |
| `FavoriteVO` | post.dto | 收藏列表响应项 |
| `BatchReadRequest` | notify.dto | 批量已读请求体 |
| `ExportRequest` | admin.dto | 导出请求参数 |
| `BroadcastMessage` | infra.websocket | WebSocket 广播消息 |
| `PostIndexEvent` | search.event | 帖子索引事件 |
| `ResourceIndexEvent` | search.event | 资源索引事件 |

### 新增配置属性类

| 类名 | 说明 |
|------|------|
| `RateLimitProperties` | 限流配置（窗口大小、最大请求数、排除路径） |
| `PreviewProperties` | 预览配置（Office 预览服务 URL、最大文件大小） |
| `EmailProperties` | 邮件配置（发件人、重置链接基础 URL） |

## API Design

### 新增 API 端点汇总

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| PUT | /api/v1/posts/{id} | 编辑帖子 | 作者本人 |
| PUT | /api/v1/comments/{id} | 编辑评论 | 作者本人 |
| GET | /api/v1/users/me/favorites | 收藏列表 | 已认证 |
| PUT | /api/v1/notifications/batch-read | 批量标记通知已读 | 已认证 |
| PUT | /api/v1/messages/read-all | 全部私信标记已读 | 已认证 |
| GET | /api/v1/resources/{id}/preview | 文件在线预览 | 已认证 |
| POST | /api/v1/admin/export/{dataType} | 数据导出 | 管理员 |
| POST | /api/v1/admin/search/reindex | 手动重建搜索索引 | 管理员 |

### 错误码新增

| 错误码 | 含义 |
|--------|------|
| 40301 | 无权编辑（非作者） |
| 40401 | 帖子/评论/资源不存在 |
| 41501 | 文件类型不支持预览 |
| 41301 | 文件过大无法预览 |
| 42901 | 请求频率超限 |
| 50201 | 预览服务不可用 |

## Correctness Properties

### Property 1: 帖子编辑权限隔离

**Validates: Requirement 2.2**

对于任意帖子 P 和用户 U，若 U.id != P.authorId，则 PUT /api/v1/posts/{P.id} 请求必须返回 403 且 P 的内容不变。验证方式：属性测试 — 随机生成 (userId, postId) 对，验证非作者编辑始终被拒绝。

### Property 2: 限流计数器单调性

**Validates: Requirement 7.1**

在任意滑动窗口内，同一用户的请求计数单调递增直到窗口滑动，且永远不超过配置的 maxRequests + 1（竞态容忍）。验证方式：并发测试 — 多线程同时发送请求，验证 429 响应出现时机。

### Property 3: 收藏列表完整性

**Validates: Requirement 3.1**

对于任意用户 U，其收藏列表返回的项集合 = reactions 表中 (user_id=U, type=COLLECT, target未删除) 的全集。验证方式：属性测试 — 随机执行收藏/取消/删除操作后，验证列表与数据库状态一致。

### Property 4: 搜索索引最终一致性

**Validates: Requirement 11.1**

对于任意帖子/资源的 CUD 操作，在操作完成后 5 秒内（正常情况）或 15 秒内（含重试），Meilisearch 索引状态与数据库一致。验证方式：集成测试 — 执行 CRUD 后轮询搜索结果验证一致性。

### Property 5: WebSocket 广播可达性

**Validates: Requirement 10.1**

对于任意在线用户 U（有活跃 WebSocket 连接），当通知发布后，U 必须在 2 秒内收到消息，无论连接到哪个实例。验证方式：集成测试 — 多实例部署下验证消息投递。

### Property 6: 批量已读幂等性

**Validates: Requirement 4.1**

对同一组通知 ID 多次执行批量已读操作，结果等价于执行一次（is_read 始终为 1，不产生副作用）。验证方式：属性测试 — 重复调用验证数据库状态不变。

## Error Handling

| 场景 | 处理策略 |
|------|---------|
| SMTP 发送失败 | 记录日志，返回通用成功响应（防枚举） |
| Redis 不可用（限流） | Fail-open，允许请求通过，记录 WARN 日志 |
| Redis 不可用（WebSocket） | 降级为本地投递，记录 WARN 日志 |
| Meilisearch 不可用 | 重试 3 次（指数退避），全部失败后记录 ERROR 日志，不阻塞业务 |
| Office 预览服务不可用 | 返回 502 Bad Gateway |
| 文件过大（>50MB 预览） | 返回 413 Content Too Large |
| 非作者编辑帖子/评论 | 返回 403 Forbidden |
| 导出数据类型不支持 | 返回 400 Bad Request |
| 批量已读超过 100 条 | 返回 400 Bad Request |
| 限流触发 | 返回 429 Too Many Requests + Retry-After 头 |

## Dependencies

### 新增 Maven 依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| spring-boot-starter-mail | (managed) | SMTP 邮件发送 |
| spring-retry | (managed) | 搜索同步重试 |
| poi-ooxml | 5.2.5 | XLSX 导出 |

### 新增 npm 依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| vue-i18n | ^9.14.0 | 前端国际化 |

### 基础设施变更

- **SMTP 服务器**: 生产环境需配置可用的 SMTP 服务（如阿里云邮件推送、腾讯企业邮箱等）
- **Office 预览服务**（可选）: 部署 KKFileView 或 OnlyOffice 用于 Office 文档预览
- Docker Compose 无需新增容器（Redis/Meilisearch 已存在）

## Testing Strategy

### 单元测试
- RateLimitInterceptor: 模拟 Redis 操作验证限流逻辑
- EmailService: Mock SMTP 验证邮件构建和发送
- ExportService: 验证 CSV/XLSX 输出格式

### 集成测试（Testcontainers）
- 帖子编辑 + 敏感词过滤端到端
- 搜索索引同步（MySQL + Meilisearch 容器）
- WebSocket 广播（Redis 容器 + 多 WebSocket 客户端）
- 限流（Redis 容器 + 并发请求）

### 前端测试
- i18n 切换：验证语言切换后所有文本正确渲染
- 收藏列表：Mock API 验证分页和过滤
