# AI 工作台后端接口需求文档

本文档对应前端 AI 页面四个视图：对话、智能体、插件中心、知识库。当前后端已提供 `/api/v1/ai/**` 工作台接口，使用 `backend/data/ai-workspace.json` 做轻量持久化，并通过 Sa-Token 判断登录用户；它适合当前产品原型与本地演示，后续如需生产级多租户规模化，需要迁移为数据库表、审计与配额策略。

## 1. 通用约定

- Base URL：`/api/v1`，前端 `request.ts` 已统一加此前缀，业务调用中写 `/ai/...`
- 鉴权：沿用现有登录态，请求头 `Authorization: <sa-token>`；当前 token 为 Sa-Token tik 风格随机串，不带 `Bearer` 前缀
- 时间格式：ISO 8601 或 `yyyy-MM-dd HH:mm:ss`，前端展示时可格式化
- 分页参数：`page` 从 1 开始，`pageSize` 默认 10
- 通用返回：

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

## 2. 智能体模块

当前已实现：

- 智能体广场布局
- 分类筛选、关键词搜索、排序 tab
- 创建智能体弹窗，本地草稿保存
- 收藏、使用、查看详情交互

### GET `/ai/agents`

查询智能体列表。

Query：

- `keyword?: string`
- `category?: string`
- `sort?: recommend | latest | popular | rating`
- `mine?: boolean`
- `favorite?: boolean`
- `page?: number`
- `pageSize?: number`

返回 `data`：

```json
{
  "items": [
    {
      "id": "agent_001",
      "name": "文案写作大师",
      "description": "擅长各类文案创作",
      "category": "通用助手",
      "tags": ["写作"],
      "avatar": "",
      "color": "#18c7a7",
      "userCount": 12500,
      "rating": 4.9,
      "isMine": false,
      "isFavorite": true,
      "createdAt": "2026-06-17T10:00:00+08:00",
      "updatedAt": "2026-06-17T10:00:00+08:00"
    }
  ],
  "total": 128
}
```

### POST `/ai/agents`

创建智能体。

Body：

```json
{
  "name": "周报生成器",
  "description": "根据工作记录生成周报",
  "category": "工作效率",
  "model": "deepseek-v4-flash",
  "prompt": "你是一个周报生成助手...",
  "abilities": ["文档处理", "内容生成"],
  "knowledgeBaseIds": ["kb_001"],
  "pluginIds": ["plugin_excel"]
}
```

### GET `/ai/agents/{agentId}`

获取智能体详情，包含配置、能力、绑定知识库和插件。

### PATCH `/ai/agents/{agentId}`

编辑智能体。

### POST `/ai/agents/{agentId}/favorite`

收藏智能体。

### DELETE `/ai/agents/{agentId}/favorite`

取消收藏。

### POST `/ai/agents/{agentId}/use`

记录一次使用，并返回可注入对话的上下文配置。

## 3. 插件中心模块

当前已实现：

- 插件中心布局
- 插件分类、tab、排序、搜索
- 安装/卸载状态本地保存
- 插件详情、开发者入驻、开发文档弹窗
- 插件排行榜、最新上架展示

### GET `/ai/plugins`

查询插件列表。

Query：

- `keyword?: string`
- `category?: string`
- `tab?: all | official | featured | latest`
- `sort?: comprehensive | rating | usage`
- `page?: number`
- `pageSize?: number`

返回字段建议：

```json
{
  "items": [
    {
      "id": "plugin_weather",
      "name": "天气查询",
      "description": "获取实时天气信息和天气预报",
      "category": "生活服务",
      "icon": "",
      "color": "#38bdf8",
      "usageCount": 12400,
      "installCount": 6500,
      "rating": 4.8,
      "isOfficial": true,
      "isInstalled": false,
      "permissions": ["network"],
      "inputSchema": {},
      "outputSchema": {},
      "createdAt": "2026-06-17T10:00:00+08:00"
    }
  ],
  "total": 128
}
```

### GET `/ai/plugins/rankings`

返回插件排行榜。

### GET `/ai/plugins/latest`

返回最新上架插件。

### POST `/ai/plugins/{pluginId}/install`

安装插件。

### DELETE `/ai/plugins/{pluginId}/install`

卸载插件。

### POST `/ai/plugins/{pluginId}/invoke`

由 AI 对话或智能体调用插件。

Body：

```json
{
  "conversationId": "chat_001",
  "agentId": "agent_001",
  "input": {
    "city": "杭州"
  }
}
```

### POST `/ai/plugin-developer/applications`

开发者入驻申请。

Body：

```json
{
  "developerName": "团队名称",
  "contact": "email@example.com",
  "description": "插件能力说明"
}
```

### POST `/ai/plugins`

开发者发布插件。需要支持插件元信息、权限、调用端点、schema 和审核状态。

## 4. 知识库模块

