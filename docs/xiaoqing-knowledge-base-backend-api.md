# 小青知识库后端接口设计文档

## 1. 背景与范围

小青知识库前端 v1 已将原 `/ai` 工作台重构为知识库产品形态，当前代码只接入 `POST /ai/rag-chat` 作为兼容问答接口，其余知识库列表、发现广场、文件目录、订阅等能力先使用前端静态数据。

本文用于指导后端后续补齐全栈知识库能力。接口前缀沿用现有 `/api/v1`，下文路径省略该公共前缀。

## 2. 本次建议下线的旧 AI 接口

| 接口 | 建议 | 原因 |
|---|---|---|
| `POST /ai/summarize` | 删除或标记 deprecated | 前端已删除“小青知识库”中的旧智能摘要入口。 |
| `POST /ai/moderate` | 删除或标记 deprecated | 前端已删除“小青知识库”中的旧内容检测入口。 |

注意：`POST /ai/rag-chat` 暂时保留，作为小青问答 v1 的兼容接口。`POST /ai/tags`、`POST /ai/chat`、`GET /ai/post-card/{postId}` 若仍被其他页面使用，不属于本次小青知识库下线范围。

## 3. 核心数据模型

### 3.1 KnowledgeBaseVO

```json
{
  "id": "math-daily",
  "title": "微积分(高等数学/数学分析)每日一题",
  "description": "系统性收入精选的微积分每日一题。",
  "ownerId": 10001,
  "ownerName": "MathHub",
  "avatarUrl": "",
  "visibility": "PUBLIC",
  "category": "EDUCATION",
  "subscribed": false,
  "subscriberCount": 1783,
  "contentCount": 2171,
  "updatedAt": "2026-06-18T12:00:00"
}
```

### 3.2 KnowledgeFileVO

```json
{
  "id": "file-001",
  "knowledgeBaseId": "math-daily",
  "parentId": null,
  "name": "README | 知识库介绍与使用指南",
  "type": "NOTE",
  "mimeType": "text/markdown",
  "size": 4096,
  "pageCount": null,
  "updatedAt": "2026-06-18T12:00:00"
}
```

## 4. 知识库接口

### 4.1 获取我的知识库

`GET /knowledge-bases`

Query:

| 参数 | 类型 | 说明 |
|---|---|---|
| `scope` | string | `PERSONAL`、`SHARED`、`SUBSCRIBED`，不传则返回全部。 |
| `page` | number | 页码，从 1 开始。 |
| `size` | number | 每页数量。 |

Response:

```json
{
  "records": [],
  "total": 0,
  "page": 1,
  "size": 20
}
```

### 4.2 获取知识库详情

`GET /knowledge-bases/{id}`

返回 `KnowledgeBaseVO`，并附带用户是否可编辑、是否已订阅等状态。

### 4.3 创建知识库

`POST /knowledge-bases`

Body:

```json
{
  "title": "我的考研资料库",
  "description": "数学、英语和专业课资料整理",
  "visibility": "PRIVATE",
  "category": "EDUCATION"
}
```

### 4.4 更新知识库

`PUT /knowledge-bases/{id}`

仅知识库所有者或有管理权限的共享成员可调用。

### 4.5 删除知识库

`DELETE /knowledge-bases/{id}`

建议软删除，并异步清理向量索引与文件存储。

## 5. 文件与目录接口

### 5.1 获取文件树

`GET /knowledge-bases/{id}/files`

Query:

| 参数 | 类型 | 说明 |
|---|---|---|
| `parentId` | string | 不传返回根目录。 |

Response:

```json
{
  "items": []
}
```

### 5.2 上传文件

`POST /knowledge-bases/{id}/files`

Content-Type: `multipart/form-data`

字段：

| 参数 | 类型 | 说明 |
|---|---|---|
| `file` | file | 文档文件。 |
| `parentId` | string | 可选目录 ID。 |

上传成功后后端应异步执行文本抽取、切片、向量化，并返回文件基础信息。

### 5.3 创建目录

`POST /knowledge-bases/{id}/folders`

Body:

```json
{
  "parentId": null,
  "name": "6.无穷级数"
}
```

### 5.4 删除文件或目录

`DELETE /knowledge-bases/{id}/files/{fileId}`

删除目录时需校验子节点处理策略，推荐默认递归软删除。

## 6. 发现广场与订阅接口

### 6.1 发现知识库

`GET /knowledge-bases/discover`

Query:

| 参数 | 类型 | 说明 |
|---|---|---|
| `keyword` | string | 搜索关键词。 |
| `category` | string | 分类，如 `EDUCATION`、`TECH`。 |
| `featured` | boolean | 是否只看精选。 |
| `page` | number | 页码。 |
| `size` | number | 每页数量。 |

### 6.2 获取分类

`GET /knowledge-bases/categories`

返回用于发现页 tab 的分类列表。

### 6.3 订阅知识库

`POST /knowledge-bases/{id}/subscriptions`

### 6.4 取消订阅

`DELETE /knowledge-bases/{id}/subscriptions`

## 7. 基于知识库问答接口

### 7.1 基于指定知识库问答

`POST /knowledge-bases/{id}/chat`

Body:

```json
{
  "messages": [
    { "role": "user", "content": "请总结不定积分的求解方法。" }
  ],
  "fileIds": ["file-001"],
  "scene": "DETAIL_PAGE"
}
```

Response:

```json
{
  "reply": "不定积分常见求法包括换元法、分部积分法、拆项与配凑...",
  "citations": [
    {
      "type": "KNOWLEDGE_FILE",
      "id": "file-001",
      "title": "README | 知识库介绍与使用指南",
      "snippet": "不定积分部分整理了常见换元和分部积分题型。",
      "url": "/ai/wikis/math-daily?fileId=file-001"
    }
  ]
}
```

### 7.2 问答历史

`GET /knowledge-bases/chats`

Query:

| 参数 | 类型 | 说明 |
|---|---|---|
| `knowledgeBaseId` | string | 可选，按知识库筛选。 |
| `page` | number | 页码。 |
| `size` | number | 每页数量。 |

### 7.3 删除问答历史

`DELETE /knowledge-bases/chats/{chatId}`

## 8. 权限与租户要求

- 所有接口都应沿用现有登录态和租户隔离策略。
- `PRIVATE` 知识库仅所有者可见。
- `SHARED` 知识库对被授权成员可见。
- `PUBLIC` 知识库可进入发现广场，被其他用户订阅。
- 文件下载、问答引用和向量检索都必须校验用户对知识库的可读权限。

## 9. 前端替换路径

当前前端 mock 数据集中在 `frontend/src/pages/AiAssistant.vue`。后端接口完成后，建议新增 `frontend/src/api/knowledge-bases.ts` 与 `frontend/src/types/knowledge-base.ts`，再将页面内静态数据替换为接口加载。
