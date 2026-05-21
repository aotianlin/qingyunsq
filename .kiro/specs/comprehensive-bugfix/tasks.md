# Implementation Plan

## Overview

本实现计划覆盖 18 个缺陷的修复，按依赖关系分为 6 个任务组共 20 个子任务。Task 2（原子 SQL 方法）是多个后续任务的前置依赖。

## Tasks

- [x] 1. 授权与租户隔离修复
  - [x] 1.1 PostService.create() 添加空间成员校验
    - Inject SpaceMemberMapper into PostService
    - Before postMapper.insert(post), when req.getSpaceId() != null, query SpaceMember table to verify user is active member (status=1)
    - If not a member, throw BusinessException with FORBIDDEN
  - [x] 1.2 PostService.deletePost() 添加应用层租户校验
    - When role is TENANT_ADMIN and user is not the post author, get tenantId from session
    - Compare session tenantId with post.getTenantId()
    - If mismatch, throw BusinessException with FORBIDDEN
  - [x] 1.3 UserService.changeRole() 添加 SUPER_ADMIN 权限校验
    - When target role is TENANT_ADMIN, check caller's role from session
    - If caller is not SUPER_ADMIN, throw BusinessException with FORBIDDEN
- [x] 2. 竞态条件修复 — Mapper 原子 SQL 方法
  - [x] 2.1 UserMapper 添加原子积分更新方法
    - Add incrementPoints(@Param("userId") Long userId, @Param("amount") long amount) with @Update("UPDATE user SET points = points + #{amount} WHERE id = #{userId}")
    - Add decrementPoints(@Param("userId") Long userId, @Param("amount") long amount) with @Update("UPDATE user SET points = points - #{amount} WHERE id = #{userId} AND points >= #{amount}")
  - [x] 2.2 PostMapper 添加原子计数器更新方法
    - Add incrementLikeCount(@Param("postId") Long postId, @Param("delta") int delta) with @Update
    - Add incrementViewCount(@Param("postId") Long postId) with @Update
    - Add incrementCommentCount(@Param("postId") Long postId, @Param("delta") int delta) with @Update
  - [x] 2.3 SpaceMapper 添加原子 memberCount 更新方法
    - Add incrementMemberCount(@Param("spaceId") Long spaceId, @Param("delta") int delta) with @Update("UPDATE space SET member_count = member_count + #{delta} WHERE id = #{spaceId}")
- [x] 3. 竞态条件修复 — Service 层使用原子更新
  - [x] 3.1 PointsService 使用原子更新替代 read-modify-write
    - In award(): replace user.setPoints() + updateById() with userMapper.incrementPoints()
    - In spend(): replace balance check + setPoints + updateById with userMapper.decrementPoints(), check rows == 0 for insufficient balance
  - [x] 3.2 PostService.toggleReaction() 使用原子计数器更新
    - Replace post.setLikeCount() + postMapper.updateById() with postMapper.incrementLikeCount()
    - Add null/deleted check for post before counter update (also fixes NPE bug 1.18)
  - [x] 3.3 CommentService.create() 使用原子计数器更新
    - Replace post.setCommentCount() + postMapper.updateById() with postMapper.incrementCommentCount()
  - [x] 3.4 SpaceService 使用原子 memberCount 更新
    - Replace all space.setMemberCount() + spaceMapper.updateById() patterns in join(), leave(), approveMember(), removeMember() with spaceMapper.incrementMemberCount()
- [x] 4. 访问控制与数据泄露修复
  - [x] 4.1 SpaceService 添加 checkMemberAccess() 并在 SpaceController 中调用
    - Add checkMemberAccess(Long spaceId, Long userId) method to SpaceService
    - Check if space visibility is PRIVATE; if so verify user is active member
    - In SpaceController.spacePosts(), get userId and call checkMemberAccess before returning posts
  - [x] 4.2 帖子状态修改时校验帖子归属空间
    - In PostService, add setStatusForSpace(Long postId, Long spaceId, Integer status) method
    - Verify post.getSpaceId() equals spaceId parameter; if mismatch throw NOT_FOUND
    - Update SpaceController.setPostStatus() to call the new method
  - [x] 4.3 PostService.getById() 拆分为内部调用和用户查看
    - Create viewPost(Long id) method that increments viewCount only for logged-in non-admin users using postMapper.incrementViewCount()
    - Remove viewCount increment from getById()
    - Update PostController detail endpoint to call viewPost()
