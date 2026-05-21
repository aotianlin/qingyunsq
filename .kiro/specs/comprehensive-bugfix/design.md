# 缺陷修复技术设计文档

## Introduction

本文档描述 18 个缺陷的技术修复方案。修复策略按类别分组：原子 SQL 更新解决竞态条件、应用层权限校验加固访问控制、ObjectMapper 替代字符串拼接消除 JSON 注入、类型化 DTO 替代原始 Map 实现输入校验、SQL 层过滤和分页优化性能、显式空值检查防止 NPE。

## Fix Strategy Overview

| 缺陷编号 | 修复策略 | 涉及文件 |
|---------|---------|---------|
| 1.1 | PostService.create() 添加空间成员校验 | PostService.java |
| 1.2 | PostService.deletePost() 添加应用层 tenantId 校验 | PostService.java |
| 1.3 | UserMapper 添加原子积分更新 SQL | UserMapper.java, PointsService.java |
| 1.4 | PostMapper 添加原子计数器更新 SQL | PostMapper.java, PostService.java, CommentService.java |
| 1.5 | SpaceMapper 添加原子 memberCount 更新 SQL | SpaceMapper.java, SpaceService.java |
| 1.6 | SpaceController spacePosts() 添加私有空间成员校验 | SpaceController.java, SpaceService.java |
| 1.7 | SpaceController setPostStatus() 添加帖子归属校验 | SpaceController.java |
| 1.8 | PostService.getById() 拆分为内部调用和用户查看 | PostService.java, PostController.java |
| 1.9 | MessageService.send() 使用 ObjectMapper 构造 JSON | MessageService.java |
| 1.10 | QaService.accept() 添加评论归属校验 | QaService.java |
| 1.11 | CheckinRecord 添加数据库唯一约束 + 异常捕获 | SQL migration, CheckinService.java |
| 1.12 | UserService.changeRole() 添加 SUPER_ADMIN 权限校验 | UserService.java |
| 1.13 | NotifyService.create() 使用 ObjectMapper 构造 JSON | NotifyService.java |
| 1.14 | ReportController 使用类型化 DTO + Jakarta 校验 | ReportController.java, CreateReportRequest.java |
| 1.15 | UserMapper 添加 SQL LIKE 查询方法 | UserMapper.java, UserService.java |
| 1.16 | MessageService.listConversations() 添加分页 | MessageService.java, MessageMapper.java |
| 1.17 | PostService 将 cleanExpiredPins() 移至 @Scheduled | PostService.java / PinCleanupTask.java |
| 1.18 | 各服务方法添加显式空值检查 | SpaceService.java, PostService.java, CommentService.java |

---

## Detailed Design

### Fix 1.1 — 创建帖子时校验空间成员身份

**文件**: `PostService.java`

**修改点**: 在 `create()` 方法中，当 `req.getSpaceId() != null` 时，查询 `SpaceMember` 表验证当前用户是该空间的活跃成员（status=1）。

```java
// PostService.create() — 在 postMapper.insert(post) 之前添加
if (req.getSpaceId() != null) {
    SpaceMember member = spaceMemberMapper.selectOne(new LambdaQueryWrapper<SpaceMember>()
            .eq(SpaceMember::getSpaceId, req.getSpaceId())
            .eq(SpaceMember::getUserId, userId)
            .eq(SpaceMember::getStatus, 1));
    if (member == null) {
        throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "非空间成员，无法发帖");
    }
}
```

**新增依赖**: PostService 注入 `SpaceMemberMapper`。

---

### Fix 1.2 — 管理员删帖时应用层租户校验

**文件**: `PostService.java`

**修改点**: 在 `deletePost()` 方法中，当操作者角色为 TENANT_ADMIN 时，从 session 获取 tenantId 并与帖子的 tenantId 比较。

