# Vault SDK

Vault SDK is a small Spring Boot security library.

It adds a ready-made JWT and API-key authentication layer to a Spring Boot application while letting that application keep its own user table, user entity, and user repository.

In other words, it is not a separate auth server. It is a Maven dependency that runs inside your Spring Boot app and registers Spring beans such as controllers, services, filters, and repositories.

## What It Does

Vault SDK provides:

- JWT login, refresh, logout, and current-user endpoints
- access token and refresh token generation
- Redis-backed token blacklist for logout
- API key creation, validation, revocation, and scoped access
- Redis-backed API key rate limiting
- audit logging for security actions
- a Spring Security filter that authenticates requests using either JWTs or API keys

Your application still owns:

- the user table
- the user entity
- user registration flow
- business-specific user fields
- roles/tenant rules
- application-specific controllers

The SDK only needs a small adapter so it can find users in your application.

## How It Relates To Spring Security

Vault SDK does not replace Spring Security.

It uses Spring Security underneath:

- `SecurityFilterChain`
- `OncePerRequestFilter`
- `SecurityContextHolder`
- `Authentication`
- authorities / roles
- method security such as `@PreAuthorize`
- `PasswordEncoder`

The difference is that Vault SDK does not use Spring Security's default form-login or session-based login flow.

Instead, it provides a stateless API flow:

```text
POST /auth/login
  -> SDK checks the application's user repository
  -> SDK verifies the password hash
  -> SDK returns JWT access and refresh tokens
```

For protected requests:

```text
Authorization: Bearer <jwt>
  -> VaultAuthFilter validates the token
  -> VaultAuthentication is placed in Spring SecurityContextHolder
  -> the controller runs as an authenticated request
```

For API-key requests:

```text
X-API-Key: vault_xxxxx
  -> VaultAuthFilter validates the hashed API key
  -> rate limit is checked in Redis
  -> API key scopes become Spring Security authorities
```

So the SDK is best understood as a custom JWT/API-key authentication module built on top of Spring Security.

## Installation

Vault SDK is available on Maven Central:

```xml
<dependency>
    <groupId>io.github.hesandaliyanage</groupId>
    <artifactId>vault-sdk</artifactId>
    <version>0.1.1</version>
</dependency>
```

Artifact: https://repo1.maven.org/maven2/io/github/hesandaliyanage/vault-sdk/

## Requirements

- Spring Boot 4.x
- PostgreSQL
- Flyway
- Redis

## Minimal Setup

Your Spring Boot app must provide a `VaultUserRepository` bean.

Example:

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

The object returned by your repository must implement `VaultUser`:

```java
public interface VaultUser {
    String getVaultId();
    String getEmail();
    String getPasswordHash();
    String getRole();
    String getTenantId();
}
```

Configure JWT and public paths:

```yaml
vault:
  jwt:
    secret: ${VAULT_JWT_SECRET}
  public-paths:
    - /auth/**
    - /public/**
    - /actuator/health
```

Use a strong `VAULT_JWT_SECRET` with at least 32 bytes.

## Provided Endpoints

Authentication:

- `POST /auth/validate-registration`
- `POST /auth/hash-password`
- `POST /auth/login`
- `POST /auth/refresh`
- `POST /auth/logout`
- `GET /auth/me`

API keys:

- `POST /vault/api-keys`
- `GET /vault/api-keys`
- `DELETE /vault/api-keys/{id}`

Audit:

- `GET /vault/audit/logs`

## What The SDK Does Not Do

Vault SDK does not force a user schema.

It does not create your application's users table and it does not own your registration business logic. Your app creates users however it wants. Vault SDK handles the reusable security parts around that user model.
