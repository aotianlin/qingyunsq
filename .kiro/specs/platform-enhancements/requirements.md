# Requirements Document

## Introduction

本文档定义了 CampusForum 多租户高校学习社群平台的综合增强需求。增强内容涵盖邮件发送、内容管理、用户交互、接口限流、文件处理、数据导出等功能缺口，以及 WebSocket 集群广播、搜索索引同步和前端国际化等扩展性改进。

## Glossary

- **Platform（平台）**: CampusForum Spring Boot 3.3 后端应用
- **Email_Service（邮件服务）**: 基于 SMTP 的邮件发送组件，负责发送事务性邮件
- **Post_Service（帖子服务）**: 管理帖子 CRUD 操作的后端服务
- **Reaction_Service（互动服务）**: 管理点赞和收藏的后端服务
- **Notification_Service（通知服务）**: 管理系统通知的后端服务
- **Message_Service（私信服务）**: 管理用户间私信的后端服务
- **Comment_Service（评论服务）**: 管理帖子评论的后端服务
- **Rate_Limiter（限流器）**: 按用户或 IP 限制 API 请求频率的中间件组件
- **Resource_Service（资源服务）**: 管理文件上传、下载和预览的后端服务
- **Export_Service（导出服务）**: 为管理员生成数据导出文件的后端服务
- **WebSocket_Broadcaster（WebSocket 广播器）**: 通过 Redis pub/sub 在多个应用实例间广播 WebSocket 消息的组件
- **Search_Sync_Service（搜索同步服务）**: 负责将内容变更同步到 Meilisearch 索引的组件
- **I18n_Module（国际化模块）**: 管理前端多语言文本资源的国际化模块
- **Author（作者）**: 最初创建帖子、评论或资源的用户
- **Administrator（管理员）**: 拥有 TENANT_ADMIN 或 SUPER_ADMIN 角色的用户
- **Previewable_File（可预览文件）**: 扩展名为 pdf、jpg、jpeg、png、gif、webp、doc、docx、ppt、pptx、xls 或 xlsx 的文件

## Requirements

### Requirement 1: 密码重置邮件发送

**User Story:** 作为用户，我希望收到真实的密码重置邮件，以便安全地恢复账户，而不依赖模拟行为。

#### Acceptance Criteria

1. 当用户为已注册邮箱请求密码重置时，Email_Service 应在 30 秒内通过 SMTP 向该邮箱发送包含重置链接的邮件，链接中包含加密随机令牌（最少 32 字节，base64url 编码）
2. Email_Service 应使用应用配置中定义的可配置 SMTP 设置（主机、端口、用户名、密码、是否启用 TLS）
3. 若 SMTP 服务器连接失败、认证被拒绝或发送操作返回错误，Email_Service 应记录失败详情（不含敏感凭据）并向用户返回通用成功响应，以防止邮箱枚举
4. Email_Service 应使用 HTML 邮件模板，包含应用名称、表明密码重置请求的主题行、重置链接以及链接 30 分钟后过期的提示
5. 发送重置邮件时，Email_Service 应包含一个 30 分钟后过期的令牌，并使同一用户之前发出的重置令牌失效
6. 若请求的邮箱地址未关联任何注册账户，Email_Service 不应发送任何邮件，并应返回与已注册邮箱情况完全相同的通用成功响应，以防止邮箱枚举
7. Email_Service 应对每个邮箱地址在 15 分钟内最多允许 5 次密码重置请求，超出限制时返回相同的通用成功响应

### Requirement 2: 帖子编辑

**User Story:** 作为帖子作者，我希望能编辑已发布的帖子，以便在发布后修正错误或更新信息。

#### Acceptance Criteria

1. 当作者提交帖子更新请求且帖子 ID 有效时，Post_Service 应使用请求中提供的值更新帖子标题（最长 255 字符）、内容（1 至 20000 字符）、附件、话题和标签
2. 若请求用户不是帖子的作者，Post_Service 应返回 403 Forbidden 响应且不修改帖子
3. 若指定帖子不存在或已被删除，Post_Service 应返回未找到错误
4. 帖子成功更新后，Post_Service 应将 updated_at 时间戳设置为当前服务器时间
5. 帖子成功更新后，Post_Service 应对新内容重新执行敏感词过滤并相应更新帖子的风险等级
6. 若敏感词过滤检测到更新内容的风险等级为 2 或更高，Post_Service 应将帖子状态设为隐藏（不公开可见）
7. Post_Service 应暴露 PUT /api/v1/posts/{id} 端点接受更新载荷