```java
// PostService.deletePost() — 在权限检查通过后、执行删除前添加
if ("TENANT_ADMIN".equals(role) && !post.getAuthorId().equals(userId)) {
    Long sessionTenantId = (Long) StpUtil.getSession().get("tenantId");
    if (sessionTenantId != null && !sessionTenantId.equals(post.getTenantId())) {
        throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权操作其他租户的帖子");
    }
}
```

---

### Fix 1.3 — 积分原子更新

**文件**: `UserMapper.java`, `PointsService.java`

**UserMapper 新增方法**:
```java
@Update("UPDATE user SET points = points + #{amount} WHERE id = #{userId}")
int incrementPoints(@Param("userId") Long userId, @Param("amount") long amount);

@Update("UPDATE user SET points = points - #{amount} WHERE id = #{userId} AND points >= #{amount}")
int decrementPoints(@Param("userId") Long userId, @Param("amount") long amount);
```

**PointsService.award() 修改**:
```java
@Transactional
public void award(Long userId, long amount, String type, String reference) {
    if (amount <= 0) return;
    User user = userMapper.selectById(userId);
    if (user == null) return;

    PointsLog pl = new PointsLog();
    pl.setUserId(userId);
    pl.setAmount(amount);
    pl.setType(type);
    pl.setReference(reference);
    pointsLogMapper.insert(pl);

    userMapper.incrementPoints(userId, amount);  // 原子更新替代 read-modify-write
    log.info("Points awarded: userId={}, amount={}, type={}", userId, amount, type);
}
```

**PointsService.spend() 修改**:
```java
@Transactional
public boolean spend(Long userId, long amount, String type, String reference) {
    if (amount <= 0) return false;
    int rows = userMapper.decrementPoints(userId, amount);  // 原子扣减，余额不足时 rows=0
    if (rows == 0) return false;

    PointsLog pl = new PointsLog();
    pl.setUserId(userId);
    pl.setAmount(-amount);
    pl.setType(type);
    pl.setReference(reference);
    pointsLogMapper.insert(pl);

    log.info("Points spent: userId={}, amount={}, type={}", userId, amount, type);
    return true;
}
```


---

### Fix 1.4 — 帖子计数器原子更新

**文件**: `PostMapper.java`, `PostService.java`, `CommentService.java`

**PostMapper 新增方法**:
```java
@Update("UPDATE post SET like_count = like_count + #{delta} WHERE id = #{postId}")
int incrementLikeCount(@Param("postId") Long postId, @Param("delta") int delta);

@Update("UPDATE post SET view_count = view_count + 1 WHERE id = #{postId}")
int incrementViewCount(@Param("postId") Long postId);

@Update("UPDATE post SET comment_count = comment_count + #{delta} WHERE id = #{postId}")
int incrementCommentCount(@Param("postId") Long postId, @Param("delta") int delta);
```

**PostService.toggleReaction() 修改** — 替换 read-modify-write:
```java
// 取消点赞
reactionMapper.deleteById(existing.getId());
if ("POST".equals(req.getTargetType()) && "LIKE".equals(req.getType())) {
    postMapper.incrementLikeCount(req.getTargetId(), -1);
}

// 新增点赞
reactionMapper.insert(reaction);
if ("POST".equals(req.getTargetType()) && "LIKE".equals(req.getType())) {
    postMapper.incrementLikeCount(req.getTargetId(), 1);
}
```

**CommentService.create() 修改** — 替换 read-modify-write:
```java
// 替换原来的 post.setCommentCount(post.getCommentCount() + 1); postMapper.updateById(post);
postMapper.incrementCommentCount(req.getPostId(), 1);
```

---

### Fix 1.5 — 空间 memberCount 原子更新

**文件**: `SpaceMapper.java`, `SpaceService.java`

**SpaceMapper 新增方法**:
```java
@Update("UPDATE space SET member_count = member_count + #{delta} WHERE id = #{spaceId}")
int incrementMemberCount(@Param("spaceId") Long spaceId, @Param("delta") int delta);
```

