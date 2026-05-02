# Vault Architecture

Vault is a reusable Java security platform for Spring Boot services. It has two runtime parts:

- `vault-server`: the central identity and authorization service. It owns users, API keys, JWT issuing/validation, audit logs, Redis-backed token revocation, and rate limiting.
- `vault-sdk`: a Maven library added to any Spring Boot service. It auto-registers a servlet filter, calls `vault-server` for validation, and exposes the validated caller identity to the host application.

`demo-service` is the sample consumer that proves the integration model.

## System Architecture

```mermaid
flowchart LR
    actor[Client / API Consumer]

    subgraph consumer["Any Spring Boot Service using vault-sdk"]
        appController["Business Controllers"]
        appService["Business Services"]
        sdkFilter["VaultAuthFilter<br/>OncePerRequestFilter"]
        sdkClient["VaultClient<br/>RestClient"]
        sdkContext["VaultSecurityContext<br/>ThreadLocal current user"]
        appConfig["application.yml<br/>vault.server-url<br/>vault.api-key"]

        sdkFilter --> sdkClient
        sdkFilter --> sdkContext
        sdkContext --> appController
        appController --> appService
        appConfig --> sdkClient
    end

    subgraph vaultServer["vault-server"]
        publicAuth["Public Auth API<br/>/auth/register<br/>/auth/login<br/>/auth/refresh<br/>/auth/logout<br/>/auth/me"]
        internalApi["Internal SDK API<br/>POST /internal/validate<br/>POST /internal/audit"]
        apiKeyApi["API Key API<br/>/api-keys<br/>/admin/api-keys"]
        jwtService["JwtService<br/>HS256 access + refresh tokens"]
        userService["UserDetailsService<br/>user lookup + roles"]
        auditService["Audit Service / AOP"]
        rateLimit["Rate Limit Service<br/>token bucket"]
    end

    subgraph data["Vault-owned Data Stores"]
        postgres[("PostgreSQL<br/>users<br/>api_keys<br/>audit_logs")]
        redis[("Redis<br/>JWT blacklist<br/>rate-limit buckets")]
    end

    actor -- "1. login/register" --> publicAuth
    publicAuth --> jwtService
    publicAuth --> userService
    userService --> postgres
    jwtService --> redis
    publicAuth -- "2. returns JWT" --> actor

    actor -- "3. request with Authorization: Bearer JWT<br/>or X-API-Key" --> sdkFilter
    sdkClient -- "4. service-to-service validation<br/>X-Vault-Api-Key" --> internalApi
    internalApi --> jwtService
    internalApi --> userService
    internalApi --> rateLimit
    internalApi --> redis
    internalApi --> postgres
    internalApi -- "5. valid + userId + tenantId + role + scopes" --> sdkClient
    sdkFilter -- "6. continue request" --> appController
    sdkClient -. "7. async audit event" .-> internalApi
    internalApi --> auditService
    auditService --> postgres

    apiKeyApi --> postgres
```

## SDK Integration Flow

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant Service as Consumer Spring Boot Service
    participant Filter as vault-sdk VaultAuthFilter
    participant ClientSdk as vault-sdk VaultClient
    participant Vault as vault-server
    participant Redis
    participant Postgres
    participant Controller as Business Controller

    Client->>Service: HTTP request with Bearer JWT or X-API-Key
    Service->>Filter: Servlet filter chain enters SDK
    Filter->>Filter: Skip public paths, otherwise extract credential
    Filter->>ClientSdk: validateToken(token) or validateApiKey(key)
    ClientSdk->>Vault: POST /internal/validate<br/>X-Vault-Api-Key: service key
    Vault->>Vault: Verify JWT signature, expiry, type, role, scopes
    Vault->>Redis: Check blacklist and rate-limit keys
    Vault->>Postgres: Load user/API-key metadata when needed
    Vault-->>ClientSdk: { valid, userId, tenantId, role, scopes }
    ClientSdk-->>Filter: VaultUser / VaultPrincipal
    Filter->>Service: Populate VaultSecurityContext and Spring SecurityContext
    Service->>Controller: Continue filter chain
    Controller-->>Client: Business response
    ClientSdk--)Vault: POST /internal/audit with request outcome
    Vault->>Postgres: Insert audit_logs row
```

## How Components Should Communicate

### 1. Consumer application to `vault-sdk`

The host service should communicate with the SDK through Spring, not by manually wiring HTTP calls.

- Add `vault-sdk` as a Maven dependency.
- Configure the SDK with Vault server URL and a service-to-service API key.
- Let Spring Boot auto-configuration create `VaultClient`, `VaultAuthFilter`, and `VaultSecurityContext`.
- Controllers and services read the current identity from `VaultSecurityContext` or Spring Security's `SecurityContextHolder`.

Recommended consumer config:

```yaml
vault:
  enabled: true
  server-url: http://vault-server:8081
  api-key: vault_service_key_issued_by_vault_server
