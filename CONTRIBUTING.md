# Contributing to CampusForum

感谢你对 CampusForum 的关注！我们欢迎任何形式的贡献。

## 行为准则

本项目遵循 [贡献者公约](CODE_OF_CONDUCT.md)。参与即表示你同意遵守其条款。

## 如何贡献

### 报告 Bug

1. 在 Issues 中使用 "Bug Report" 模板
2. 描述复现步骤、期望行为和实际行为
3. 提供环境信息（OS、浏览器、版本号）

### 提交功能请求

1. 在 Issues 中使用 "Feature Request" 模板
2. 描述使用场景和期望效果
3. 说明是否愿意参与实现

### 代码贡献流程

1. Fork 本仓库
2. 从 `develop` 分支创建 `feature/xxx` 或 `fix/xxx` 分支
3. 编写代码并添加测试
4. 确保 CI 全部通过（lint + test）
5. 提交 PR 到 `develop` 分支
6. 等待 Code Review

## 开发环境搭建

```bash
# 后端
cd backend
./mvnw spring-boot:run

# 前端
cd frontend
npm install
npm run dev
```

## 提交规范

使用 Conventional Commits:

- `feat:` 新功能
- `fix:` 修复
- `docs:` 文档
- `refactor:` 重构
- `test:` 测试
- `chore:` 构建/工具

## 代码规范

- Java: Google Java Style + 阿里巴巴 Java 开发手册
- TypeScript: ESLint + Prettier, strict mode
- Vue: Composition API + `<script setup>`

## 多租户部署

CampusForum 支持两种租户模式：**standalone**（单校部署）和 **multi**（SaaS 多校部署）。

### Standalone 模式（默认）

默认无需任何额外配置。所有数据使用 `tenant_id=1`，系统行为与单租户应用一致。

`application.yml` 中的默认配置：

```yaml
tenant:
  mode: standalone
  standalone-tenant-id: 1
```

此模式下：
- 客户端发送的 `X-Tenant-Id` 头和子域名信息会被忽略
- 所有 SQL 查询自动附加 `tenant_id = 1` 条件
- 无需配置 `root-domain`

### 切换到 Multi 模式

1. 修改 `application.yml`：

```yaml
tenant:
  mode: multi
  standalone-tenant-id: 1
  root-domain: campusforum.com       # 你的根域名
  allow-header-fallback: true        # 是否允许 X-Tenant-Id 头作为回退
  cache:
    max-size: 1024
    ttl: 60s
```

2. 确保 `tenants` 表中至少有一条 `status=1` 的活跃租户记录（见下方 Bootstrap Migration）。

3. 配置子域名 DNS（见下方说明）。

4. 重启应用。启动时 `TenantStartupValidator` 会校验：
   - `tenants` 表至少有一条活跃记录
   - `root-domain` 配置非空

### 子域名 DNS 配置示例

Multi 模式通过子域名识别租户。例如 `fudan.campusforum.com` 会被解析为 code=`fudan` 的租户。

DNS 配置（泛域名解析）：

```
# A 记录 - 将所有子域名指向应用服务器
*.campusforum.com    A    <你的服务器 IP>

# 或使用 CNAME（如果前面有 CDN/负载均衡）
*.campusforum.com    CNAME    lb.campusforum.com
```

本地开发时可在 `/etc/hosts`（Linux/macOS）或 `C:\Windows\System32\drivers\etc\hosts`（Windows）中添加：

```
127.0.0.1    fudan.campusforum.local
127.0.0.1    pku.campusforum.local
```

并将 `application-dev.yml` 中 `root-domain` 设为 `campusforum.local`。

### Bootstrap Migration 注意事项

首次部署或从旧版本升级时，必须确保 `tenants` 表中存在默认租户记录。项目提供了自动迁移脚本：

```
db/migrations/V1__bootstrap_default_tenant.sql
```

该脚本使用 `INSERT ... ON DUPLICATE KEY UPDATE` 语义，保证 `(id=1, code='default', status=1)` 记录存在，不会破坏已有数据。

**注意事项**：
- Standalone 模式要求 `tenants` 表中存在 `id=standalone-tenant-id`（默认 1）且 `status=1` 的记录，否则应用启动失败
- Multi 模式要求至少一条 `status=1` 的记录
- `db/schema.sql` 末尾已包含相同的 INSERT 语句，新部署时会自动初始化

## 集成测试与 Testcontainers

项目集成测试使用 [Testcontainers](https://www.testcontainers.org/) 启动 MySQL 容器。默认情况下 Testcontainers 连接本机 Docker daemon。

### 在 VM 中开发时配置 Docker daemon

如果你在虚拟机（如 WSL2、Multipass、Vagrant）中运行 Docker，而 IDE 在宿主机上，需要配置 `~/.testcontainers.properties` 指向 VM 中的 Docker daemon：

1. 在用户主目录下创建或编辑 `~/.testcontainers.properties`：

```properties
# 指向 VM 中的 Docker daemon（替换为你的 VM IP）
docker.host=tcp://192.168.56.10:2375

# 如果使用 TLS
# docker.host=tcp://192.168.56.10:2376
# docker.tls.verify=1
# docker.cert.path=/path/to/certs
```

2. 确保 VM 中的 Docker daemon 监听 TCP 端口。编辑 VM 中的 `/etc/docker/daemon.json`：

```json
{
  "hosts": ["unix:///var/run/docker.sock", "tcp://0.0.0.0:2375"]
}
```

3. 重启 VM 中的 Docker 服务：`sudo systemctl restart docker`

**安全提示**：`tcp://0.0.0.0:2375` 无认证，仅适用于本地开发环境。生产环境或共享网络中请启用 TLS。

仓库根目录提供了 `.testcontainers.properties.example` 作为参考模板。

### GitHub Actions CI

CI 环境（GitHub Actions runner）自带 Docker，无需额外配置 `~/.testcontainers.properties`。Testcontainers 会自动检测并使用本地 Docker daemon。
