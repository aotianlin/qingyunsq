# CampusForum 后端业务逻辑分析及优化方案

## 一、 整体架构与基础功能评估

经过对 `backend` 代码的深入分析，目前项目的后端业务逻辑**主体框架是完整且健壮的**。以下模块实现了较为完善的闭环：
1. **用户体系**：注册/登录完整，包含了密码哈希、Sa-Token 鉴权、学号自动映射学院专业、重置密码等机制。
2. **多租户架构**：利用了 MyBatis Plus 的 `TenantLineInnerInterceptor`，对绝大多数业务表进行了底层 SQL 拦截与自动隔离，架构设计非常优雅。
3. **发帖与互动**：`PostService` 处理了发帖、引用（Quote）、问答类型、置顶、加精以及 Meilisearch 搜索引擎同步；处理了@提及和标签订阅的通知，考虑周全。
4. **空间模块**：实现了不同可见度（Public/Review）、人员加入上限检测以及所有权和角色的权限控制。

总体而言，作为校园级部署的基础设施已经达标，但在**部分边界场景**和**多租户细节**上存在逻辑漏洞。

---

## 二、 发现的逻辑漏洞与不足（需优化的重点）

### 1. 租户数据隔离泄露（严重）
* **问题点**：在 `MyBatisPlusConfig.java` 中，`sensitive_words` 表被加入了 `TENANT_IGNORE_TABLES`（意味着 MyBatis Plus 不会自动为其拼接 `tenant_id` 查询条件）。但在 `SensitiveWordService.java` 中，`listAll()` 方法直接使用了 `mapper.selectList(null)`。
* **影响**：这会导致 A 学校（租户）添加的自定义敏感词，会直接作用于 B 学校（租户）的发帖审核中。这是一个典型的多租户数据泄露问题。
* **优化方案**：在 `SensitiveWordService` 中手动加入当前租户的条件过滤。
  ```java
  public List<SensitiveWord> listAll() {
      Long tid = TenantContext.getTenantId();
      return mapper.selectList(new LambdaQueryWrapper<SensitiveWord>()
              .eq(SensitiveWord::getTenantId, tid != null ? tid : 1L));
  }
  ```

### 2. 积分与成就解析逻辑脆弱（中度）
* **问题点**：`AchievementService.java` 在 `countStat` 和 `parseThreshold` 方法中，使用了 `indexOf`、`substring` 来手动截取解析 JSON 规则字符串。
* **影响**：如果 JSON 规则格式稍有变化（例如多了一个空格或者字段顺序变化），字符串截取就会失败，导致成就无法正确触发。而且其中使用了字符串拼接 SQL：`inSql("target_id", "SELECT id FROM posts WHERE author_id = " + userId)`，虽然此处的 `userId` 是 `Long` 类型不会导致 SQL 注入，但写法不够规范。
* **优化方案**：使用项目中已有的 `ObjectMapper` 将 Rule 字符串反序列化为 Java 对象进行判断；尽量使用 Mybatis Plus 的常规 Wrapper 方法替代 `inSql` 字符串拼接。

### 3. 数据转化与流转丢失（轻度）
* **问题点**：在 `CheckinService.java` 的 `shareToSquare` 方法中，将打卡记录分享到广场成为帖子时，只拼接了 `Content` 文本，而**丢弃了**打卡记录中的图片 `imageUrls`。
* **影响**：用户带图片的打卡分享到广场后，帖子中看不到图片。
* **优化方案**：将 `CheckinRecord` 的 `imageUrls` 解析并转化为 `Post` 的 `attachments` 字段进行保存。

### 4. AI 密钥明文存储（安全隐患）
* **问题点**：在 `TenantService.java` 中，租户的 `aiConfig`（包含 `apiKey`）直接被序列化为 JSON 明文存储在数据库中。
* **影响**：如果数据库被拖库或运维人员越权查看，租户配置的 OpenAI 密钥将直接泄露。
* **优化方案**：在存储 `apiKey` 之前使用对称加密（如 AES）进行加密，在 `getAiConfig` 返回或 `OpenAiCompatService` 调用时再解密。

---

## 三、 优化执行建议

如果你希望修复上述问题，我们可以分步进行：
1. **第一步**：修复最核心的 `SensitiveWordService` 租户隔离问题，确保各高校敏感词互不干扰。
2. **第二步**：修复 `CheckinService` 分享到广场的图片丢失问题，提升用户体验。
3. **第三步**：重构 `AchievementService` 的 JSON 解析逻辑，提高代码健壮性。

你可以告诉我希望先从哪一步开始处理，或者我直接为你生成修复代码！