当前已实现：

- 知识库首页布局
- 统计卡片、分类、tab、搜索、筛选、列表/网格切换
- 创建知识库弹窗，本地保存
- 导入文档弹窗，本地模拟导入
- 收藏、分享、详情、基于知识库提问、批量选择交互

### GET `/ai/knowledge-bases`

查询知识库列表。

Query：

- `keyword?: string`
- `category?: string`
- `tab?: all | mine | shared | favorite`
- `type?: string`
- `sort?: recent | docs | vectors`
- `page?: number`
- `pageSize?: number`

返回：

```json
{
  "items": [
    {
      "id": "kb_001",
      "name": "产品帮助文档",
      "description": "包含产品使用说明、功能介绍、常见问题等",
      "category": "产品文档",
      "type": "产品文档",
      "documentCount": 156,
      "vectorCount": 458642,
      "storageBytes": 123456789,
      "owner": "我创建的",
      "isFavorite": true,
      "updatedAt": "2024-05-20T14:30:00+08:00"
    }
  ],
  "total": 28
}
```

### GET `/ai/knowledge-bases/stats`

知识库统计。

返回：

```json
{
  "knowledgeBaseCount": 28,
  "documentCount": 1248,
  "vectorCount": 3567892,
  "storageUsedBytes": 13368003788,
  "storageLimitBytes": 53687091200
}
```

### POST `/ai/knowledge-bases`

创建知识库。

Body：

```json
{
  "name": "产品帮助文档",
  "description": "产品说明与 FAQ",
  "category": "产品文档",
  "visibility": "private"
}
```

### PATCH `/ai/knowledge-bases/{knowledgeBaseId}`

编辑知识库。

### DELETE `/ai/knowledge-bases/{knowledgeBaseId}`

移入回收站。

### POST `/ai/knowledge-bases/{knowledgeBaseId}/favorite`

收藏知识库。

### DELETE `/ai/knowledge-bases/{knowledgeBaseId}/favorite`

取消收藏。

### POST `/ai/knowledge-bases/{knowledgeBaseId}/share`

生成分享链接或共享给指定用户。

Body：

```json
{
  "targetUserIds": ["user_001"],
  "permission": "read"
}
```

### POST `/ai/knowledge-bases/{knowledgeBaseId}/documents`

上传文档。建议使用 `multipart/form-data`。

字段：

- `files`: 文件数组
- `tags?: string`
- `parseMode?: auto | text | table | ocr`

返回：

```json
{
  "taskId": "task_001",
  "uploaded": 3
}
```

### GET `/ai/knowledge-bases/{knowledgeBaseId}/documents`

查询文档列表。

### DELETE `/ai/knowledge-bases/{knowledgeBaseId}/documents/{documentId}`

删除文档。

### GET `/ai/knowledge-ingest-tasks/{taskId}`

查询文档解析与向量化进度。

返回：

```json
{
  "taskId": "task_001",
  "status": "processing",
  "progress": 65,
  "message": "正在生成向量"
}
```

### POST `/ai/knowledge-bases/{knowledgeBaseId}/qa-pairs`

创建问答对。

Body：

```json
{
  "question": "如何重置密码？",
  "answer": "进入设置页面后...",
  "tags": ["账号"]
}
```

### GET `/ai/knowledge-bases/{knowledgeBaseId}/usage`

知识库使用统计，包括调用次数、命中率、无答案率、热门问题。

## 5. 对话模块补充

现有前端已接入基础聊天接口：

- `POST /ai/chat`
- `POST /ai/rag-chat`

工作台历史对话接口已实现：

### POST `/ai/conversations`

创建云端对话。

### GET `/ai/conversations`

查询历史对话。

### GET `/ai/conversations/{conversationId}/messages`

查询对话消息。

### POST `/ai/conversations/{conversationId}/messages`

发送消息，支持指定智能体、插件、知识库。

Body：

```json
{
  "content": "帮我总结这份文档",
  "model": "deepseek-v4-flash",
  "agentId": "agent_001",
  "pluginIds": ["plugin_pdf"],
  "knowledgeBaseIds": ["kb_001"],
  "attachments": ["file_001"],
  "stream": true
}
```

### POST `/ai/messages/{messageId}/feedback`

记录有用/无用反馈。

Body：

```json
{
  "helpful": true,
  "reason": "回答准确"
}
```

## 6. 权限与注意事项

- 智能体、插件、知识库都需要区分个人、组织、公开三种可见性。
- 插件调用必须校验权限，尤其是联网、文件、数据库、邮件发送类插件。
- 知识库上传需要异步解析和向量化任务，前端需要轮询任务进度。
- 分享链接需要过期时间、访问权限和撤销能力。
- 大文件上传建议支持断点续传或分片上传。
- 对话接口如支持流式输出，建议使用 SSE：`Content-Type: text/event-stream`。