### Requirement 3: 收藏列表

**User Story:** 作为用户，我希望查看我收藏的帖子和资源列表，以便日后轻松找到保存的内容。

#### Acceptance Criteria

1. 当已认证用户请求其收藏列表时，Reaction_Service 应返回该用户 reaction 类型为 COLLECT 的分页列表，默认每页 20 条，最大每页 50 条
2. 若用户提供 target_type 过滤参数，Reaction_Service 应仅返回匹配指定 target_type（POST、COMMENT 或 RESOURCE）的收藏
3. 若用户提供无效或不支持的 target_type 过滤值，Reaction_Service 应返回错误消息指明该 target_type 不被识别
4. Reaction_Service 应按 reaction 创建时间戳倒序（最新优先）返回收藏
5. Reaction_Service 应暴露 GET /api/v1/users/me/favorites 端点，使用 reaction ID 作为游标参数实现游标分页
6. 当收藏的目标已被删除（状态标记为已移除）时，Reaction_Service 应将该项从收藏列表响应中排除
7. 若用户没有匹配请求条件的收藏项，Reaction_Service 应返回空列表且无错误
8. 若用户未认证，Reaction_Service 应拒绝请求并返回需要认证的错误

### Requirement 4: 通知批量已读

**User Story:** 作为用户，我希望一次性将多条通知标记为已读，以便高效管理通知收件箱。

#### Acceptance Criteria

1. 当用户提交包含通知 ID 列表的批量标记已读请求时，Notification_Service 应将属于该用户的所有指定通知标记为已读，并返回成功标记的数量
2. Notification_Service 应验证批次中每条通知是否属于请求用户，对不属于该用户或不存在的通知 ID 静默跳过，不拒绝整个请求
3. Notification_Service 应暴露 PUT /api/v1/notifications/batch-read 端点，在请求体中接受通知 ID 数组
4. 当用户提交空列表的批量标记已读请求时，Notification_Service 应返回成功响应且计数为 0，不修改任何记录
5. 若批量标记已读请求包含超过 100 个通知 ID，Notification_Service 应拒绝请求并返回错误响应，指明批次大小限制为 100
6. 当批量标记已读请求包含已经标记为已读的通知 ID 时，Notification_Service 应跳过这些通知且不视为错误

### Requirement 5: 私信批量已读

**User Story:** 作为用户，我希望批量将所有对话标记为已读，以便高效清除未读标记。

#### Acceptance Criteria

1. 当已认证用户提交全部消息标记已读请求时，Message_Service 应将该用户作为接收者的所有未读消息标记为已读，并返回 code 为 0 及更新的消息数量
2. Message_Service 应暴露 PUT /api/v1/messages/read-all 端点，需要认证
3. 当已认证用户没有未读消息并提交全部标记已读请求时，Message_Service 应返回 code 为 0 且更新数量为 0 的响应，不修改任何记录
4. 若未经有效认证提交全部标记已读请求，Message_Service 应拒绝请求并返回认证错误，不修改任何消息

### Requirement 6: 评论编辑

**User Story:** 作为评论作者，我希望能编辑我的评论，以便修正错别字或澄清回复内容。

#### Acceptance Criteria

1. 当作者提交评论更新请求且内容有效（1 至 5000 字符）时，Comment_Service 应更新评论内容并记录更新时间戳
2. Comment_Service 应在允许更新前验证请求用户是否为该评论的作者
3. 若非作者用户尝试编辑评论，Comment_Service 应拒绝请求并返回禁止错误，指明用户不是评论作者
4. 若更新内容为空或超过 5000 字符，Comment_Service 应拒绝请求并返回验证错误，指明内容长度约束
5. 评论更新时，Comment_Service 应对新内容重新执行敏感词过滤，若风险等级大于零则拒绝更新并返回检测到敏感内容的错误
6. 评论更新时，Comment_Service 应保留原始 created_at 时间戳
7. 若评论不存在或已被删除，Comment_Service 应返回未找到错误

