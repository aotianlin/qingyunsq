# Repository Guidelines

## Project Structure & Module Organization

CampusForum is a modular full-stack application. Backend code lives in `backend/src/main/java/com/campusforum`, grouped by domain packages such as `user`, `post`, `space`, `tenant`, `admin`, `ai`, and `infra`. Backend tests are in `backend/src/test/java`. Frontend code lives in `frontend/src`, with pages in `frontend/src/pages`, API clients in `frontend/src/api`, stores in `frontend/src/stores`, shared types in `frontend/src/types`, and utilities in `frontend/src/utils`. Database schema is in `db/schema.sql`; Docker/Nginx deployment assets are under `deploy/`.

## Build, Test, and Development Commands

Backend commands run from `backend/`:

```bash
JAVA_HOME=/home/morose/.local/jdk-17 /mnt/d/develop/apache-maven-3.9.4/bin/mvn test
JAVA_HOME=/home/morose/.local/jdk-17 /mnt/d/develop/apache-maven-3.9.4/bin/mvn spring-boot:run
```

本地开发默认复用已经存在并挂好历史测试数据的数据容器：`my-mysql` 与 `my-redis`。它们通过 Docker 端口映射暴露为宿主机 `localhost:3306` 和 `localhost:6379`，所以 `application-dev.yml` 里的 `localhost` 表示 Docker 映射入口，不是宿主机裸跑 MySQL/Redis。启动服务前必须先执行 `docker ps` 检查现有容器和端口映射；不要为后端启动新建数据库/缓存容器，不要把 dev 配置改到空库容器，也不要运行 `docker compose up` 去补齐 `deploy-mysql` / `deploy-redis` 这类空库服务。后端只用宿主机/虚拟机 JVM 跑，基础设施继续复用已有 Docker 容器，避免为了调试反复构建后端镜像。

Frontend commands run from `frontend/`:

```bash
npm run dev -- --host 127.0.0.1
npm run test
npm run build
npm run lint
```

`npm run build` performs TypeScript checking with `vue-tsc` before Vite builds `dist/`.

## Coding Style & Naming Conventions

Use Java 17+ conventions for backend code: `PascalCase` classes, `camelCase` methods/fields, and package names grouped by feature. Keep controllers thin and put business rules in services. Frontend files use Vue 3 Composition API and TypeScript; Vue page/component files use `PascalCase.vue`, composables use `useXxx.ts`, and API modules use lower-case domain names such as `posts.ts`. Run Prettier through `npm run format` for frontend formatting.

核心代码和复杂逻辑必须写详细注释，所有代码注释必须使用中文。

## Testing Guidelines

Backend tests use Spring Boot Test and JUnit under `backend/src/test/java`; name test classes `XxxTest`. Frontend tests use Vitest and should be named `*.test.ts` or `*.spec.ts` near the code under test. Add tests for changed behavior, especially utilities, services, permission checks, and tenant isolation.

## Commit & Pull Request Guidelines

Use Conventional Commits, consistent with project history: `feat: ...`, `fix: ...`, `test: ...`, `docs: ...`, `refactor: ...`. Keep commits focused and include generated or test changes only when they are part of the same change. Pull requests should include a short summary, test results, linked issues when applicable, and screenshots for visible frontend changes.

## Security & Configuration Tips

Do not commit `.env`, credentials, uploads, `node_modules`, `dist`, or Maven `target/`. Local development uses `application-dev.yml`; production settings should be supplied through environment variables or deployment configuration.
