# 缺陷修复需求文档

## Introduction

本文档记录了通过代码分析在校园论坛后端中发现的 18 个缺陷。这些缺陷涵盖安全漏洞（缺少授权检查、JSON 注入）、数据完整性问题（并发竞态导致更新丢失）、逻辑错误（缺少校验）以及性能问题（全表扫描、缺少分页）。这些缺陷共同影响了平台的安全性、正确性和可靠性。

## Bug Analysis

### Current Behavior (Defect)

**严重 — 授权与租户隔离**

1.1 WHEN 已认证用户创建帖子时设置了一个自己并非成员的 `spaceId` THEN 系统允许帖子在该空间中创建，未校验用户是否为空间成员

1.2 WHEN TENANT_ADMIN 删除帖子时 THEN 系统完全依赖 SQL 租户拦截器进行租户校验，未在应用层验证帖子的 `tenantId` 是否与管理员当前租户上下文一致

**高 — 竞态条件（更新丢失）**

1.3 WHEN 两个并发请求同时为同一用户调用 `PointsService.award()` 或 `PointsService.spend()` THEN 系统使用读-改-写模式（`selectById` → `setPoints` → `updateById`），导致其中一次更新丢失

1.4 WHEN 两个并发请求对同一帖子触发 `PostService.toggleReaction()` 或 `PostService.getById()` THEN 系统对 `likeCount`、`viewCount`、`commentCount` 使用读-改-写模式，导致计数值丢失

1.5 WHEN 两个并发请求对同一空间调用 `SpaceService.join()`、`leave()`、`approveMember()` 或 `removeMember()` THEN 系统使用 `setMemberCount(getMemberCount() + 1)` 模式，导致成员数更新丢失

**高 — 访问控制与数据泄露**

1.6 WHEN 未认证或非成员用户请求 `GET /api/v1/spaces/{id}/posts` 访问 PRIVATE 空间 THEN 系统返回该空间的帖子，未校验请求者是否为空间成员

1.7 WHEN 空间管理员调用 `PUT /api/v1/spaces/{spaceId}/posts/{postId}/status` 且 `postId` 属于另一个空间 THEN 系统修改了帖子状态，未校验帖子是否属于指定空间

1.8 WHEN `PostService.getById()` 因任何原因被调用（内部服务调用、管理员查看、同一用户重复加载页面） THEN 系统在每次调用时都递增 `viewCount`

**中 — 输入校验与逻辑错误**

1.9 WHEN `MessageService.send()` 构造 WebSocket JSON 载荷且发送者昵称包含 `"` 或 `\` 等字符 THEN 系统使用字符串拼接生成格式错误/可注入的 JSON

1.10 WHEN `QaService.accept()` 被调用时传入的 `commentId` 不属于指定的 `postId` THEN 系统接受该答案，未校验评论与帖子的归属关系

1.11 WHEN 两个并发请求在同一天为同一用户和挑战调用 `CheckinService.checkin()` THEN 系统的先检查后插入操作不是原子的，允许创建重复的打卡记录

1.12 WHEN TENANT_ADMIN 调用 `UserService.changeRole()` 将另一用户提升为 TENANT_ADMIN THEN 系统允许提升操作，未要求更高权限级别（只有 SUPER_ADMIN 才应能创建新的 TENANT_ADMIN）

**低 — JSON 注入、校验、性能与空指针**

1.13 WHEN `NotifyService.create()` 构造 WebSocket JSON 载荷且通知标题或内容包含 `"` 或 `\` 字符 THEN 系统使用字符串拼接生成格式错误/可注入的 JSON

1.14 WHEN 举报控制器接收请求体为原始 `Map<String, Object>` THEN 系统未对举报字段进行任何输入校验

1.15 WHEN `UserService.findSubscribedUserIds()` 被调用 THEN 系统将所有具有非空 `tagSubscriptions` 的用户加载到内存中并在 Java 中遍历，而非在 SQL 层过滤

1.16 WHEN `MessageService.listConversations()` 被调用 THEN 系统加载该用户发送或接收的所有消息，无分页，对活跃用户造成内存问题

