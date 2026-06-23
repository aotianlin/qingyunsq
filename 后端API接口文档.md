# CampusForum 后端 API 接口文档

本文档基于当前后端代码整理，用于前后端联调。接口公共前缀为 `/api/v1`，下文路径均包含此前缀。

## 1. 通用约定

### 1.1 基础信息

- 本地默认服务地址：`http://localhost:8080`
- 前端开发代理通常只写业务路径，如 `/posts`，由 `frontend/src/api/request.ts` 统一拼接 `/api/v1`
- 时间格式：`yyyy-MM-dd'T'HH:mm:ss`，时区 `Asia/Shanghai`
- JSON 请求默认 `Content-Type: application/json`
- 文件上传使用 `multipart/form-data`

### 1.2 统一响应

普通 JSON 接口统一返回：

```json
{
  "code": 0,
  "message": "ok",
  "data": {},
  "traceId": "a1b2c3d4"
}
```

- `code=0` 表示成功
- `data` 为实际业务数据，可能为对象、数组、布尔值、数字或 `null`
- `traceId` 可用于和后端日志排障
- 下载、预览、导出接口可能直接返回文件流，不包裹 `R`

常见错误码：

| code | HTTP | 含义 |
|---:|---:|---|
| `40000` | 400 | 参数错误 |
| `40100` | 401 | 未登录或登录态失效 |
| `40101` | 200/业务错误 | 邮箱、密码或验证码错误 |
| `40300` | 403 | 无权限 |
| `40400` | 200/业务错误 | 资源不存在 |
| `42900` | 429 | 请求过于频繁 |
| `50000` | 500 | 服务端内部错误 |
| `50301` | 503 | 服务暂不可用，可稍后重试 |

### 1.3 鉴权

- 登录成功后，后端返回 Sa-Token `token`
- 后续请求头：`Authorization: <token>`，不带 `Bearer`
- 当前 token 是 Sa-Token tik 随机串，服务端通过 Redis 维护登录态，不是 JWT
- `/api/v1/auth/login`、注册、验证码、重置密码、租户信息、部分下载/预览直链等可匿名访问
- 所有写操作通常要求登录
- 管理后台接口额外依赖 `@SaCheckPermission`

### 1.4 多租户

当前默认 `tenant.mode=standalone`，后端自动使用租户 `1`。`multi` 模式下租户解析规则：

- 已登录请求以 Sa-Token Session 中的 `tenantId` 为准
- 未登录请求优先子域名，其次 `X-Tenant-Id`
- 前端已登录后不建议主动携带 `X-Tenant-Id`，否则后端会做绑定一致性校验，不一致返回 `TENANT_VIOLATION`

### 1.5 分页

大多数列表接口使用游标分页：

- `cursor`：上一页最后一条记录的 `id`
- `limit`：默认 `20`，多数接口后端最大截断到 `50`，评论/成员等个别接口最大 `100`
- 返回值一般直接是数组，不包含 `nextCursor`，前端可取最后一项 `id` 作为下一页游标

AI 工作台接口使用页码分页：

- `page`：从 `1` 开始
- `pageSize`：默认 `10`
- 返回 `{ items, total }`

## 2. 数据结构

### 2.1 PublicUserVO

公开用户摘要：

```json
{
  "id": 1,
  "nickname": "小青",
  "avatarUrl": "https://...",
  "bio": "简介"
}
```

### 2.2 UserVO

本人或后台用户视图：

```json
{
  "id": 1,
  "studentNo": "202410010001",
  "email": "user@example.com",
  "nickname": "小青",
  "avatarUrl": "https://...",
  "profileCoverUrl": "https://...",
  "bio": "简介",
  "college": "计算机学院",
  "major": "软件工程",
  "grade": "2024级",
  "role": "USER",
  "status": 1,
  "tenantId": 1,
  "tenantCode": "default",
  "lastLoginAt": "2026-06-22T10:00:00",
  "createdAt": "2026-06-22T10:00:00"
}
```

### 2.3 常用枚举

| 字段 | 取值 |
|---|---|
| 用户角色 `role` | `USER`、`TENANT_ADMIN`、`SUPER_ADMIN` |
| 用户状态 `status` | `1` 正常，`0` 停用/封禁 |
| 帖子范围 `scope` | `SQUARE` 广场，`SPACE` 空间 |
| 帖子类型 `type` | `NORMAL`、`QA`、`CHECKIN`、`RESOURCE`、`QUOTE` |
| 帖子状态 `status` | `1` 正常，`2` 隐藏，`0` 待审/停用语义 |
| 点赞收藏 `Reaction.type` | `LIKE`、`COLLECT` |
| 空间分类 `category` | `MAJOR`、`CLASS`、`CLUB`、`INTEREST` |
| 空间可见性 `visibility` | `PUBLIC`、`REVIEW`、`INVITE` |
| 空间成员角色 `role` | `OWNER`、`ADMIN`、`MEMBER` |
| 空间成员状态 `status` | `0` 待审核，`1` 已加入，`2` 已退出，`3` 已拒绝/移除 |
| 资源可见性 `visibility` | `PUBLIC`、`SPACE`、`PRIVATE` |
| 举报目标 `targetType` | `POST`、`COMMENT`、`RESOURCE`、`USER` |
| 举报状态 `status` | `0` 待处理，`1` 已处理，`2` 已驳回 |
| 验证码场景 `scene` | `REGISTER`、`LOGIN`、`RESET_PASSWORD` |

