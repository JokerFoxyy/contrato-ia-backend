# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

Build / run / test (Maven wrapper is not committed — use `mvn` directly):

```bash
# Run app against local Postgres + Keycloak (requires CLAUDE_API_KEY env var)
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Compile + run the full test suite (what CI runs)
mvn clean verify

# Run a single test class or method
mvn test -Dtest=DocumentServiceTest
mvn test -Dtest=DocumentServiceTest#shouldGenerateDocument

# Build an OCI image via Spring Boot's buildpacks (used by CI on main)
mvn spring-boot:build-image -DskipTests

# Bring up Postgres (5432) + Keycloak (8180) for local dev
docker-compose up -d
```

CI (`.github/workflows/ci.yml`) runs on JDK 21 with a Postgres 16 service container — no Keycloak service is started, so any test that hits Keycloak must be self-contained (mock the JWT decoder or use `@WithMockUser`-style helpers from `spring-security-test`).

## Java version gotcha

`pom.xml` sets `<java.version>21</java.version>` matching CI. The local dev machine has JDK 19 — to run tests locally, override with `-Dmaven.compiler.release=19`. CI uses JDK 21 and compiles natively.

## Architecture

Spring Boot 3.2 monolith. Single Maven module, package root `br.com.contratoai`, entry point `ContratoIaApplication`.

### Request flow for the core feature (AI document generation)

`POST /api/v1/documents/generate` → `DocumentController` → `DocumentService.generate(...)`:

1. `UserService.getOrCreateUser(jwt)` — **lazy provisioning**: on first request from a Keycloak user, a row is inserted into `users` keyed by `keycloak_id = jwt.getSubject()`. There is no separate signup endpoint; the JWT is the source of truth for identity.
2. Plan-gating: `documentRepository.countDocumentsSince(userId, firstOfMonth)` is checked against `FREE_PLAN_MONTHLY_LIMIT = 3` (hard-coded in `UserService`). PRO/BUSINESS skip the check.
3. A `Document` row is persisted with `status = GENERATING`.
4. A `DocumentGenerationMessage` is published to SQS FIFO queue via `DocumentQueuePublisher`. The endpoint returns **HTTP 202** immediately.
5. `DocumentGenerationWorker` polls the SQS queue (`@Scheduled(fixedDelay=2000)`, long polling 5s, batch of 5, visibility timeout 120s):
   - Calls `ClaudeService.generateDocument(description)` synchronously.
   - On success: status → `DRAFT`, generates PDF/DOCX via Flying Saucer + POI, uploads to S3, deletes SQS message.
   - On failure: status → `FAILED`, message stays in queue (SQS retries up to 3x, then DLQ).
6. Frontend polls `GET /api/v1/documents/{id}/status` until status is no longer `GENERATING`.

`DocumentStatus` lifecycle: `GENERATING → DRAFT/FAILED → FINALIZED → SIGNING → SIGNED → ARCHIVED`.

### Two separate API surfaces share the JWT

- `SecurityConfig` enforces stateless JWT-bearer auth via Spring's OAuth2 Resource Server, validating against `KEYCLOAK_ISSUER_URI`.
- The `/api` prefix comes from `server.servlet.context-path` in `application.yml` — controllers use `@RequestMapping("/v1/...")`, so the public path is `/api/v1/...`.
- Roles are extracted from the `realm_access.roles` claim with prefix `ROLE_` (custom `JwtAuthenticationConverter` in `SecurityConfig`).
- Public paths (no auth): `/actuator/health`, `/v3/api-docs/**`, `/swagger-ui/**`. Everything else requires a Bearer token.

### Persistence

- **Flyway owns the schema.** `spring.jpa.hibernate.ddl-auto=validate`, so JPA never alters the DB. New columns / tables go in `src/main/resources/db/migration/V{n}__*.sql` — never edit a previously released `V*` file.
- Migrations so far: V1 users, V2 templates (+ seed data for 5 default contract templates), V3 documents, V4 signatures, V5 drops `documents_this_month` column, V6 adds `pdf_s3_key`/`docx_s3_key` to documents, V7 creates `audit_logs` table (JSONB details, indexed by user/action/resource/date).
- The monthly document count is computed on demand via `DocumentRepository.countDocumentsSince`.
- The `templates` table is seeded with system prompts that are intended to *augment* the persona prompt in `ClaudeService`. Today `DocumentService.generate` looks up the template but does **not** thread `template.systemPrompt` into the Claude call — the persona prompt is always used. If you wire templates up, that's the join point.

### External integrations

- **Anthropic** (`ClaudeService` + `WebClientConfig`): direct REST via WebClient with `x-api-key` header and `anthropic-version: 2023-06-01`. Model and max-tokens come from `claude.api.*` config. The 10MB `maxInMemorySize` exists because generated contracts can be long. WebClient configured with 10s connect timeout, 60s response timeout, and retry with backoff (2 retries, 2s interval) for 5xx errors.
- **AWS S3** (`S3Config` + `S3StorageService`): dual-mode — LocalStack (dev, endpoint override + static creds) vs IAM role (prod, DefaultCredentialsProvider). Documents stored at `documents/{userId}/{documentId}/{timestamp}.{ext}` with SSE-S3 encryption. Soft delete via tagging for audit. Presigned URLs with configurable expiry.
- **AWS SQS FIFO** (`SqsConfig` + `DocumentQueuePublisher` + `DocumentGenerationWorker`): async document generation. Main queue `contrato-ia-generation.fifo` with DLQ (maxReceiveCount=3). MessageGroupId = userId for ordered processing per user.
- **Stripe**: config keys are wired (`stripe.*` in `application.yml`) but **no service classes implement them yet** — `Plan.PRO/BUSINESS` paths are placeholders.

### Error handling

Typed exception hierarchy in `br.com.contratoai.exception`:

`GlobalExceptionHandler` maps:
- `MethodArgumentNotValidException` → 400 with field-keyed errors.
- `DocumentNotFoundException` → 404
- `UserNotFoundException` → 404
- `PlanLimitExceededException` → 402 Payment Required
- `ClaudeApiException` → 503 Service Unavailable
- Any other `RuntimeException` → 500 with generic message.

## Local dev defaults

`application.yml` provides dev defaults for everything except `CLAUDE_API_KEY`, `STRIPE_*`, and `R2_*`. The `dev` profile (`application-dev.yml`) only raises log levels — there is no separate dev datasource. `docker-compose.yml` + `init-db.sh` create both `contratoiadb` and `keycloakdb` in the same Postgres instance.