1.17 WHEN `PostService.page()` 被调用 THEN 系统在每次列表请求时同步调用 `cleanExpiredPins()`，在每次分页加载时执行查询和潜在的更新操作

1.18 WHEN `SpaceService.checkOwnership()` 传入 null 的 `space` 参数且 `spaceId` 不存在时，或 `PostService.toggleReaction()` 遇到 null 帖子时，或 `CommentService.create()` 遇到 null 父评论时 THEN 系统抛出未处理的 NullPointerException 而非适当的业务异常

### Expected Behavior (Correct)

**严重 — 授权与租户隔离**

2.1 WHEN 已认证用户创建帖子时设置了 `spaceId` THEN 系统 SHALL 在允许创建帖子前验证用户是该空间的活跃成员，若非成员则 SHALL 以 FORBIDDEN 错误拒绝

2.2 WHEN TENANT_ADMIN 删除帖子时 THEN 系统 SHALL 在应用层验证帖子的 `tenantId` 与当前用户会话中的租户上下文一致（在 SQL 拦截器之外），若不匹配则 SHALL 以 FORBIDDEN 拒绝

**高 — 竞态条件（更新丢失）**

2.3 WHEN `PointsService.award()` 或 `PointsService.spend()` 被调用 THEN 系统 SHALL 使用原子 SQL 更新（`UPDATE user SET points = points + ? WHERE id = ?`）代替读-改-写模式，防止并发下的更新丢失

2.4 WHEN 帖子计数器（`likeCount`、`viewCount`、`commentCount`）被修改 THEN 系统 SHALL 使用原子 SQL 更新（`UPDATE post SET like_count = like_count + 1 WHERE id = ?`）代替读-改-写模式，防止并发下的更新丢失

2.5 WHEN 空间 `memberCount` 在 `join()`、`leave()`、`approveMember()` 或 `removeMember()` 中被修改 THEN 系统 SHALL 使用原子 SQL 更新（`UPDATE space SET member_count = member_count + 1 WHERE id = ?`）代替读-改-写模式，防止并发下的更新丢失

**高 — 访问控制与数据泄露**

2.6 WHEN 用户请求 `GET /api/v1/spaces/{id}/posts` 访问 PRIVATE 空间 THEN 系统 SHALL 验证请求者是该空间的活跃成员，若非成员则 SHALL 返回 FORBIDDEN（PUBLIC 空间仍对所有人可访问）

2.7 WHEN 空间管理员调用 `PUT /api/v1/spaces/{spaceId}/posts/{postId}/status` THEN 系统 SHALL 验证帖子的 `spaceId` 与路径参数 `spaceId` 一致，若帖子不属于该空间则 SHALL 以 NOT_FOUND 或 FORBIDDEN 拒绝

2.8 WHEN `PostService.getById()` 被调用 THEN 系统 SHALL 仅对不同的终端用户查看递增 `viewCount`（例如通过检查调用者是否为已认证的非管理员用户发起的显式查看请求），SHALL NOT 对内部服务调用、管理员查看或短时间内的重复加载递增

**中 — 输入校验与逻辑错误**

2.9 WHEN 在 `MessageService.send()` 中构造 WebSocket JSON 载荷 THEN 系统 SHALL 使用正规的 JSON 序列化器（如 ObjectMapper）安全编码所有动态值，防止昵称或内容中的特殊字符导致 JSON 注入

2.10 WHEN `QaService.accept()` 被调用时传入 `commentId` THEN 系统 SHALL 验证该评论的 `postId` 与指定帖子一致后再接受，若评论不属于该帖子则 SHALL 以 BAD_REQUEST 拒绝

2.11 WHEN `CheckinService.checkin()` 被调用 THEN 系统 SHALL 使用数据库级唯一约束 `(challenge_id, user_id, checkin_date)` 或原子的检查并插入机制，防止并发请求下产生重复打卡记录

2.12 WHEN `UserService.changeRole()` 被调用设置角色为 TENANT_ADMIN THEN 系统 SHALL 验证调用者具有 SUPER_ADMIN 权限，若调用者仅为 TENANT_ADMIN 则 SHALL 以 FORBIDDEN 拒绝

**低 — JSON 注入、校验、性能与空指针**

