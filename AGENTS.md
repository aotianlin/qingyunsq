# Repository Guidelines

## Project Structure & Module Organization

CampusForum is a modular full-stack application. Backend code lives in `backend/src/main/java/com/campusforum`, grouped by domain packages such as `user`, `post`, `space`, `tenant`, `admin`, `ai`, and `infra`. Backend tests are in `backend/src/test/java`. Frontend code lives in `frontend/src`, with pages in `frontend/src/pages`, API clients in `frontend/src/api`, stores in `frontend/src/stores`, shared types in `frontend/src/types`, and utilities in `frontend/src/utils`. Database schema is in `db/schema.sql`; Docker/Nginx deployment assets are under `deploy/`.

## Build, Test, and Development Commands

Backend commands run from `backend/`:

```bash
JAVA_HOME=/home/morose/.local/jdk-21.0.2 /mnt/d/develop/apache-maven-3.9.4/bin/mvn test
JAVA_HOME=/home/morose/.local/jdk-21.0.2 /mnt/d/develop/apache-maven-3.9.4/bin/mvn spring-boot:run
```

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

## Testing Guidelines

Backend tests use Spring Boot Test and JUnit under `backend/src/test/java`; name test classes `XxxTest`. Frontend tests use Vitest and should be named `*.test.ts` or `*.spec.ts` near the code under test. Add tests for changed behavior, especially utilities, services, permission checks, and tenant isolation.

## Commit & Pull Request Guidelines

Use Conventional Commits, consistent with project history: `feat: ...`, `fix: ...`, `test: ...`, `docs: ...`, `refactor: ...`. Keep commits focused and include generated or test changes only when they are part of the same change. Pull requests should include a short summary, test results, linked issues when applicable, and screenshots for visible frontend changes.

## Security & Configuration Tips

Do not commit `.env`, credentials, uploads, `node_modules`, `dist`, or Maven `target/`. Local development uses `application-dev.yml`; production settings should be supplied through environment variables or deployment configuration.
