# Repository Guidelines

## Project Structure & Module Organization
`src/main/java/com/lin/linagent` contains the Spring Boot backend, organized by feature (`controller`, `service`, `agent`, `tools`, `config`, `rag`). Shared configuration and runtime assets live in `src/main/resources`, including `application.yaml`, MyBatis mappers, prompt templates, and static text corpora. Backend tests sit under `src/test/java`.

`lin-agent-fronted/` is the Vue 3 + Vite client. Keep page components in `src/pages`, reusable UI in `src/components`, API helpers in `src/services`, and router setup in `src/router`. `lin-image-search-mcp/` is a separate Spring Boot MCP service. Database bootstrap SQL lives in `sql/create_table.sql`; build output belongs in `target/`.

## Build, Test, and Development Commands
`.\mvnw.cmd spring-boot:run` starts the backend on `http://localhost:8080/api`.

`.\mvnw.cmd test` runs backend tests. Use `-Dtest=ClassName` for a focused run.

`.\mvnw.cmd -q -DskipTests compile` is the quickest compile check before committing.

`cd lin-agent-fronted; npm install` installs frontend dependencies.

`cd lin-agent-fronted; npm run dev` starts the Vite dev server with `/api` proxied to port `8080`.

`cd lin-agent-fronted; npm run build` verifies the frontend production bundle.

`cd lin-image-search-mcp; .\mvnw.cmd test` validates the MCP subproject.

## Coding Style & Naming Conventions
Use UTF-8 for every file. Follow existing Java conventions: 4-space indentation, `PascalCase` classes, `camelCase` methods/fields, and package names under `com.lin.linagent`. Vue single-file components also use `PascalCase` filenames such as `AssistantChat.vue`; service modules stay lowercase, such as `auth.js`. Keep comments brief and in Chinese when extra context is needed.

## Testing Guidelines
The repository uses JUnit 5 with `@SpringBootTest`. Many tests are integration-style and rely on local MySQL, PostgreSQL/pgVector, Elasticsearch, or DashScope credentials from `application.yaml`, so prefer targeted runs when those services are unavailable. Name new tests `*Test.java` and place them beside the feature package they exercise.

## Commit & Pull Request Guidelines
Recent history favors short Chinese summaries describing completed work, for example `完成获取用户信息、修改用户信息的前后端代码编写`. Keep one logical change per commit. Pull requests should state affected modules, required config changes, related issues, and include screenshots or request/response examples for UI or API behavior changes.

## Security & Configuration Tips
Do not commit real API keys, database passwords, or environment-specific endpoints. Move local secrets out of `src/main/resources/application.yaml` before sharing branches, and document any new required configuration in the PR description.
