# ContratoIA - Backend

API REST para a plataforma de geração de contratos jurídicos com inteligência artificial, focada no mercado brasileiro.

## Stack

- **Java 21** + **Spring Boot 3.2**
- **Spring Security** + **Keycloak** (OAuth2 Resource Server)
- **PostgreSQL 16** + **Flyway** (migrations)
- **Spring WebClient** → Claude API (geração de documentos)
- **Flying Saucer + OpenPDF** (PDF) + **Apache POI** (DOCX)
- **AWS S3** (armazenamento de arquivos com presigned URLs)
- **AWS SQS FIFO** (geração assíncrona de documentos com DLQ)
- **Stripe** (pagamentos — em integração)

## Pré-requisitos

- **Java 19+** (local) / Java 21 (CI)
- **Maven 3.9+**
- **Docker** e **Docker Compose**
- **Chave de API da Anthropic** (`CLAUDE_API_KEY`)

## Guia completo: rodando localmente

### 1. Subir os containers (PostgreSQL + Keycloak + LocalStack)

```bash
docker-compose up -d
```

Isso sobe:
- **PostgreSQL 16** na porta `5432` (databases: `contratoiadb` + `keycloakdb`)
- **Keycloak 24** na porta `8180` (admin: `admin` / `admin`)
- **LocalStack** na porta `4566` (simula S3 + SQS localmente)

Aguarde o Keycloak ficar pronto (~30s). Verifique com:

```bash
docker-compose ps
```

### 2. Configurar o realm do Keycloak

Acesse `http://localhost:8180` → login como `admin/admin`.

1. **Criar realm**: clique em "Create realm" → nome: `contrato-ia` → Create
2. **Criar client**: no realm `contrato-ia`, vá em Clients → Create client:
   - Client ID: `contrato-ia-frontend`
   - Client authentication: OFF (public client)
   - Valid redirect URIs: `http://localhost:4200/*`
   - Web origins: `http://localhost:4200`
3. **Criar usuário de teste**: vá em Users → Add user:
   - Username: `victor`
   - Email: `victor@email.com`
   - First name: `Victor`
   - Email verified: ON
   - Salve → aba Credentials → Set password (ex: `123456`, Temporary OFF)

### 3. Obter um token JWT para testes

```bash
# Obtenha um access token via Keycloak
curl -s -X POST "http://localhost:8180/realms/contrato-ia/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=contrato-ia-frontend" \
  -d "username=victor" \
  -d "password=123456" | jq -r '.access_token'
```

Guarde o token retornado para usar nas chamadas à API.

### 4. Rodar o backend

#### Via IntelliJ (recomendado)

1. Abra o projeto no IntelliJ
2. Vá em **Run → Edit Configurations** → clique em `+` → **Spring Boot**
3. Main class: `br.com.contratoai.ContratoIaApplication`
4. Active profiles: `dev`
5. Environment variables:
   ```
   CLAUDE_API_KEY=sk-ant-sua-chave-aqui
   ```
6. VM options (necessário se seu Java local for < 21):
   ```
   -Dmaven.compiler.release=19
   ```
7. Clique em **Run** ▶

#### Via terminal

```bash
# Defina a chave da API
export CLAUDE_API_KEY=sk-ant-sua-chave-aqui

# Rode com profile dev (Java 19 local)
mvn spring-boot:run -Dspring-boot.run.profiles=dev -Dmaven.compiler.release=19
```

A API estará disponível em `http://localhost:8080/api`.

### 5. Testar a API

```bash
# Substitua <TOKEN> pelo JWT obtido no passo 3

# Gerar um contrato (retorna 202 — geração assíncrona via SQS)
curl -X POST "http://localhost:8080/api/v1/documents/generate" \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"description": "Preciso de um contrato de prestacao de servicos de TI para freelancer MEI"}'

# Consultar status da geração (polling)
curl "http://localhost:8080/api/v1/documents/<ID>/status" \
  -H "Authorization: Bearer <TOKEN>"

# Listar documentos do usuário
curl "http://localhost:8080/api/v1/documents" \
  -H "Authorization: Bearer <TOKEN>"

# Baixar PDF
curl -o contrato.pdf "http://localhost:8080/api/v1/documents/<ID>/pdf" \
  -H "Authorization: Bearer <TOKEN>"

# Baixar DOCX
curl -o contrato.docx "http://localhost:8080/api/v1/documents/<ID>/docx" \
  -H "Authorization: Bearer <TOKEN>"
```