2.13 WHEN 在 `NotifyService.create()` 中构造 WebSocket JSON 载荷 THEN 系统 SHALL 使用正规的 JSON 序列化器（如 ObjectMapper）安全编码所有动态值，防止特殊字符导致 JSON 注入

2.14 WHEN 举报控制器接收请求 THEN 系统 SHALL 使用带有 Jakarta 校验注解的类型化 DTO 进行输入校验，而非原始 `Map<String, Object>`

2.15 WHEN `findSubscribedUserIds()` 被调用 THEN 系统 SHALL 使用 SQL LIKE 或 JSON 查询在数据库层按标签订阅过滤用户，避免将所有用户加载到内存中

2.16 WHEN `MessageService.listConversations()` 被调用 THEN 系统 SHALL 实现分页（游标或限制数量），避免将用户的所有消息加载到内存中

2.17 WHEN 需要清理过期置顶帖 THEN 系统 SHALL 通过定时任务（如 `@Scheduled`）执行清理，而非在每次 `page()` 请求时同步执行

2.18 WHEN 服务方法从数据库查询遇到 null 实体 THEN 系统 SHALL 执行显式空值检查并抛出适当的 `BusinessException` 错误（如 NOT_FOUND），而非允许 NullPointerException 传播

### Unchanged Behavior (Regression Prevention)

**授权与租户隔离**

3.1 WHEN 已认证用户是某空间的成员并创建帖子设置该 `spaceId` THEN 系统 SHALL CONTINUE TO 在该空间中成功创建帖子

3.2 WHEN TENANT_ADMIN 删除属于自己租户的帖子 THEN 系统 SHALL CONTINUE TO 成功删除帖子

3.3 WHEN 帖子作者删除自己的帖子 THEN 系统 SHALL CONTINUE TO 成功删除帖子，不受管理员角色影响

**竞态条件**

3.4 WHEN 单个（非并发）请求奖励或消费积分 THEN 系统 SHALL CONTINUE TO 正确更新用户积分余额并创建积分日志记录

3.5 WHEN 单个（非并发）请求点赞或取消点赞帖子 THEN 系统 SHALL CONTINUE TO 正确更新帖子的 `likeCount` 并创建/删除反应记录

3.6 WHEN 单个（非并发）请求加入或退出空间 THEN 系统 SHALL CONTINUE TO 正确更新空间的 `memberCount`

**访问控制与数据泄露**

3.7 WHEN PRIVATE 空间的成员请求该空间的帖子 THEN 系统 SHALL CONTINUE TO 成功返回帖子

3.8 WHEN 空间管理员修改属于其空间的帖子状态 THEN 系统 SHALL CONTINUE TO 成功更新帖子状态

3.9 WHEN 终端用户显式查看帖子详情页 THEN 系统 SHALL CONTINUE TO 为该次查看递增 `viewCount`

**输入校验与逻辑**

3.10 WHEN `MessageService.send()` 被调用且发送者昵称仅包含安全的 ASCII 字符 THEN 系统 SHALL CONTINUE TO 以正确的 JSON 格式推送 WebSocket 通知

3.11 WHEN `QaService.accept()` 被调用且 `commentId` 属于指定帖子 THEN 系统 SHALL CONTINUE TO 接受答案、转移悬赏积分并通知回答者

3.12 WHEN 用户在某天首次对某挑战打卡（无并发） THEN 系统 SHALL CONTINUE TO 创建打卡记录并奖励积分

3.13 WHEN SUPER_ADMIN 调用 `changeRole()` 将用户提升为 TENANT_ADMIN THEN 系统 SHALL CONTINUE TO 成功修改角色

**性能与稳定性**

3.14 WHEN `NotifyService.create()` 被调用且内容安全 THEN 系统 SHALL CONTINUE TO 成功推送 WebSocket 通知

3.15 WHEN `PostService.page()` 被调用 THEN 系统 SHALL CONTINUE TO 正确返回分页帖子（置顶过期清理仍会执行，只是不再在每次请求时同步执行）

3.16 WHEN 服务方法接收到数据库中存在的有效实体 ID THEN 系统 SHALL CONTINUE TO 正常处理，行为无任何变化