### Requirement 7: 通用 API 限流

**User Story:** 作为平台运维人员，我希望对通用 API 端点进行限流，以便平台在高负载下保持稳定并防止滥用。

#### Acceptance Criteria

1. Rate_Limiter 应对通用 API 端点按已认证用户在滑动时间窗口内执行可配置的最大请求数限制，默认为每 60 秒 200 次请求
2. Rate_Limiter 应对未认证端点按 IP 地址在滑动时间窗口内执行可配置的最大请求数限制，默认为每 60 秒 100 次请求
3. 当用户超过限流阈值时，Rate_Limiter 应返回 HTTP 429 Too Many Requests 并附带 Retry-After 头，指明当前窗口重置前的剩余秒数
4. Rate_Limiter 应使用 Redis 作为分布式限流计数器的后端存储
5. Rate_Limiter 应支持通过配置进行按端点的限流覆盖，当某端点未定义覆盖时回退到全局默认限制
6. Rate_Limiter 应将健康检查和 actuator 端点（health、info、metrics）排除在限流之外
7. 若 Redis 不可用，Rate_Limiter 应允许请求通过（fail-open）并记录警告日志指明限流器处于降级状态

### Requirement 8: 文件在线预览

**User Story:** 作为用户，我希望在线预览文件（PDF、图片、Office 文档）而无需下载，以便快速评估内容相关性。

#### Acceptance Criteria

1. 当用户通过 GET /api/v1/resources/{id}/preview 请求预览可预览文件时，Resource_Service 应返回文件内容，Content-Disposition 设为 inline，Content-Type 匹配文件的媒体类型
2. 当用户请求预览 PDF 文件时，Resource_Service 应返回 Content-Type 为 application/pdf、Content-Disposition 为 inline 的文件
3. 当用户请求预览图片文件（jpg、jpeg、png、gif、webp）时，Resource_Service 应返回对应的图片 Content-Type（如 image/png、image/jpeg）且 Content-Disposition 为 inline
4. 当用户请求预览 Office 文档（doc、docx、ppt、pptx、xls、xlsx）时，Resource_Service 应返回重定向到已配置的文档预览服务 URL，其中包含文档的可访问下载链接
5. 若请求的资源 ID 不存在，Resource_Service 应返回 HTTP 404 Not Found 并附带资源未找到的错误消息
6. 若文件类型不可预览，Resource_Service 应返回 HTTP 415 Unsupported Media Type 并附带支持的文件类型说明
7. 若配置的文档预览服务不可用，Resource_Service 应返回 HTTP 502 Bad Gateway 并附带预览服务暂时不可用的错误消息
8. Resource_Service 应在预览端点上执行与下载端点相同的租户隔离和访问控制规则
9. 若文件大小超过 50 MB，Resource_Service 应返回 HTTP 413 Content Too Large 并附带文件超过最大可预览大小的错误消息

### Requirement 9: 管理后台数据导出

**User Story:** 作为管理员，我希望将平台数据（用户、帖子、审计日志）导出为 CSV 或 Excel 文件，以便进行离线分析和报告。

#### Acceptance Criteria

1. 当管理员请求数据导出并指定支持的格式（CSV 或 XLSX）时，Export_Service 应生成包含指定数据类型数据的对应格式文件
2. Export_Service 应支持导出以下数据类型：users、posts、audit_logs、reports
3. Export_Service 应在导出期间应用与正常数据访问相同的租户隔离规则
4. Export_Service 应暴露 POST /api/v1/admin/export/{dataType} 端点，仅限管理员角色访问
5. 当导出数据集超过 10,000 行时，Export_Service 应流式传输响应以避免内存耗尽
6. Export_Service 应在生成文件的第一行包含与导出实体字段名匹配的列标题
7. 若请求的 dataType 不是支持的数据类型（users、posts、audit_logs、reports）之一，Export_Service 应拒绝请求并返回数据类型不支持的错误消息
8. 若请求的格式不是 CSV 或 XLSX，Export_Service 应拒绝请求并返回格式不支持的错误消息
9. 当导出数据集为零行时，Export_Service 应返回仅包含列标题的有效文件