### 6. Rodar os testes

```bash
# Todos os testes (Java 19 local)
mvn clean test -Dmaven.compiler.release=19

# Um teste específico
mvn test -Dmaven.compiler.release=19 -Dtest=DocumentServiceTest
```

## Arquitetura

### Fluxo de geração assíncrona

```
POST /generate → DocumentService → salva GENERATING → publica SQS FIFO
                                                            ↓
Frontend faz polling ← GET /{id}/status      DocumentGenerationWorker
                                                    ↓ (a cada 2s)
                                              Claude API → DRAFT
                                              PDF/DOCX → S3 upload
                                              mensagem deletada da fila
                                              
                                              Em caso de falha:
                                              status → FAILED
                                              mensagem volta para fila (3 retries)
                                              depois vai para DLQ
```

### Estrutura do projeto

```
src/main/java/br/com/contratoai/
├── config/          # SecurityConfig, WebClientConfig, S3Config, SqsConfig,
│                    # RateLimitConfig, RateLimitFilter, RequestLoggingFilter,
│                    # InputSanitizer
├── controller/      # REST controllers
├── domain/
│   ├── entity/      # User, Document, Template, Signature, AuditLog
│   └── enums/       # Plan, DocumentStatus, SignatureStatus, AuditAction
├── dto/             # Request/Response DTOs + DocumentGenerationMessage
├── exception/       # Typed exceptions + GlobalExceptionHandler
├── repository/      # Spring Data JPA repositories
└── service/         # ClaudeService, DocumentService, UserService,
                     # DocumentQueuePublisher, DocumentGenerationWorker,
                     # PdfGenerationService, DocxGenerationService,
                     # S3StorageService, AuditService,
                     # ContentIntegrityService
```

## Endpoints

| Método | Endpoint                          | Status | Descrição                              |
|--------|-----------------------------------|--------|----------------------------------------|
| POST   | `/api/v1/documents/generate`      | 202    | Enfileira geração de contrato via IA   |
| GET    | `/api/v1/documents`               | 200    | Lista documentos do usuário (paginado) |
| GET    | `/api/v1/documents/{id}`          | 200    | Detalhe de um documento                |
| GET    | `/api/v1/documents/{id}/status`   | 200    | Status da geração (polling)            |
| GET    | `/api/v1/documents/{id}/pdf`      | 200    | Download do PDF                        |
| GET    | `/api/v1/documents/{id}/docx`     | 200    | Download do DOCX                       |
| GET    | `/api/v1/user/data`               | 200    | Exportação de dados pessoais (LGPD)    |
| POST   | `/api/v1/user/consent`            | 200    | Registro de consentimento (LGPD)       |
| DELETE | `/api/v1/user/data`               | 200    | Exclusão/anonimização de dados (LGPD)  |

Todos os endpoints requerem autenticação via Bearer token (Keycloak JWT).

### LGPD Compliance (Lei 13.709/2018)

A plataforma atende aos seguintes direitos do titular de dados:

| Direito (Art. 18) | Implementação |
|--------------------|---------------|
| **Portabilidade** (V) | `GET /api/v1/user/data` — exporta todos os dados em JSON |
| **Eliminação** (VI) | `DELETE /api/v1/user/data` — anonimiza dados pessoais irreversivelmente |
| **Consentimento** (Art. 7) | `POST /api/v1/user/consent` — registra aceite com timestamp |

**Processo de exclusão de dados:**
1. Dados pessoais do User são anonimizados (nome → "USUÁRIO REMOVIDO", email → hash)
2. Conteúdo dos documentos é removido (mantém metadados para auditoria)
3. Arquivos no S3 recebem soft delete (tag `deleted=true`)
4. IPs nos audit logs são anonimizados (→ `0.0.0.0`)
5. PII nos detalhes JSON dos audit logs é removida
6. Registro de auditoria da exclusão é criado (compliance Art. 16, I)

## Variáveis de ambiente