## 3. 认证与用户

### POST `/api/v1/auth/email-code`

发送邮箱验证码。

Body:

```json
{
  "email": "user@example.com",
  "scene": "REGISTER"
}
```

Response `data`:

```json
{ "message": "验证码已发送，请查收邮箱" }
```

### POST `/api/v1/auth/email-exists`

检查当前租户下邮箱是否存在。

Body:

```json
{ "email": "user@example.com" }
```

Response `data`:

```json
{ "exists": true }
```

### POST `/api/v1/auth/register`

注册账号。密码要求 8-64 位，必须同时包含字母和数字；昵称仅允许中英文、数字、下划线、连字符和空格。

Body:

```json
{
  "email": "user@example.com",
  "password": "Passw0rd123",
  "emailCode": "123456",
  "studentNo": "202410010001",
  "nickname": "小青"
}
```

Response `data`: `UserVO`

### POST `/api/v1/auth/login`

密码登录或邮箱验证码登录。

密码登录 Body:

```json
{
  "email": "user@example.com",
  "password": "Passw0rd123",
  "loginType": "PASSWORD"
}
```

验证码登录 Body:

```json
{
  "email": "user@example.com",
  "emailCode": "123456",
  "loginType": "CODE"
}
```

Response `data`:

```json
{
  "token": "satoken-value",
  "user": {}
}
```

### POST `/api/v1/auth/wechat-login`

微信小程序登录。

Body:

```json
{ "code": "wx-login-code" }
```

Response `data`:

```json
{
  "token": "satoken-value",
  "tenantId": 1,
  "tenantCode": "default",
  "user": {}
}
```

### POST `/api/v1/auth/logout`

退出登录。需登录。

### GET `/api/v1/auth/me`

获取当前登录用户。需登录。Response `data`: `UserVO`

### PUT `/api/v1/auth/password`

修改密码。成功后后端会踢掉该用户活跃会话，前端应主动清空本地会话并跳转登录。

Body:

```json
{
  "oldPassword": "OldPass123",
  "newPassword": "NewPass456"
}
```

### POST `/api/v1/auth/forgot-password`

忘记密码发送重置验证码。无论邮箱是否存在，返回相同文案以避免枚举。

Body:

```json
{ "email": "user@example.com" }
```

### POST `/api/v1/auth/reset-password`

通过邮箱验证码重置密码。成功后后端会踢掉该用户活跃会话。

Body:

```json
{
  "email": "user@example.com",
  "emailCode": "123456",
  "newPassword": "NewPass456"
}
```

### POST `/api/v1/auth/ws-ticket`

颁发 WebSocket 短期票据。需登录。

Response `data`:

```json
{
  "ticket": "temporary-ticket",
  "expiresAt": 1782093600
}
```

### GET `/api/v1/users/me`

获取当前用户资料。需登录。Response `data`: `UserVO`

### PUT `/api/v1/users/me`

更新个人资料。头像/封面 URL 必须为 `http(s)`，且 service 会校验允许域名。

Body:

```json
{
  "nickname": "小青",
  "avatarUrl": "https://...",
  "profileCoverUrl": "https://...",
  "bio": "简介",
  "college": "计算机学院",
  "major": "软件工程",
  "grade": "2024级"
}
```

### POST `/api/v1/users/me/assets`

上传头像或封面素材。需登录，`multipart/form-data`。

Form:

| 字段 | 类型 | 说明 |
|---|---|---|
| `file` | file | 图片，最大 5MB，仅支持 JPG、PNG、GIF、WEBP |

Response `data`:

```json
{
  "url": "https://...",
  "storageKey": "2026-06-22/xxx.png"
}
```

### GET `/api/v1/users/{id}`

获取公开用户资料。Response `data`: `PublicUserVO`

### GET `/api/v1/users/me/mute-settings`

获取通知免打扰类型集合。需登录。Response `data`: `string[]`

### PUT `/api/v1/users/me/mute-settings`

更新免打扰类型。

Body:

```json
{ "mutedTypes": ["LIKE", "COMMENT"] }
```