- [x] 5. JSON 注入修复
  - [x] 5.1 MessageService.send() 使用 ObjectMapper 构造 WebSocket JSON
    - Inject ObjectMapper into MessageService
    - Replace string concatenation payload with Map + objectMapper.writeValueAsString()
  - [x] 5.2 NotifyService.create() 使用 ObjectMapper 构造 WebSocket JSON
    - Replace string concatenation payload with Map + jsonMapper.writeValueAsString()
    - jsonMapper already exists in NotifyService as static field
- [x] 6. 输入校验与逻辑错误修复
  - [x] 6.1 QaService.accept() 添加评论归属校验
    - Before setting acceptedCommentId, verify comment exists and comment.getPostId() equals postId
    - If mismatch, throw BAD_REQUEST
  - [x] 6.2 打卡记录添加数据库唯一约束
    - Create SQL migration file: ALTER TABLE checkin_record ADD UNIQUE INDEX uk_challenge_user_date (challenge_id, user_id, checkin_date)
    - In CheckinService.checkin(), wrap recordMapper.insert() with try-catch for DuplicateKeyException, throw ALREADY_CHECKED_IN
  - [x] 6.3 ReportController 使用类型化 DTO
    - Create CreateReportRequest.java in report.dto package with @NotBlank/@NotNull Jakarta validation annotations
    - Modify ReportController.create() to accept @Valid @RequestBody CreateReportRequest instead of Map
- [x] 7. 性能与稳定性修复
  - [x] 7.1 UserService.findSubscribedUserIds() SQL 层过滤
    - Add selectUserIdsByTagSubscription(@Param("tags") List<String> tags) to UserMapper with SQL LIKE filtering
    - Modify findSubscribedUserIds() to use SQL pre-filter then Java exact match
  - [x] 7.2 MessageService.listConversations() 添加分页
    - Add selectLatestMessageIdsPerConversation(@Param("userId") Long userId, @Param("limit") int limit) to MessageMapper
    - Modify listConversations() to use the new query, limit to 50 conversations
  - [x] 7.3 cleanExpiredPins() 移至定时任务
    - Create PinCleanupTask.java in post.task package with @Scheduled(cron = "0 0 2 * * ?")
    - Move cleanExpiredPins() logic from PostService to PinCleanupTask
    - Remove cleanExpiredPins() call from PostService.page()
    - Ensure @EnableScheduling is present on application class
  - [x] 7.4 显式空值检查防止 NPE
    - SpaceService.checkOwnership(): add null check for space variable, throw SPACE_NOT_FOUND
    - CommentService.create(): add null check for parentComment when parentId is provided, throw COMMENT_NOT_FOUND

## Task Dependency Graph

```
1 (授权与租户隔离)
2 (Mapper 原子 SQL) → 3 (Service 原子更新)
2 (Mapper 原子 SQL) → 4.3 (viewPost 使用 incrementViewCount)
4 (访问控制)
5 (JSON 注入)
6 (输入校验)
7 (性能与稳定性)
```

Tasks 1, 2, 4.1, 4.2, 5, 6, 7 have no dependencies and can start immediately.
Task 3 depends on Task 2 (needs atomic SQL methods in mappers).
Task 4.3 depends on Task 2.2 (needs postMapper.incrementViewCount()).

## Notes

- All fixes are internal implementation changes; no API contract changes except Task 6.3 (ReportController DTO replaces Map, but JSON body structure is identical).
- Task 6.2 requires a database migration that should be run before deploying the code change.
- Task 7.3 requires @EnableScheduling on the Spring Boot application class.
