# ContratoIA - Backend

API REST para a plataforma de geração de contratos jurídicos com inteligência artificial, focada no mercado brasileiro.

## Stack

- **Java 21** + **Spring Boot 3.2**
- **Spring Security** + **Keycloak** (OAuth2 Resource Server)
- **PostgreSQL 16** + **Flyway** (migrations)
- **Spring WebClient** → Claude API (geração de documentos)
- **iText 8** (PDF) + **Apache POI** (DOCX)
- **Stripe** (pagamentos)
- **Cloudflare R2** (armazenamento de arquivos)

## Pré-requisitos

- Java 21+
- Maven 3.9+
- Docker e Docker Compose

## Rodando localmente

```bash
# 1. Suba PostgreSQL + Keycloak
docker-compose up -d

# 2. Configure as variáveis de ambiente (ou use os defaults para dev)
export CLAUDE_API_KEY=sua-chave-aqui

# 3. Rode a aplicação
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

A API estará disponível em `http://localhost:8080/api`.

## Estrutura do projeto

```
src/main/java/br/com/contratoai/
├── config/          # SecurityConfig, WebClientConfig
├── controller/      # REST controllers
├── domain/
│   ├── entity/      # User, Document, Template, Signature
│   └── enums/       # Plan, DocumentStatus, SignatureStatus
├── dto/             # Request/Response DTOs
├── exception/       # GlobalExceptionHandler
├── repository/      # Spring Data JPA repositories
└── service/         # ClaudeService, DocumentService, UserService
```

## Endpoints principais

| Método | Endpoint                     | Descrição                    |
|--------|------------------------------|------------------------------|
| POST   | `/api/v1/documents/generate` | Gera um novo contrato via IA |
| GET    | `/api/v1/documents`          | Lista documentos do usuário  |
| GET    | `/api/v1/documents/{id}`     | Detalhe de um documento      |

Todos os endpoints requerem autenticação via Bearer token (Keycloak JWT).

## Variáveis de ambiente

| Variável              | Default                                         | Descrição               |
|-----------------------|-------------------------------------------------|-------------------------|
| `DB_URL`              | `jdbc:postgresql://localhost:5432/contratoiadb`  | URL do PostgreSQL       |
| `DB_USERNAME`         | `contrato_user`                                  | Usuário do banco        |
| `DB_PASSWORD`         | `contrato_pass`                                  | Senha do banco          |
| `KEYCLOAK_ISSUER_URI` | `http://localhost:8180/realms/contrato-ia`       | URL do realm Keycloak   |
| `CLAUDE_API_KEY`      | —                                                | Chave da API Anthropic  |
| `STRIPE_SECRET_KEY`   | —                                                | Chave secreta do Stripe |
| `R2_ACCESS_KEY`       | —                                                | Access key Cloudflare R2|
| `R2_SECRET_KEY`       | —                                                | Secret key Cloudflare R2|

## Docker Compose (dev)

O `docker-compose.yml` sobe:
- **PostgreSQL 16** na porta `5432` (user: `contrato_user`, pass: `contrato_pass`, db: `contratoiadb`)
- **Keycloak 24** na porta `8180` (admin: `admin`, pass: `admin`)

## Modelo de dados

- **users** — Dados do usuário + plano + vínculo Keycloak/Stripe
- **templates** — Templates de contratos com system prompts para a IA
- **documents** — Contratos gerados (input do usuário + output da IA)
- **signatures** — Assinaturas digitais vinculadas aos documentos
