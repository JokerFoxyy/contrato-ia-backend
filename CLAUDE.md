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

`pom.xml` sets `<java.version>19</java.version>` but the README and CI both target **JDK 21**. Build with JDK 21; if you bump the property, keep all three in sync.

## Architecture

Spring Boot 3.2 monolith. Single Maven module, package root `br.com.contratoai`, entry point `ContratoIaApplication`.

### Request flow for the core feature (AI document generation)

`POST /api/v1/documents/generate` → `DocumentController` → `DocumentService.generate(...)`:

1. `UserService.getOrCreateUser(jwt)` — **lazy provisioning**: on first request from a Keycloak user, a row is inserted into `users` keyed by `keycloak_id = jwt.getSubject()`. There is no separate signup endpoint; the JWT is the source of truth for identity.
2. Plan-gating: `documentRepository.countDocumentsSince(userId, firstOfMonth)` is checked against `FREE_PLAN_MONTHLY_LIMIT = 3` (hard-coded in `UserService`). PRO/BUSINESS skip the check.
3. A `Document` row is persisted with `status = GENERATING` *before* calling the LLM.
4. `ClaudeService.generateDocument(description)` calls Anthropic's `/v1/messages` synchronously (`.block()` on the WebClient) using the system prompt hard-coded inside `ClaudeService` (Brazilian-jurist persona).
5. Status flips to `DRAFT` on success. Note: `DocumentStatus.FAILED` does **not** exist — failures bubble as `RuntimeException` and the row stays at `DRAFT` (this is a known behavioral quirk; see `DocumentService.generate`).

`DocumentStatus` lifecycle: `GENERATING → DRAFT → FINALIZED → SIGNING → SIGNED → ARCHIVED`.

### Two separate API surfaces share the JWT

- `SecurityConfig` enforces stateless JWT-bearer auth via Spring's OAuth2 Resource Server, validating against `KEYCLOAK_ISSUER_URI`.
- The `/api` prefix comes from `server.servlet.context-path` in `application.yml` — controllers use `@RequestMapping("/v1/...")`, so the public path is `/api/v1/...`.
- Roles are extracted from the `realm_access.roles` claim with prefix `ROLE_` (custom `JwtAuthenticationConverter` in `SecurityConfig`).
- Public paths (no auth): `/actuator/health`, `/v3/api-docs/**`, `/swagger-ui/**`. Everything else requires a Bearer token.

### Persistence

- **Flyway owns the schema.** `spring.jpa.hibernate.ddl-auto=validate`, so JPA never alters the DB. New columns / tables go in `src/main/resources/db/migration/V{n}__*.sql` — never edit a previously released `V*` file.
- Migrations so far: V1 users, V2 templates (+ seed data for 5 default contract templates), V3 documents, V4 signatures.
- `users.documents_this_month` exists in the schema but is unused — the monthly count is computed on demand via `DocumentRepository.countDocumentsSince`.
- The `templates` table is seeded with system prompts that are intended to *augment* the persona prompt in `ClaudeService`. Today `DocumentService.generate` looks up the template but does **not** thread `template.systemPrompt` into the Claude call — the persona prompt is always used. If you wire templates up, that's the join point.

### External integrations

- **Anthropic** (`ClaudeService` + `WebClientConfig`): direct REST via WebClient with `x-api-key` header and `anthropic-version: 2023-06-01`. Model and max-tokens come from `claude.api.*` config. The 10MB `maxInMemorySize` exists because generated contracts can be long.
- **Stripe**, **Cloudflare R2 (via AWS S3 SDK)**: dependencies and config keys are wired (`stripe.*`, `r2.*` in `application.yml`) but **no service classes implement them yet** — `pdfUrl`/`docxUrl` columns and `Plan.PRO/BUSINESS` paths are placeholders waiting for those integrations.

### Error handling

`GlobalExceptionHandler` maps:
- `MethodArgumentNotValidException` → 400 with field-keyed errors.
- Any `RuntimeException` → 500, **except** when `ex.getMessage().contains("não encontrado")` (Portuguese for "not found"), which becomes 404. Throw `RuntimeException("... não encontrado")` for not-found cases until a proper exception hierarchy lands.

## Local dev defaults

`application.yml` provides dev defaults for everything except `CLAUDE_API_KEY`, `STRIPE_*`, and `R2_*`. The `dev` profile (`application-dev.yml`) only raises log levels — there is no separate dev datasource. `docker-compose.yml` + `init-db.sh` create both `contratoiadb` and `keycloakdb` in the same Postgres instance.
