# Implementation Plan: Platform Enhancements

## Overview

本实施计划涵盖 CampusForum 平台 12 项功能增强的开发任务，按依赖关系分为 4 个实施波次。

## Task Dependency Graph

```json
{
  "waves": [
    {
      "name": "Wave 1: 基础设施与独立后端功能",
      "tasks": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11]
    },
    {
      "name": "Wave 2: 前端国际化",
      "tasks": [12]
    },
    {
      "name": "Wave 3: 前端集成联调",
      "tasks": [13],
      "dependsOn": [2, 3, 4, 5, 6, 8, 12]
    },
    {
      "name": "Wave 4: 集成测试与验收",
      "tasks": [14],
      "dependsOn": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13]
    }
  ]
}
```

## Tasks

- [x] 1. 实现 SMTP 邮件服务：在 pom.xml 添加 spring-boot-starter-mail 依赖；创建 EmailService 接口和 SmtpEmailService 实现类；创建 HTML 邮件模板；在 application.yml 添加 SMTP 配置；修改 UserService.forgotPassword() 替换 mock 为真实邮件发送；实现重置令牌频率限制（每邮箱 15 分钟最多 5 次）；实现旧令牌失效逻辑；编写单元测试
- [x] 2. 实现帖子编辑功能：创建 UpdatePostRequest DTO；在 PostController 添加 PUT /api/v1/posts/{id} 端点；在 PostService 实现 updatePost 方法（验证作者身份、敏感词过滤、risk_level>=2 自动隐藏）；发布 PostUpdatedEvent；编写单元测试；在前端 api/posts.ts 添加 updatePost 方法
- [x] 3. 实现收藏列表功能：创建 FavoriteVO DTO；在 UserController 添加 GET /api/v1/users/me/favorites 端点；实现带 LEFT JOIN 的收藏查询（排除已删除目标）；支持 target_type 过滤和游标分页；编写单元测试；在前端添加收藏列表 API 和页面
- [x] 4. 实现通知批量已读：创建 BatchReadRequest DTO（@Size(max=100)）；在 NotifyController 添加 PUT /api/v1/notifications/batch-read 端点；实现批量更新逻辑（验证归属、跳过无效 ID、返回标记数量）；编写单元测试；在前端添加批量已读 API 和"全部已读"按钮
- [x] 5. 实现私信全部已读：在 MessageController 添加 PUT /api/v1/messages/read-all 端点；实现批量更新（UPDATE messages SET is_read=1 WHERE receiver_id=? AND is_read=0）；返回更新数量；编写单元测试；在前端添加 API 和按钮
- [x] 6. 实现评论编辑功能：在 comments 表添加 updated_at 字段；创建 UpdateCommentRequest DTO（@Size(min=1,max=5000)）；在 CommentController 添加 PUT /api/v1/comments/{id} 端点；实现 updateComment 方法（验证作者、敏感词过滤、保留 created_at）；编写单元测试；在前端添加评论编辑 API 和内联编辑 UI
- [x] 7. 实现通用 API 限流：创建 ratelimit 包；实现 RateLimitProperties 配置类；实现 RedisRateLimiter（Sorted Set 滑动窗口）；实现 RateLimitInterceptor；在 WebMvcConfig 注册拦截器并配置排除路径；实现 Redis 不可用时 fail-open 降级；在 application.yml 添加配置；编写单元测试和并发集成测试
- [x] 8. 实现文件在线预览：创建 PreviewProperties 配置类；在 ResourceController 添加 GET /api/v1/resources/{id}/preview 端点；实现 PDF/图片流式返回（Content-Disposition: inline）；实现 Office 文档重定向到预览服务；实现文件类型判断（415）和大小检查（413）；编写单元测试；在前端 ResourceDetail 添加预览按钮
- [x] 9. 实现管理后台数据导出：在 pom.xml 添加 poi-ooxml 依赖；创建 export 包；实现 CsvExporter 和 XlsxExporter（流式写入）；实现 ExportService（分批查询每批 1000 条）；创建 ExportController（POST /api/v1/admin/export/{dataType}）；实现 4 种数据类型字段映射；添加权限控制；编写单元测试；在前端管理后台添加导出按钮
- [x] 10. 实现 WebSocket 集群广播：创建 WebSocketBroadcaster 接口；实现 RedisWebSocketBroadcaster（Redis publish）；实现 BroadcastMessage DTO；实现 Redis MessageListener 订阅频道并投递本地 Session；实现 Redis 不可用降级；修改现有通知推送代码改用 Broadcaster；编写单元测试和集成测试
- [x] 11. 实现搜索索引同步：在 pom.xml 添加 spring-retry 依赖；创建 PostIndexEvent/ResourceIndexEvent 事件类；在 PostService 和 ResourceService 中发布事件；创建 SearchSyncListener（@EventListener + @Async）；实现 MeilisearchSyncService（@Retryable 3 次指数退避）；创建 SearchReindexController（POST /api/v1/admin/search/reindex）；实现全量重建索引；编写单元测试和集成测试
- [x] 12. 实现前端国际化（i18n）：安装 vue-i18n；创建 locales 目录和 zh-CN.json/en-US.json 语言文件；创建 i18n 实例（localStorage 持久化 + 浏览器语言检测）；在 main.ts 注册插件；创建 LanguageSwitcher 组件集成到头部；逐步替换各页面硬编码文本为 $t() 调用；编写单元测试
- [x] 13. 前端功能集成与联调：在路由中添加收藏列表页面；统一前端 API 错误处理（429/415 提示）；集成帖子编辑和评论编辑的 UI 交互；验证所有新增 API 的前后端联调
- [x] 14. 集成测试与验收：编写邮件发送端到端测试（GreenMail）；编写帖子编辑+敏感词集成测试；编写限流并发集成测试；编写搜索同步集成测试；编写 WebSocket 广播集成测试；编写批量已读幂等性测试；编写数据导出流式测试；全量回归测试

## Notes

- Wave 1 中的任务 1-11 相互独立，可并行开发
- Task 12（i18n）可与后端任务并行，但前端集成（Task 13）需等待 i18n 基础设施就绪
- Task 14 为最终验收，依赖所有前置任务完成
- 建议优先实现高优先级功能（Task 1-7），再处理中优先级（Task 8-9）和扩展性改进（Task 10-12）
