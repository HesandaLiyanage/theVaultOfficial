# Vault SDK

Embedded Spring Boot security SDK for JWT authentication, API keys, audit logging, and Redis-backed token/rate-limit state.

## Install from JitPack

Add JitPack:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

Add the SDK dependency:

```xml
<dependency>
    <groupId>com.github.YOUR_GITHUB_USERNAME.theVault</groupId>
    <artifactId>vault-sdk</artifactId>
    <version>TAG</version>
</dependency>
```

Replace `YOUR_GITHUB_USERNAME` and `TAG` with your GitHub owner and release tag. If your GitHub repository name is different from `theVault`, use that repository name in the JitPack group id.

## Minimal Setup

Your Spring Boot app must provide a `VaultUserRepository` bean:

```java
@Component
class UserVaultAdapter implements VaultUserRepository {
    private final UserRepository users;

    UserVaultAdapter(UserRepository users) {
        this.users = users;
    }

    public Optional<VaultUser> findByEmail(String email) {
        return users.findByEmail(email).map(user -> user);
    }

    public Optional<VaultUser> findById(String id) {
        return users.findById(UUID.fromString(id)).map(user -> user);
    }
}
```

Configure JWT and infrastructure:

```yaml
vault:
  jwt:
    secret: ${VAULT_JWT_SECRET}
  public-paths:
    - /auth/**
    - /public/**
    - /actuator/health
```

Use a strong `VAULT_JWT_SECRET` with at least 32 bytes. The SDK also expects PostgreSQL/Flyway and Redis to be available in the host Spring Boot app.

## Provided Endpoints

- `POST /auth/validate-registration`
- `POST /auth/hash-password`
- `POST /auth/login`
- `POST /auth/refresh`
- `POST /auth/logout`
- `GET /auth/me`
- `POST /vault/api-keys`
- `GET /vault/api-keys`
- `DELETE /vault/api-keys/{id}`
- `GET /vault/audit/logs`

The SDK is embedded. There is no separate Vault server process in the published artifact.