### Requirement 10: WebSocket 集群广播

**User Story:** 作为平台运维人员，我希望 WebSocket 通知能到达用户，无论用户连接到哪个应用实例，以便平台支持水平扩展。

#### Acceptance Criteria

1. 当通知被发布时，WebSocket_Broadcaster 应在发布事件后 2 秒内通过 Redis pub/sub 将消息广播到所有应用实例
2. 当应用实例收到广播消息时，WebSocket_Broadcaster 应将消息投递到本地连接的属于目标用户的所有 WebSocket 会话
3. WebSocket_Broadcaster 应使用专用的 Redis 频道进行 WebSocket 消息广播
4. 若 Redis 不可用超过 5 秒，WebSocket_Broadcaster 应仅向本地连接的会话投递消息，并记录警告日志指明 Redis 连接丢失
5. 当 Redis 连接在中断后恢复时，WebSocket_Broadcaster 应自动恢复向所有实例的广播，无需人工干预
6. WebSocket_Broadcaster 应使用 JSON 格式序列化广播消息，包含 userId、事件类型和载荷，最大序列化消息大小为 64 KB
7. 若目标用户在任何实例上都没有活跃的 WebSocket 会话，WebSocket_Broadcaster 应丢弃广播消息且不报错

### Requirement 11: 搜索索引同步

**User Story:** 作为平台运维人员，我希望搜索索引能自动与数据库内容保持同步，以便用户始终能找到最新的结果。

#### Acceptance Criteria

1. 当帖子被创建、更新或删除时，Search_Sync_Service 应在触发数据库变更后 5 秒内更新对应的 Meilisearch 文档
2. 当资源被创建、更新或删除时，Search_Sync_Service 应在触发数据库变更后 5 秒内更新对应的 Meilisearch 文档
3. Search_Sync_Service 应使用 Spring 应用事件将索引更新与业务逻辑解耦，即实体服务发布领域事件，Search_Sync_Service 独立消费
4. 若 Meilisearch 暂时不可用，Search_Sync_Service 应重试同步最多 3 次，采用指数退避策略，基础延迟 1 秒，每次翻倍
5. 若 3 次重试全部失败，Search_Sync_Service 应记录失败日志（包含实体类型、实体标识和操作类型），且不应阻塞或回滚原始业务操作
6. 当管理员向 POST /api/v1/admin/search/reindex 发送请求时，Search_Sync_Service 应从数据库重新索引所有帖子和资源到 Meilisearch，并返回重新索引的文档总数
7. 若非管理员用户尝试访问 POST /api/v1/admin/search/reindex，Search_Sync_Service 应拒绝请求并返回授权错误

### Requirement 12: 前端国际化（i18n）

**User Story:** 作为用户，我希望能以我偏好的语言使用平台，以便舒适地浏览和交互。

#### Acceptance Criteria

1. I18n_Module 应至少支持简体中文（zh-CN）和英文（en-US）两种语言，并为所有面向用户的文本提供翻译
2. I18n_Module 应将所有面向用户的文本存储在独立于组件代码的语言资源文件中
3. 当用户选择语言偏好时，I18n_Module 应将选择持久化到 localStorage，并在 500 毫秒内以所选语言渲染所有可见 UI 文本，无需整页刷新
4. 若 localStorage 中未存储用户语言偏好，I18n_Module 应通过 navigator.language 属性检测浏览器偏好语言，若匹配支持的语言则应用，否则默认使用 zh-CN
5. I18n_Module 应在应用头部提供语言切换组件，显示支持的语言列表
6. 当所选语言缺少某个语言资源键时，I18n_Module 应回退到该键的 zh-CN 语言值
7. 若某个语言资源键在所选语言和 zh-CN 回退中均缺失，I18n_Module 应将原始资源键字符串作为文本内容显示