**SpaceService 修改** — 所有 `space.setMemberCount(...)` + `spaceMapper.updateById(space)` 替换为:
```java
// join() 中 memberStatus == 1 时:
spaceMapper.incrementMemberCount(spaceId, 1);

// leave() 中:
spaceMapper.incrementMemberCount(spaceId, -1);

// approveMember() 中:
spaceMapper.incrementMemberCount(spaceId, 1);

// removeMember() 中:
spaceMapper.incrementMemberCount(spaceId, -1);
```

---

### Fix 1.6 — 私有空间帖子列表访问控制

**文件**: `SpaceController.java`, `SpaceService.java`

**SpaceService 新增方法**:
```java
public void checkMemberAccess(Long spaceId, Long userId) {
    Space space = spaceMapper.selectById(spaceId);
    if (space == null || space.getDeleted() == 1) {
        throw new BusinessException(ErrorCode.SPACE_NOT_FOUND);
    }
    if ("PRIVATE".equals(space.getVisibility())) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "私有空间需登录访问");
        }
        SpaceMember member = memberMapper.selectOne(new LambdaQueryWrapper<SpaceMember>()
                .eq(SpaceMember::getSpaceId, spaceId)
                .eq(SpaceMember::getUserId, userId)
                .eq(SpaceMember::getStatus, 1));
        if (member == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "非空间成员，无法查看帖子");
        }
    }
}
```

**SpaceController.spacePosts() 修改**:
```java
@GetMapping("/{id}/posts")
public R<List<PostVO>> spacePosts(@PathVariable Long id, ...) {
    Long userId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
    spaceService.checkMemberAccess(id, userId);
    return R.ok(postService.pageBySpace(id, false, cursor, limit));
}
```

---

### Fix 1.7 — 帖子状态修改时校验帖子归属空间

**文件**: `SpaceController.java`

**修改 setPostStatus()**:
```java
@PutMapping("/{id}/posts/{postId}/status")
public R<Void> setPostStatus(@PathVariable Long id, @PathVariable Long postId, @RequestParam Integer status) {
    Long userId = StpUtil.getLoginIdAsLong();
    spaceService.checkSpaceAdmin(id, userId);
    // 新增：校验帖子属于该空间
    Post post = postMapper.selectById(postId);
    if (post == null || post.getDeleted() == 1 || !id.equals(post.getSpaceId())) {
        throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "帖子不存在或不属于该空间");
    }
    postService.setStatus(postId, status);
    return R.ok();
}
```

**注意**: 需要在 SpaceController 中注入 `PostMapper`，或将校验逻辑封装到 PostService 中（推荐后者）。推荐方案：在 `PostService.setStatus()` 中增加 `spaceId` 参数进行校验。

---

### Fix 1.8 — viewCount 仅对终端用户显式查看递增

**文件**: `PostService.java`, `PostController.java`

**设计思路**: 将 `getById()` 拆分为两个方法：
- `getById(Long id)` — 内部调用，不递增 viewCount
- `viewPost(Long id)` — 用户显式查看，递增 viewCount

**PostService 修改**:
```java
public PostVO getById(Long id) {
    Post post = postMapper.selectById(id);
    if (post == null || post.getDeleted() == 1) {
        throw new BusinessException(ErrorCode.POST_NOT_FOUND);
    }
    Long currentUserId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
    return toVO(post, currentUserId);
}

public PostVO viewPost(Long id) {
    Post post = postMapper.selectById(id);
    if (post == null || post.getDeleted() == 1) {
        throw new BusinessException(ErrorCode.POST_NOT_FOUND);
    }
    // 仅对已登录的非管理员用户递增浏览量（使用原子更新）
    Long currentUserId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
    if (currentUserId != null) {
        String role = (String) StpUtil.getSession().get("role");
        if (!"TENANT_ADMIN".equals(role) && !"SUPER_ADMIN".equals(role)) {
            postMapper.incrementViewCount(id);
        }
    }
    return toVO(post, currentUserId);
}
```