### GET `/api/v1/users/me/tag-subscriptions`

获取问答标签订阅集合。需登录。Response `data`: `string[]`

### PUT `/api/v1/users/me/tag-subscriptions`

更新问答标签订阅。标签只允许中英文、数字、下划线、连字符，长度 1-32。

Body:

```json
{ "tags": ["Java", "高数"] }
```

### GET `/api/v1/users/me/favorites`

获取我的收藏。需登录。

Query:

| 参数 | 类型 | 说明 |
|---|---|---|
| `targetType` | string | 可选，`POST` 或 `RESOURCE` |
| `cursor` | number | 收藏记录 id 游标 |
| `limit` | number | 默认 20 |

Response `data`: `FavoriteVO[]`

## 4. 帖子与评论

### PostVO

```json
{
  "id": 1,
  "authorId": 1,
  "author": {},
  "scope": "SQUARE",
  "spaceId": null,
  "type": "NORMAL",
  "title": "标题",
  "content": "正文",
  "topics": ["校园"],
  "tags": ["Java"],
  "viewCount": 10,
  "likeCount": 2,
  "commentCount": 1,
  "isPinned": 0,
  "isEssence": 0,
  "status": 1,
  "liked": false,
  "collected": false,
  "createdAt": "2026-06-22T10:00:00",
  "updatedAt": "2026-06-22T10:00:00"
}
```

### POST `/api/v1/posts`

创建帖子。需登录。空间帖必须是空间成员；内容会做 HTML 净化和敏感词风险检测，高风险自动隐藏。

Body:

```json
{
  "scope": "SQUARE",
  "spaceId": null,
  "type": "NORMAL",
  "title": "标题",
  "content": "正文",
  "topics": ["校园"],
  "tags": ["Java"],
  "quotePostId": null
}
```

说明：

- `type=QA` 会创建问答扩展记录
- `quotePostId` 存在时会生成引用内容，帖子类型变为 `QUOTE`
- 发布成功触发成就、搜索索引、@提及通知；QA 标签会通知订阅用户

### GET `/api/v1/posts`

帖子列表。

Query:

| 参数 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `scope` | string | `SQUARE` | `SQUARE` 或 `SPACE` |
| `authorId` | number | - | 按作者筛选 |
| `sort` | string | `latest` | `latest`、`hot`、`following` |
| `cursor` | number | - | 游标 |
| `cursorId` | number | - | 兼容游标，优先于 `cursor` |
| `limit` | number | `20` | 最大 50 |

Response `data`: `PostVO[]`

### GET `/api/v1/posts/{id}`

帖子详情。会对浏览量做 30 分钟去重计数。Response `data`: `PostVO`

### PUT `/api/v1/posts/{id}`

更新帖子。需作者本人。

Body:

```json
{
  "title": "新标题",
  "content": "新正文",
  "topics": ["校园"],
  "tags": ["Java"],
  "attachments": "json-string"
}
```

### DELETE `/api/v1/posts/{id}`

删除帖子。需作者本人；管理员强删走后台接口。

### POST `/api/v1/posts/{id}/reactions`

切换帖子点赞或收藏。需登录。

Body:

```json
{
  "targetType": "POST",
  "targetId": 1,
  "type": "LIKE"
}
```

Response `data`: `true` 表示本次新增，`false` 表示本次取消。

### CommentVO

```json
{
  "id": 1,
  "postId": 1,
  "parentId": null,
  "replyToId": null,
  "authorId": 1,
  "author": {},
  "content": "评论",
  "likeCount": 0,
  "replies": [],
  "createdAt": "2026-06-22T10:00:00"
}
```

### POST `/api/v1/posts/{postId}/comments`

发表评论或回复。需登录。内容 1-5000 字，风险等级 3 会拒绝。

Body:

```json
{
  "postId": 1,
  "parentId": null,
  "replyToId": null,
  "content": "评论内容"
}
```

说明：`postId` 会被路径覆盖；创建后触发评论数自增、成就、评论/回复/@提及通知和 WebSocket 评论变更事件。

### GET `/api/v1/posts/{postId}/comments`

评论列表。

Query:

| 参数 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `cursor` | number | - | 根评论 id 游标 |
| `limit` | number | `20` | 最大 100 |
| `qaSort` | boolean | `false` | QA 场景可优先展示采纳相关排序 |

Response `data`: `CommentVO[]`

### PUT `/api/v1/comments/{id}`

编辑评论。需作者本人。Body: `{ "content": "新内容" }`

### DELETE `/api/v1/comments/{id}`

删除评论。需作者本人。

### POST `/api/v1/comments/{id}/reactions`

切换评论点赞。需登录，仅支持 `type=LIKE`。

## 5. 空间

### POST `/api/v1/spaces`