| Variável                        | Default                                        | Descrição                          |
|---------------------------------|------------------------------------------------|------------------------------------|
| `DB_URL`                        | `jdbc:postgresql://localhost:5432/contratoiadb` | URL do PostgreSQL                  |
| `DB_USERNAME`                   | `contrato_user`                                | Usuário do banco                   |
| `DB_PASSWORD`                   | `contrato_pass`                                | Senha do banco                     |
| `KEYCLOAK_ISSUER_URI`           | `http://localhost:8180/realms/contrato-ia`     | URL do realm Keycloak              |
| `CLAUDE_API_KEY`                | —                                              | Chave da API Anthropic             |
| `AWS_S3_ENDPOINT`               | — (LocalStack: `http://localhost:4566`)        | Endpoint S3 (vazio = AWS real)     |
| `AWS_S3_BUCKET`                 | `contrato-ia-docs`                             | Bucket S3                          |
| `AWS_SQS_ENDPOINT`              | — (LocalStack: `http://localhost:4566`)        | Endpoint SQS (vazio = AWS real)    |
| `AWS_SQS_GENERATION_QUEUE_URL`  | —                                              | URL da fila SQS FIFO              |
| `AWS_ACCESS_KEY_ID`             | — (LocalStack: `test`)                         | Access key AWS / LocalStack        |
| `AWS_SECRET_ACCESS_KEY`         | — (LocalStack: `test`)                         | Secret key AWS / LocalStack        |
| `STRIPE_SECRET_KEY`             | —                                              | Chave secreta do Stripe            |
| `STRIPE_WEBHOOK_SECRET`         | —                                              | Secret do webhook Stripe           |
| `RATE_LIMIT_GENERATE`           | `5`                                            | Requests/min por user (generate)   |
| `RATE_LIMIT_EXPORT`             | `20`                                           | Requests/min por user (PDF/DOCX)   |
| `RATE_LIMIT_READ`               | `60`                                           | Requests/min por user (leitura)    |
| `CORS_ORIGINS`                  | `http://localhost:4200`                        | Origens permitidas para CORS       |

## Docker Compose (dev)

O `docker-compose.yml` sobe:
- **PostgreSQL 16** na porta `5432` (user: `contrato_user`, pass: `contrato_pass`)
  - Databases: `contratoiadb` (app) + `keycloakdb` (Keycloak)
- **Keycloak 24** na porta `8180` (admin: `admin`, pass: `admin`)
- **LocalStack** na porta `4566` (simula AWS S3 + SQS)
  - Bucket: `contrato-ia-docs` (com versionamento)
  - Fila SQS FIFO: `contrato-ia-generation.fifo` + DLQ (3 retries)

## Modelo de dados

- **users** — Dados do usuário + plano (FREE/PRO/BUSINESS) + vínculo Keycloak/Stripe
- **templates** — Templates de contratos com system prompts para a IA
- **documents** — Contratos gerados (input do usuário + output da IA + S3 keys)
- **signatures** — Assinaturas digitais vinculadas aos documentos
- **audit_logs** — Registro imutável de auditoria (ações, usuários, recursos, detalhes em JSONB)

### Rate Limiting

Rate limiting per-user baseado no JWT, com 3 tiers:

| Tier | Limite | Endpoints | Protege contra |
|------|--------|-----------|----------------|
| GENERATE | 5/min | `POST /generate` | Abuso da Claude API ($$) |
| EXPORT | 20/min | `GET /{id}/pdf`, `GET /{id}/docx` | Download abusivo (CPU) |
| READ | 60/min | Todos os outros | Scraping, polling excessivo |

Retorna `429 Too Many Requests` com header `Retry-After: 60` quando excedido.
Limites configuráveis via variáveis de ambiente: `RATE_LIMIT_GENERATE`, `RATE_LIMIT_EXPORT`, `RATE_LIMIT_READ`.

### Logging

- **Profile `dev`**: formato legível com cores no console (inclui `requestId` e `userId` no MDC)
- **Profile `prod`** (default): JSON estruturado via Logstash Encoder (pronto para CloudWatch/ELK)
- Cada request HTTP recebe um `requestId` no header `X-Request-Id` para rastreabilidade
- O worker SQS injeta `documentId`, `userId` e `correlationId` no MDC

### Audit Log

Todas as ações relevantes do sistema são registradas na tabela `audit_logs` com:
- Ação (`DOCUMENT_GENERATION_REQUESTED`, `USER_CREATED`, `DOCUMENT_EXPORTED_PDF`, etc.)
- ID do usuário, tipo e ID do recurso afetado
- Detalhes em JSONB (flexível por ação)
- IP do cliente e `requestId` para correlacionar com logs HTTP