**PostController 修改**: 帖子详情接口调用 `viewPost()` 而非 `getById()`。


---

### Fix 1.9 — MessageService WebSocket JSON 注入修复

**文件**: `MessageService.java`

**修改 send() 方法中的 WebSocket 推送**:
```java
// 替换字符串拼接为 ObjectMapper 序列化
try {
    User sender = userMapper.selectById(senderId);
    String senderName = sender != null ? sender.getNickname() : "有人";
    Map<String, Object> payloadMap = new LinkedHashMap<>();
    payloadMap.put("type", "MESSAGE");
    payloadMap.put("senderId", senderId);
    payloadMap.put("senderName", senderName);
    payloadMap.put("content", content != null ? content.substring(0, Math.min(content.length(), 50)) : "[图片]");
    String payload = objectMapper.writeValueAsString(payloadMap);
    sessionRegistry.sendToUser(receiverId, payload);
} catch (Exception ignored) {}
```

**新增依赖**: MessageService 注入 `ObjectMapper`（Spring Boot 自动配置的 Bean）。

---

### Fix 1.10 — QaService.accept() 评论归属校验

**文件**: `QaService.java`

**修改 accept() 方法** — 在设置 acceptedCommentId 前校验:
```java
Comment acceptedComment = commentMapper.selectById(commentId);
if (acceptedComment == null) {
    throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "评论不存在");
}
if (!acceptedComment.getPostId().equals(postId)) {
    throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "该评论不属于此帖子");
}
```

将此校验移到 `qa.setAcceptedCommentId(commentId)` 之前执行。

---

### Fix 1.11 — 打卡记录唯一约束防并发重复

**文件**: SQL migration, `CheckinService.java`

**数据库迁移** — 添加唯一索引:
```sql
ALTER TABLE checkin_record ADD UNIQUE INDEX uk_challenge_user_date (challenge_id, user_id, checkin_date);
```

**CheckinService.checkin() 修改** — 捕获唯一约束冲突:
```java
try {
    recordMapper.insert(record);
} catch (org.springframework.dao.DuplicateKeyException e) {
    throw new BusinessException(ErrorCode.ALREADY_CHECKED_IN);
}
```

移除原有的先查询后判断逻辑（`selectOne` 检查），改为依赖数据库约束保证原子性。保留查询作为快速失败路径（减少不必要的 AI 检测调用），但最终一致性由唯一约束保证。

---

### Fix 1.12 — changeRole() 权限提升限制

**文件**: `UserService.java`

**修改 changeRole() 方法**:
```java
@Transactional
public void changeRole(Long userId, String role) {
    User user = userMapper.selectById(userId);
    if (user == null) {
        throw new BusinessException(ErrorCode.USER_NOT_FOUND);
    }
    if (!List.of("USER", "TENANT_ADMIN").contains(role)) {
        throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "无效的角色");
    }
    // 新增：提升为 TENANT_ADMIN 需要 SUPER_ADMIN 权限
    if ("TENANT_ADMIN".equals(role)) {
        String callerRole = (String) StpUtil.getSession().get("role");
        if (!"SUPER_ADMIN".equals(callerRole)) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "仅超级管理员可提升用户为租户管理员");
        }
    }
    user.setRole(role);
    userMapper.updateById(user);
    log.info("User role changed: id={}, role={}", userId, role);
}
```

---

### Fix 1.13 — NotifyService WebSocket JSON 注入修复

**文件**: `NotifyService.java`

**修改 create() 方法中的 WebSocket 推送**:
```java
// 替换字符串拼接为 ObjectMapper 序列化
try {
    Map<String, Object> payloadMap = new LinkedHashMap<>();
    payloadMap.put("type", type != null ? type : "");
    payloadMap.put("title", title != null ? title : "");
    payloadMap.put("content", content != null ? content : "");
    String payload = jsonMapper.writeValueAsString(payloadMap);
    sessionRegistry.sendToUser(receiverId, payload);
} catch (Exception ignored) {}
```

