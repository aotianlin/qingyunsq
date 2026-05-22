# 数据库迁移目录

本目录存放 CampusForum 的增量数据库迁移脚本。命名采用 `V{版本号}__{说明}.sql` 风格，与 Flyway 命名约定兼容（即使本项目目前未集成 Flyway，便于后续平滑接入）。

## 目录结构

```
db/migrations/
├── V1__bootstrap_default_tenant.sql       # 默认租户初始化（standalone 模式必需）
├── V2__add_comment_updated_at.sql         # 评论增加 updated_at 字段
├── V2__checkin_unique_constraint.sql      # 打卡记录唯一约束
├── V20260522_01__reset_token_hash.sql     # 安全加固：reset_token 改为 SHA-256 哈希
├── V20260522_02__resource_sha256.sql      # 安全加固：resources 新增 file_sha256 字段
└── README.md                              # 本文件
```

## 执行顺序

迁移脚本必须**按版本号升序**手动执行。版本号为 `V20260522_*` 的脚本对应 `security-hardening` spec 的安全加固改造。

### 首次部署（全新数据库）

1. 先执行 `db/schema.sql` 初始化所有表结构（已包含安全加固后的最新字段）
2. 再按顺序执行所有 `V*__*.sql`

### 升级现有部署

仅执行**未应用过**的迁移脚本。建议在 `tenants` 表建立元数据记录，或参考下方的「应用进度跟踪」方案。

## 安全加固迁移（V20260522_*）

> ⚠️ **重要**：以下两个脚本是 [`security-hardening` spec](../../.kiro/specs/security-hardening/) 的部署前置条件，**必须在新版本后端启动前完成**，否则会出现：
> - reset_token 列长度不足导致密码重置插入失败
> - 文件上传时 `file_sha256` 列不存在导致 SQL 错误

### V20260522_01__reset_token_hash.sql

**目的**：将 `users.reset_token` 从明文存储改为 SHA-256 哈希。

**变更**：
- `UPDATE users SET reset_token = NULL, reset_token_expires = NULL` —— 强制现存所有令牌失效
- `ALTER TABLE users MODIFY COLUMN reset_token VARCHAR(64)` —— 列长度从 128 缩为 64

**业务影响**：所有"忘记密码"流程中已发但未使用的链接全部失效，需要用户重新申请。

**执行命令**：
```bash
mysql -u $MYSQL_USER -p $MYSQL_PASSWORD campus_forum < db/migrations/V20260522_01__reset_token_hash.sql
```

### V20260522_02__resource_sha256.sql

**目的**：在 `resources` 表新增 `file_sha256` 字段，准备从 MD5 切换到 SHA-256 指纹。

**变更**：
- `ALTER TABLE resources ADD COLUMN file_sha256 VARCHAR(64)`
- `CREATE INDEX idx_resources_file_sha256 ON resources(file_sha256)`

**业务影响**：
- 新上传文件写入 `file_sha256` 列，去重查询优先匹配该列
- 历史 `file_md5` 列保留不动，未来增量任务可异步回填 `file_sha256`
- 在所有数据回填完成前，去重查询会先查 SHA-256 后回退 MD5

**执行命令**：
```bash
mysql -u $MYSQL_USER -p $MYSQL_PASSWORD campus_forum < db/migrations/V20260522_02__resource_sha256.sql
```

## 应用进度跟踪（可选）

如果需要程序化追踪已应用的迁移，可以建立元数据表：

```sql
CREATE TABLE IF NOT EXISTS schema_migrations (
    version VARCHAR(32) PRIMARY KEY,
    applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

每次手动执行后追加记录：

```sql
INSERT INTO schema_migrations(version) VALUES ('V20260522_01');
INSERT INTO schema_migrations(version) VALUES ('V20260522_02');
```

后续接入 Flyway 时可以将该表交给 Flyway 接管。

## 回滚

本批迁移不提供自动回滚脚本，原因：
- `V20260522_01` 仅清空 reset_token 与缩小列长度，"回滚"会丢失安全收益
- `V20260522_02` 仅新增列与索引，回滚等价于 `DROP COLUMN file_sha256`

如确需回滚，请联系数据库管理员手动操作。