```

The implementation guide names these properties as `vault.base-url` and `vault.service-api-key`. The current code uses `vault.server-url` and `vault.api-key` in `VaultProperties`, so either the guide or the property class should be aligned before publishing the SDK.

### 2. `vault-sdk` to `vault-server`

The SDK should be the only part of the consumer service that talks to `vault-server`.

- `VaultAuthFilter` extracts the incoming caller credential.
- `VaultClient` sends validation requests to `vault-server`.
- Every SDK-to-server request includes the consumer service API key in `X-Vault-Api-Key`.
- `vault-server` treats `/internal/**` as service-to-service endpoints, separate from user-facing authentication.

Recommended validation contract:

```http
POST /internal/validate
X-Vault-Api-Key: vault_service_key_issued_by_vault_server
Content-Type: application/json

{
  "token": "jwt_or_api_key",
  "type": "JWT"
}
```

Recommended response:

```json
{
  "valid": true,
  "userId": "uuid",
  "tenantId": "uuid",
  "email": "user@example.com",
  "role": "USER",
  "scopes": ["READ", "WRITE"]
}
```

### 3. `vault-server` to PostgreSQL

Only `vault-server` owns security data.

- `users`: user identity, BCrypt password hash, tenant, role, enabled flag.
- `api_keys`: BCrypt-hashed API keys, key prefix, scopes, expiry, revoke state, tenant ownership.
- `audit_logs`: cross-service audit trail with tenant, user, action, resource, status, metadata.

Consumer services should not create or duplicate these tables. Their databases stay focused on business data.

### 4. `vault-server` to Redis

Redis is used for fast, TTL-based security state:

- `blacklist:{token}`: revoked JWTs after logout until original token expiry.
- `ratelimit:{tenantId}:{keyId}:tokens`: token bucket count.
- `ratelimit:{tenantId}:{keyId}:reset`: reset timestamp for `Retry-After`.

The SDK does not need direct Redis access. Keeping Redis behind `vault-server` prevents every consumer service from needing security infrastructure credentials.

### 5. Audit communication

After each protected request, the SDK should send an asynchronous audit event to `vault-server`.

```http
POST /internal/audit
X-Vault-Api-Key: vault_service_key_issued_by_vault_server
Content-Type: application/json

{
  "tenantId": "uuid",
  "userId": "uuid",
  "action": "HTTP_REQUEST",
  "resource": "GET /orders/123",
  "status": "SUCCESS",
  "metadata": {
    "service": "orders-service",
    "durationMs": 42
  }
}
```

Audit should be best effort: a failed audit write must not break the business request unless the product explicitly requires strict compliance mode.

## Runtime Boundaries

```mermaid
flowchart TB
    subgraph build["Build-time / dependency relationship"]
        root["vault parent pom"]
        sdkArtifact["vault-sdk Maven artifact"]
        demo["demo-service"]
        root --> sdkArtifact
        root --> demo
        demo --> sdkArtifact
    end

    subgraph runtime["Runtime / network relationship"]
        consumer["Consumer service JVM<br/>includes vault-sdk jar"]
        auth["vault-server JVM"]
        pg[("PostgreSQL")]
        rd[("Redis")]

        consumer -- "HTTP internal validation/audit" --> auth
        auth --> pg
        auth --> rd
    end

    sdkArtifact -. "packaged into" .-> consumer
```

## Current Repo vs Target Architecture

The current project already contains:

- Maven modules for `vault-server`, `vault-sdk`, and `demo-service`.
- SDK auto-configuration registration via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- `VaultAuthFilter`, `VaultClient`, `VaultProperties`, and `VaultSecurityContext`.
- JWT creation and validation logic in `vault-server`.
- PostgreSQL migrations for `users`, `api_keys`, and `audit_logs`.
- Redis and PostgreSQL services in `docker-compose.yml`.

The guide-level architecture still needs these implementation pieces:

- `/internal/validate` endpoint in `vault-server`.
- `/internal/audit` endpoint in `vault-server`.
- API key generation, hashing, validation, scope enforcement, and revocation services.
- Redis-backed JWT blacklist and rate limiting.
- SDK support for `X-API-Key`, public path skipping, scope/role mapping into Spring Security, and async audit dispatch.
- Alignment between guide property names and current `VaultProperties`.