`jsonMapper` 已存在于 NotifyService 中（`private static final ObjectMapper jsonMapper`），直接复用。

---

### Fix 1.14 — 举报控制器类型化 DTO

**新增文件**: `CreateReportRequest.java`
```java
package com.campusforum.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateReportRequest {
    @NotBlank(message = "举报目标类型不能为空")
    private String targetType;

    @NotNull(message = "举报目标ID不能为空")
    private Long targetId;

    @NotBlank(message = "举报原因不能为空")
    private String reason;

    private String description;
}
```

**修改文件**: `ReportController.java`
```java
@PostMapping
public R<Void> create(@Valid @RequestBody CreateReportRequest req) {
    reportService.create(req.getTargetType(), req.getTargetId(), req.getReason(), req.getDescription());
    return R.ok();
}
```


---

### Fix 1.15 — findSubscribedUserIds() SQL 层过滤

**文件**: `UserMapper.java`, `UserService.java`

**UserMapper 新增方法**:
```java
@Select("<script>" +
        "SELECT id FROM user WHERE tag_subscriptions IS NOT NULL " +
        "AND tag_subscriptions != '' AND tag_subscriptions != '[]' " +
        "AND (" +
        "<foreach collection='tags' item='tag' separator=' OR '>" +
        "tag_subscriptions LIKE CONCAT('%', #{tag}, '%')" +
        "</foreach>" +
        ")" +
        "</script>")
List<Long> selectUserIdsByTagSubscription(@Param("tags") List<String> tags);
```

**UserService.findSubscribedUserIds() 修改**:
```java
public Set<Long> findSubscribedUserIds(List<String> tags) {
    if (tags == null || tags.isEmpty()) return Set.of();
    List<Long> candidateIds = userMapper.selectUserIdsByTagSubscription(tags);
    // 二次精确过滤（SQL LIKE 可能有误匹配，如 tag "java" 匹配 "javascript"）
    Set<Long> result = new HashSet<>();
    for (Long uid : candidateIds) {
        User user = userMapper.selectById(uid);
        if (user == null || user.getTagSubscriptions() == null) continue;
        try {
            Set<String> subs = jsonMapper.readValue(user.getTagSubscriptions(),
                    new TypeReference<Set<String>>() {});
            for (String tag : tags) {
                if (subs.contains(tag)) {
                    result.add(uid);
                    break;
                }
            }
        } catch (JsonProcessingException ignored) {}
    }
    return result;
}
```

**说明**: SQL LIKE 作为粗筛大幅减少加载到内存的用户数量，Java 层精确匹配保证正确性。

---

### Fix 1.16 — MessageService.listConversations() 分页

**文件**: `MessageService.java`, `MessageMapper.java`

**设计思路**: 使用子查询获取每个对话的最新消息 ID，然后分页加载。

**MessageMapper 新增方法**:
```java
@Select("SELECT MAX(id) FROM message " +
        "WHERE (sender_id = #{userId} OR receiver_id = #{userId}) " +
        "GROUP BY CASE WHEN sender_id = #{userId} THEN receiver_id ELSE sender_id END " +
        "ORDER BY MAX(id) DESC " +
        "LIMIT #{limit}")
List<Long> selectLatestMessageIdsPerConversation(@Param("userId") Long userId, @Param("limit") int limit);
```

**MessageService.listConversations() 修改**:
```java
public List<MessageVO> listConversations(Long userId) {
    // 限制最多返回 50 个对话
    List<Long> latestIds = messageMapper.selectLatestMessageIdsPerConversation(userId, 50);
    if (latestIds.isEmpty()) return List.of();
    List<Message> messages = messageMapper.selectBatchIds(latestIds);
    // 按 ID 倒序排列
    messages.sort((a, b) -> Long.compare(b.getId(), a.getId()));
    return messages.stream().map(this::toVO).toList();
}
```