创建空间。需登录，创建者自动成为 `OWNER`。

Body:

```json
{
  "name": "软件工程 2024",
  "description": "课程交流",
  "category": "CLASS",
  "visibility": "PUBLIC"
}
```

### GET `/api/v1/spaces`

空间列表。

Query: `category?`、`cursor?`、`limit?`

排序：`memberCount desc, id desc`。Response `data`: `SpaceVO[]`

### GET `/api/v1/spaces/{id}`

空间详情。公开返回基础信息；只有 `OWNER/ADMIN` 视图会返回 `sensitiveWords`。

### PUT `/api/v1/spaces/{id}`

更新空间。需空间 `OWNER/ADMIN`。

Body:

```json
{
  "name": "新名称",
  "description": "新描述",
  "visibility": "REVIEW",
  "sensitiveWords": "词1,词2",
  "postNotice": "发帖须知"
}
```

### POST `/api/v1/spaces/{id}/join`

加入空间。

- `PUBLIC`：直接加入，成员数 +1
- `REVIEW` 或 `INVITE`：进入待审核 `status=0`，通知空间主
- 用户加入空间数量受后端配置限制

### POST `/api/v1/spaces/{id}/leave`

退出空间。空间主不能退出，只能解散或转让后再退出。

### GET `/api/v1/spaces/{id}/members`

成员列表。Query: `cursor?`、`limit?`

### PUT `/api/v1/spaces/{id}/members/{userId}?action=approve|remove`

审批或移除成员。需空间 `OWNER/ADMIN`。

### GET `/api/v1/spaces/{id}/posts`

空间公开帖子列表。非 `PUBLIC` 空间需要成员身份。

### GET `/api/v1/spaces/{id}/posts/all`

空间管理视图帖子列表，包含隐藏帖子。需空间 `OWNER/ADMIN`。

### PUT `/api/v1/spaces/{id}/posts/{postId}/status?status=1|2|0`

空间内帖子状态管理。需空间 `OWNER/ADMIN`，且校验帖子属于该空间。

### DELETE `/api/v1/spaces/{id}`

解散空间。空间主、租户管理员或超管可操作。

## 6. 资源

### POST `/api/v1/resources`

上传资源。需登录，`multipart/form-data`。全局最大文件 50MB。

Form:

| 字段 | 类型 | 说明 |
|---|---|---|
| `file` | file | 支持 `pdf/doc/docx/ppt/pptx/xls/xlsx/jpg/jpeg/png/gif/webp/md/markdown` |
| `spaceId` | number | 可选，关联空间 |
| `visibility` | string | `PUBLIC`、`SPACE`、`PRIVATE`，默认 `PUBLIC` |
| `college` | string | 学院 |
| `major` | string | 专业 |
| `course` | string | 课程 |
| `semester` | string | 学期 |
| `tags` | string[] | 标签。表单可重复传同名字段 |
| `description` | string | 描述 |

说明：

- 后端会校验扩展名和真实 MIME
- 计算 SHA-256 指纹，同文件复用已有资源记录并删除新上传对象
- 前端当前 `resourceAccept` 包含 zip/rar/7z，但后端默认不允许压缩包，联调时应以前端限制同步调整

### GET `/api/v1/resources`

资源列表。

Query: `spaceId?`、`college?`、`major?`、`course?`、`cursor?`、`limit?`

可见性过滤：

- `PUBLIC` 对所有人可见
- `SPACE` 仅空间成员可见
- `PRIVATE` 仅上传者和管理员可见

### GET `/api/v1/resources/{id}`

资源详情。无权访问时统一返回资源不存在，避免枚举。

### GET `/api/v1/resources/{id}/signed-url?action=download|preview`

申请短期下载/预览签名。需登录。

Response `data`:

```json
{
  "token": "signed-token",
  "expiresAt": 1782093600
}
```

### GET `/api/v1/resources/{id}/download?sig=...`

下载文件。支持两种方式：

- 带 `sig`：校验短期签名，适合 `<a href>` 或浏览器直链
- 不带 `sig`：要求登录态和访问权限

响应为文件流，`Content-Disposition: attachment`。

### GET `/api/v1/resources/{id}/preview?sig=...`

在线预览。

- PDF、图片、Markdown：直接返回对应内容流
- Office 文档：返回 JSON，包含 `previewServiceUrl`、`downloadPath`、`expiresAt`，由前端打开预览服务
- 超过 50MB 返回 413
- 不支持类型返回 415