### Security Hardening

#### Prompt Injection Defense
- **InputSanitizer**: valida e sanitiza inputs do usuário antes de enviar ao Claude API
  - Detecta padrões de prompt injection (role override, system prompt exfiltration, code execution)
  - Remove caracteres de controle, script tags e event handlers
  - Lança `PromptInjectionException` (HTTP 400) quando detecta tentativa de injeção
- **System prompt reforçado**: instruções de segurança no system prompt do Claude (ignora tentativas de override)

#### Security Headers (via Spring Security)
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `X-XSS-Protection: 0` (desabilitado em favor de CSP — best practice moderna)
- `Strict-Transport-Security: max-age=31536000; includeSubDomains`
- `Referrer-Policy: strict-origin-when-cross-origin`
- `Permissions-Policy: camera=(), microphone=(), geolocation=(), payment=(self)`

#### CORS Hardening
- Headers permitidos restritos: `Authorization`, `Content-Type`, `Accept`, `Origin`, `X-Requested-With`, `X-Request-Id`
- Headers expostos: `X-Request-Id`, `X-Content-Type-Options`, `Retry-After`
- Preflight cache: 1 hora (`maxAge=3600`)
- Credenciais habilitadas apenas para origens configuradas

#### Contract Integrity (SHA-256)
- Hash SHA-256 gerado e salvo quando o conteúdo é criado pelo Claude
- Verificação de integridade no export de PDF/DOCX
- Bloqueia export se o conteúdo foi adulterado após a geração

#### SQS Message Validation
- Validação de campos obrigatórios nas mensagens SQS antes do processamento
- Mensagens malformadas são deletadas da fila e logadas

#### Error Response Sanitization
- Stack traces nunca vazam nas respostas
- Erros da Claude API retornam mensagem genérica (HTTP 503)
- Erros internos retornam "Erro interno do servidor" (HTTP 500)

### Ciclo de vida do documento

`GENERATING → DRAFT/FAILED → FINALIZED → SIGNING → SIGNED → ARCHIVED`

## CI/CD

### Pipelines (GitHub Actions)

| Workflow | Trigger | Ação |
|----------|---------|------|
| `ci.yml` | push/PR em `main` e `develop` | Build, testes, push Docker para GHCR (main) |
| `auto-pr.yml` | push em `develop` | Cria PR develop → main |
| `feature-pr.yml` | push em `feature/**` | Cria PR feature → develop |
| `deploy.yml` | manual (`workflow_dispatch`) | Deploy para staging ou production (AWS ECS) |

### Docker

Multi-stage Dockerfile com:
- **Build stage**: Maven 3.9 + JDK 21 (Alpine)
- **Runtime stage**: JRE 21 Alpine, non-root user, container-aware JVM tuning
- Imagem publicada no GHCR a cada push em `main`

```bash
# Build local
docker build -t contrato-ia-backend .

# Rodar local (requer env vars)
docker run -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/contratoiadb \
  -e DB_USERNAME=contrato_user \
  -e DB_PASSWORD=contrato_pass \
  -e KEYCLOAK_ISSUER_URI=http://host.docker.internal:8180/realms/contrato-ia \
  -e CLAUDE_API_KEY=sk-ant-sua-chave \
  contrato-ia-backend
```

### Deploy para produção

1. Push para `main` → CI builda e pusha a imagem Docker para GHCR
2. Vá em **Actions → Deploy - Backend → Run workflow**
3. Selecione `staging` ou `production` e a tag da imagem
4. O workflow puxa do GHCR, pusha para ECR, e atualiza o ECS service

### Branch Protection

- `main` e `develop` protegidas — merge apenas via PR com aprovação
- Fluxo: `feature/*` → PR → `develop` → PR → `main` → deploy

## Planos

| Plano      | Docs/mês | PDF/DOCX | Assinatura digital | Multi-usuário | API |
|------------|----------|----------|--------------------|---------------|-----|
| FREE       | 3        | Sim      | Nao                | Nao           | Nao |
| PRO        | Ilimitado| Sim      | Sim                | Nao           | Nao |
| BUSINESS   | Ilimitado| Sim      | Sim                | Sim           | Sim |