---

### Fix 1.17 — cleanExpiredPins() 移至定时任务

**新增文件**: `com.campusforum.post.task.PinCleanupTask.java`
```java
package com.campusforum.post.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusforum.post.domain.Post;
import com.campusforum.post.mapper.PostMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PinCleanupTask {

    private final PostMapper postMapper;

    @Scheduled(cron = "0 0 2 * * ?")  // 每天凌晨 2 点执行
    @Transactional
    public void cleanExpiredPins() {
        LambdaQueryWrapper<Post> qw = new LambdaQueryWrapper<>();
        qw.eq(Post::getIsPinned, 1);
        qw.isNotNull(Post::getPinnedAt);
        qw.lt(Post::getPinnedAt, LocalDateTime.now().minusDays(30));
        List<Post> expired = postMapper.selectList(qw);
        for (Post p : expired) {
            p.setIsPinned(0);
            p.setPinnedAt(null);
            postMapper.updateById(p);
            log.info("Auto-unpinned post {}", p.getId());
        }
        if (!expired.isEmpty()) {
            log.info("Cleaned {} expired pinned posts", expired.size());
        }
    }
}
```

**PostService.page() 修改**: 移除 `cleanExpiredPins()` 调用。

**前提**: 确保 Application 类或配置类上有 `@EnableScheduling` 注解。

---

### Fix 1.18 — 显式空值检查防止 NPE

**文件**: `SpaceService.java`, `PostService.java`, `CommentService.java`

**SpaceService.checkOwnership() 修改**:
```java
private void checkOwnership(Long spaceId, Long userId, Space space) {
    Space s = space != null ? space : spaceMapper.selectById(spaceId);
    if (s == null) {
        throw new BusinessException(ErrorCode.SPACE_NOT_FOUND);
    }
    // ... 后续逻辑不变
}
```

**PostService.toggleReaction() 修改**:
```java
// 在点赞/取消点赞时
Post post = postMapper.selectById(req.getTargetId());
if (post == null || post.getDeleted() == 1) {
    throw new BusinessException(ErrorCode.POST_NOT_FOUND);
}
```

**CommentService.create() 修改** — 父评论空值检查:
```java
if (req.getParentId() != null) {
    Comment parentComment = commentMapper.selectById(req.getParentId());
    if (parentComment == null) {
        throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND.getCode(), "父评论不存在");
    }
    // ... 后续通知逻辑
}
```

---

## Testing Strategy

每个修复应包含对应的单元测试验证：

1. **授权类修复 (1.1, 1.2, 1.6, 1.7, 1.12)**: 测试非授权用户被拒绝、授权用户正常通过
2. **竞态条件修复 (1.3, 1.4, 1.5)**: 验证原子 SQL 方法存在且语法正确；并发测试可选
3. **JSON 注入修复 (1.9, 1.13)**: 测试包含特殊字符的输入生成合法 JSON
4. **逻辑校验修复 (1.10, 1.11)**: 测试非法输入被拒绝
5. **性能修复 (1.15, 1.16, 1.17)**: 验证新方法存在且逻辑正确
6. **NPE 修复 (1.18)**: 测试 null 输入抛出 BusinessException 而非 NPE

## Migration Notes

- **数据库迁移**: Fix 1.11 需要添加唯一索引，需确保现有数据无重复记录，否则迁移会失败。建议先清理重复数据再添加约束。
- **@EnableScheduling**: Fix 1.17 需要确保 Spring Boot 应用启用了定时任务调度。
- **向后兼容**: 所有修复均为内部实现变更，不改变 API 接口签名（除 Fix 1.14 将 Map 替换为 DTO，但请求体 JSON 结构不变）。