Office JSON 示例：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "kind": "office",
    "previewServiceUrl": "http://localhost:8012/onlinePreview",
    "downloadPath": "/api/v1/resources/1/download?sig=...",
    "expiresAt": 1782093600
  },
  "traceId": "..."
}
```

### GET `/api/v1/resources/{id}/preview-text`

文本预览。支持 `md/markdown/docx`，返回 `ResourcePreviewVO`。

### DELETE `/api/v1/resources/{id}`

删除资源。上传者、租户管理员或超管可删除。

## 7. 搜索

### GET `/api/v1/search`

站内搜索。

Query:

| 参数 | 类型 | 说明 |
|---|---|---|
| `keyword` | string | 必填，后端截断到 64 字符 |
| `type` | string | 可选：`POST`、`USER`、`RESOURCE`、`SPACE` |
| `sort` | string | `time` 按时间，否则按热度/成员数等 |
| `cursor` | number | 游标 |
| `limit` | number | 默认 20，最大 50 |

Response `data`: `SearchResultVO[]`

说明：

- 帖子搜索优先 MeiliSearch，失败或无结果时回退 MySQL FULLTEXT，再回退 LIKE
- 用户搜索关键词长度小于 2 返回空数组
- 搜索结果中的用户仅返回公开摘要

## 8. AI

### POST `/api/v1/ai/summarize`

Body:

```json
{ "content": "长文本" }
```

Response `data`:

```json
{ "summary": "摘要" }
```

### POST `/api/v1/ai/moderate`

Body: `{ "content": "待检测内容" }`

Response `data`:

```json
{
  "riskLevel": 1,
  "riskReason": "原因"
}
```

### POST `/api/v1/ai/tags`

Body:

```json
{
  "title": "标题",
  "content": "正文"
}
```

Response `data`:

```json
{ "tags": ["Java", "考试"] }
```

### POST `/api/v1/ai/chat`

Body:

```json
{
  "messages": [
    { "role": "user", "content": "你好" }
  ],
  "context": "可选上下文",
  "model": "deepseek-v4-flash",
  "abilities": ["web-search"]
}
```

Response `data`:

```json
{ "reply": "回复" }
```

### POST `/api/v1/ai/rag-chat`

站内检索增强问答。`abilities` 不传或包含 `web-search` 时会检索站内帖子、资源、空间作为引用。

Response `data`:

```json
{
  "reply": "回复",
  "citations": [
    {
      "type": "POST",
      "id": 1,
      "title": "标题",
      "snippet": "摘要",
      "url": "/posts/1"
    }
  ]
}
```

### GET `/api/v1/ai/post-card/{postId}?passive=false`

帖子智能卡片。

- `passive=false`：无缓存或过期时触发 AI 生成，详情页使用
- `passive=true`：只读缓存，列表页使用
- `data=null` 表示无缓存或生成失败，前端应隐藏卡片

### POST `/api/v1/ai/post-cards/batch`

批量读取帖子智能卡片缓存。匿名放行。请求体为帖子 id 数组，最多处理前 100 个。

Body:

```json
[1, 2, 3]
```

Response `data`:

```json
{
  "1": {
    "tldr": "一句话总结",
    "audience": "适合人群",
    "valueType": "价值类型",
    "readMinutes": 3,
    "commentConsensus": "评论共识",
    "commentDisputes": "争议点",
    "hotCommentId": 10,
    "hotCommentExcerpt": "热门评论摘录",
    "highlights": ["亮点1"]
  }
}
```

### AI 工作台 `/api/v1/ai/**`

这些接口由 `AiWorkspaceService` 使用 `backend/data/ai-workspace.json` 轻量持久化，偏产品原型。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/ai/agents` | 智能体列表，支持 `keyword/category/sort/mine/favorite/page/pageSize` |
| POST | `/api/v1/ai/agents` | 创建智能体 |
| GET | `/api/v1/ai/agents/{agentId}` | 智能体详情 |
| PATCH | `/api/v1/ai/agents/{agentId}` | 更新智能体，需所有者 |
| POST | `/api/v1/ai/agents/{agentId}/favorite` | 收藏智能体 |
| DELETE | `/api/v1/ai/agents/{agentId}/favorite` | 取消收藏 |
| POST | `/api/v1/ai/agents/{agentId}/use` | 使用智能体 |
| GET | `/api/v1/ai/plugins` | 插件列表 |
| GET | `/api/v1/ai/plugins/rankings` | 插件排行 |
| GET | `/api/v1/ai/plugins/latest` | 最新插件 |
| POST | `/api/v1/ai/plugins/{pluginId}/install` | 安装插件 |
| DELETE | `/api/v1/ai/plugins/{pluginId}/install` | 卸载插件 |
| POST | `/api/v1/ai/plugins/{pluginId}/invoke` | 调用插件 |
| POST | `/api/v1/ai/plugin-developer/applications` | 插件开发者申请 |
| POST | `/api/v1/ai/plugins` | 发布插件 |
| GET | `/api/v1/ai/knowledge-bases` | 知识库列表 |
| GET | `/api/v1/ai/knowledge-bases/stats` | 知识库统计 |
| POST | `/api/v1/ai/knowledge-bases` | 创建知识库 |
| PATCH | `/api/v1/ai/knowledge-bases/{knowledgeBaseId}` | 更新知识库，需所有者 |
| DELETE | `/api/v1/ai/knowledge-bases/{knowledgeBaseId}` | 删除知识库，需所有者 |
| POST | `/api/v1/ai/knowledge-bases/{knowledgeBaseId}/favorite` | 收藏知识库 |
| DELETE | `/api/v1/ai/knowledge-bases/{knowledgeBaseId}/favorite` | 取消收藏 |
| POST | `/api/v1/ai/knowledge-bases/{knowledgeBaseId}/share` | 分享知识库 |
| POST | `/api/v1/ai/knowledge-bases/{knowledgeBaseId}/documents` | 上传知识库文档，字段 `files/tags/parseMode` |
| GET | `/api/v1/ai/knowledge-bases/{knowledgeBaseId}/documents` | 文档列表 |
| DELETE | `/api/v1/ai/knowledge-bases/{knowledgeBaseId}/documents/{documentId}` | 删除文档 |
| GET | `/api/v1/ai/knowledge-ingest-tasks/{taskId}` | 查询导入任务 |
| POST | `/api/v1/ai/knowledge-bases/{knowledgeBaseId}/qa-pairs` | 创建 QA 对 |
| GET | `/api/v1/ai/knowledge-bases/{knowledgeBaseId}/usage` | 知识库使用统计 |
| POST | `/api/v1/ai/conversations` | 创建对话 |
| GET | `/api/v1/ai/conversations` | 对话列表 |
| GET | `/api/v1/ai/conversations/{conversationId}/messages` | 对话消息 |
| POST | `/api/v1/ai/conversations/{conversationId}/messages` | 发送对话消息 |
| POST | `/api/v1/ai/messages/{messageId}/feedback` | 消息反馈 |

## 9. 打卡

### POST `/api/v1/checkin/challenges`

创建打卡挑战。需登录。

Body:

```json
{
  "name": "每日背单词",
  "description": "每天 30 个单词",
  "spaceId": null,
  "startDate": "2026-06-22",
  "endDate": "2026-07-22",
  "rule": "{\"daily\":true}"
}
```

### GET `/api/v1/checkin/challenges`

挑战列表。Query: `spaceId?`、`cursor?`、`limit?`

### GET `/api/v1/checkin/challenges/{id}`

挑战详情。会返回当前用户参与状态、总打卡天数和连续天数。

### PUT `/api/v1/checkin/challenges/{id}`

更新挑战。需创建者。

### POST `/api/v1/checkin/challenges/{id}/checkin`

今日打卡。需登录，同一挑战每天只能一次。

Body:

```json
{
  "content": "今天完成了 30 个单词",
  "imageUrls": ["https://..."]
}
```

说明：会调用 AI 判断内容和挑战主题相关性，`aiCheck=1` 相关，`0` 不相关；首次打卡会增加挑战成员数。

### GET `/api/v1/checkin/challenges/{id}/records`

打卡记录列表。Query: `cursor?`、`limit?`

### GET `/api/v1/checkin/challenges/{id}/leaderboard`

排行榜，按总天数、连续天数降序。

### DELETE `/api/v1/checkin/challenges/{id}`

删除挑战。创建者、租户管理员或超管可操作。实际设置 `status=0`。

### POST `/api/v1/checkin/records/{id}/share`

将自己的打卡记录分享到广场，生成 `CHECKIN` 类型帖子。

Response `data`:

```json
{ "postId": 100 }
```

## 10. 问答

### GET `/api/v1/qa/{postId}`

获取问答扩展信息。若不是问答帖或没有记录，`data` 可能为 `null`。

### POST `/api/v1/qa/{postId}/accept/{commentId}`

采纳回答。需登录且仅提问者可采纳；评论必须属于该帖子；已采纳后不能重复采纳。

## 11. 关注

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/follows/{followeeId}` | 关注用户，不能关注自己 |
| DELETE | `/api/v1/follows/{followeeId}` | 取消关注 |
| GET | `/api/v1/follows/check/{targetId}` | 查询当前用户是否关注目标；匿名返回 `false` |
| GET | `/api/v1/follows/{userId}/followers` | 粉丝列表，支持 `cursor/limit` |
| GET | `/api/v1/follows/{userId}/following` | 关注列表，支持 `cursor/limit` |
| GET | `/api/v1/follows/{userId}/counts` | 粉丝和关注数 |

## 12. 私信与通知

### POST `/api/v1/messages`

发送私信。需登录。

Body:

```json
{
  "receiverId": 2,
  "content": "你好",
  "imageUrl": "https://..."
}
```

说明：

- `receiverId` 后端 DTO 是数字，前端建议传 number
- 不能给自己发私信
- 内容最大 2000 字，`imageUrl` 最大 500 字且必须为 `http(s)`
- 内容入库前会净化 HTML，并计算敏感词风险等级
- 若接收方在线，会通过 WebSocket 推送 `MESSAGE`

### GET `/api/v1/messages/conversations`

对话列表。每个对话返回最后一条消息。

### GET `/api/v1/messages/conversations/{peerId}`

单个会话消息。Query: `cursor?`、`limit?`，默认 50，最大 50。返回按时间正序。

### PUT `/api/v1/messages/conversations/{peerId}/read`

将某个会话标记已读。

### GET `/api/v1/messages/unread-count`

未读私信数。Response `data`: number

### PUT `/api/v1/messages/read-all`

全部私信标记已读。Response `data`: `{ "count": 10 }`

### GET `/api/v1/notifications`

通知列表。Query: `cursor?`、`limit?`

### GET `/api/v1/notifications/unread-count`

未读通知数。Response `data`: `{ "count": 3 }`

### PUT `/api/v1/notifications/{id}/read`

单条通知标记已读。

### PUT `/api/v1/notifications/read-all`

全部通知标记已读。

### PUT `/api/v1/notifications/batch-read`

批量标记已读，最多 100 条。

Body:

```json
{ "ids": [1, 2, 3] }
```

Response `data`: `{ "count": 3 }`

### WebSocket `/ws/notify`

通知和私信推送通道。

推荐流程：

1. 调用 `POST /api/v1/auth/ws-ticket` 获取短期票据
2. 连接 `ws://localhost:8080/ws/notify?ticket=<ticket>`
3. 服务端只推送文本 JSON，客户端发送 ping 会被忽略

兼容配置 `security.ws-ticket.enforced=false` 时，握手也可能支持旧 token 方式；新联调应使用 ticket。

## 13. 举报

### POST `/api/v1/reports`

创建举报。需登录。

Body:

```json
{
  "targetType": "POST",
  "targetId": 1,
  "reason": "垃圾信息",
  "description": "补充说明"
}
```

## 14. 成就

### GET `/api/v1/achievements?userId=1`

获取用户成就列表。

Response `data`: `AchievementVO[]`

```json
{
  "id": 1,
  "code": "FIRST_POST",
  "name": "首次发帖",
  "description": "发布第一篇帖子",
  "iconUrl": "https://...",
  "awarded": true,
  "awardedAt": "2026-06-22T10:00:00"
}
```

## 15. 租户与后台管理

后台接口需登录且具备对应权限。

权限角色：

- `TENANT_ADMIN`：租户后台权限
- `SUPER_ADMIN`：租户后台权限 + `super:tenant:manage`

### GET `/api/v1/tenant/info`

获取当前租户展示信息。可匿名。

Response `data`:

```json
{
  "id": 1,
  "code": "default",
  "name": "默认租户",
  "logoUrl": "",
  "domain": "",
  "announcement": ""
}
```

### GET `/api/v1/admin/dashboard`

权限：`tenant:dashboard`。Response `data`: `DashboardVO`

### 管理用户 `/api/v1/admin/users`

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/v1/admin/users` | `tenant:user:list` | 用户列表，支持 `keyword/role/status/cursor/limit` |
| PUT | `/api/v1/admin/users/{id}/role` | `tenant:user:role` | 改角色，Body `{ "role": "USER" }` 或 `TENANT_ADMIN` |
| PUT | `/api/v1/admin/users/{id}/ban` | `tenant:user:ban` | 封禁用户 |
| PUT | `/api/v1/admin/users/{id}/unban` | `tenant:user:ban` | 解封用户 |
| PUT | `/api/v1/admin/users/batch-status` | `tenant:user:ban` | 批量改状态，Body `{ "ids": [1], "status": 0 }`，最多 100 |

说明：不能通过该接口提升为 `SUPER_ADMIN`。

### 管理帖子 `/api/v1/admin/posts`

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/v1/admin/posts` | `tenant:post:manage` | 帖子列表，支持 `keyword/status/scope/cursor/limit` |
| PUT | `/api/v1/admin/posts/{id}/pin` | `tenant:post:manage` | 切换置顶 |
| PUT | `/api/v1/admin/posts/{id}/essence` | `tenant:post:manage` | 切换精华 |
| PUT | `/api/v1/admin/posts/{id}/status` | `tenant:post:manage` | 设置状态，Body `{ "status": 1 }` |
| DELETE | `/api/v1/admin/posts/{id}` | `tenant:post:manage` | 强制删除 |

### 管理空间 `/api/v1/admin/spaces`

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/v1/admin/spaces` | `tenant:space:manage` | 空间列表，支持 `keyword/category/status/cursor/limit` |
| PUT | `/api/v1/admin/spaces/{id}/status` | `tenant:space:manage` | 设置状态，Body `{ "status": 1 }` |
| DELETE | `/api/v1/admin/spaces/{id}` | `tenant:space:manage` | 解散空间 |

### 管理举报 `/api/v1/admin/reports`

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/v1/admin/reports` | `tenant:report:manage` | 举报列表，支持 `cursor/limit/targetType/status` |
| PUT | `/api/v1/admin/reports/{id}/handle` | `tenant:report:manage` | 处理举报，Body `{ "status": 1, "note": "已处理" }` |
| PUT | `/api/v1/admin/reports/batch-handle` | `tenant:report:manage` | 批量处理，Body `{ "ids": [1], "status": 1, "note": "" }` |

### 审计日志

`GET /api/v1/admin/audit-logs`

权限：`tenant:audit:log`

Query: `operatorId?`、`action?`、`cursor?`、`limit?`

### 敏感词

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/v1/admin/sensitive-words` | `tenant:sensitive:manage` | 列表 |
| POST | `/api/v1/admin/sensitive-words` | `tenant:sensitive:manage` | 新增，Body `{ "word": "xxx", "level": 2, "isRegex": false }` |
| DELETE | `/api/v1/admin/sensitive-words/{id}` | `tenant:sensitive:manage` | 删除 |

`level` 会被限制到 1-3；正则最长 200 字符，非法正则会被拒绝。

### 导出

文件流响应，支持 `format=csv|xlsx`。

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/api/v1/admin/export/users?format=csv&fullPii=false` | `tenant:export:users` | 导出用户 |
| POST | `/api/v1/admin/export/posts?format=csv` | `tenant:export:posts` | 导出帖子 |
| POST | `/api/v1/admin/export/audit_logs?format=csv` | `tenant:export:audit` | 导出审计日志 |
| POST | `/api/v1/admin/export/reports?format=csv` | `tenant:export:reports` | 导出举报 |

说明：`fullPii=true` 仅 `SUPER_ADMIN` 可用，否则返回无权限。

### 搜索重建索引

`POST /api/v1/admin/search/reindex`

权限：`tenant:dashboard`

Response `data`:

```json
{ "indexed": 1234 }
```

### 超管租户管理 `/api/v1/admin/tenants`

权限：`super:tenant:manage`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/admin/tenants` | 租户列表，支持 `keyword/cursor/limit` |
| POST | `/api/v1/admin/tenants` | 创建租户，Body `{ "code": "fudan", "name": "复旦大学", "domain": "fudan.example.com" }` |
| PUT | `/api/v1/admin/tenants/{id}` | 更新租户，Body `{ "name": "...", "domain": "...", "logoUrl": "...", "announcement": "..." }` |
| PUT | `/api/v1/admin/tenants/{id}/status` | 启用/停用租户；停用会清租户缓存并踢出该租户用户 |
| GET | `/api/v1/admin/tenants/{id}/ai-config` | 获取租户 AI 配置，`apiKey` 永不明文返回 |
| PUT | `/api/v1/admin/tenants/{id}/ai-config` | 更新租户 AI 配置 |

AI 配置 Body:

```json
{
  "provider": "openai",
  "baseUrl": "https://api.deepseek.com",
  "apiKey": "sk-...",
  "model": "deepseek-v4-flash"
}
```

说明：

- `apiKey` 存储前加密
- GET 返回掩码或 `apiKeyConfigured`
- PUT 时如果前端原样回传带 `***` 的掩码，后端视为不修改 key
- `provider=openai` 时会校验 `baseUrl` 不能指向私网，防 SSRF

## 16. 联调注意事项

- 前端请求头 `Authorization` 直接放 token，不加 `Bearer`
- 已登录后不要主动注入 `X-Tenant-Id`
- 游标分页没有总数和 nextCursor，取最后一项 id 继续翻页
- 下载、预览、导出不是统一 JSON，Axios 需要按文件流处理
- `GET /api/v1/resources/{id}/preview` 对 Office 文档返回 JSON，不是文件流
- 前端 `resources.ts` 当前 accept 包含压缩包，但后端默认不允许 zip/rar/7z
- 前端 `messages.ts` 建议把 `receiverId` 从字符串改为 number
- `GET /api/v1/users/{id}` 后端返回 `PublicUserVO`，不是完整 `UserVO`
- `POST /api/v1/ai/post-cards/batch` 匿名可调，但只读缓存，不触发生成
- API 文档 Swagger/Knife4j 在 dev 可用，生产默认关闭并受来源 IP 过滤
