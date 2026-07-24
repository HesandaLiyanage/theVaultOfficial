# The Vault — Interview Preparation Guide

> A centralized security platform for Spring Boot microservices. JWT auth, API keys, audit logging, rate limiting — one HTTP endpoint instead of reimplementing in every service.

---

## Table of Contents

1. [Elevator Pitch](#1-elevator-pitch)
2. [Tech Stack](#2-tech-stack)
3. [Architecture Overview](#3-architecture-overview)
4. [Module Breakdown](#4-module-breakdown)
5. [Deep Dive: JWT Internals](#5-deep-dive-jwt-internals)
6. [Deep Dive: Spring Security Filter Chain](#6-deep-dive-spring-security-filter-chain)
7. [Deep Dive: Distributed Systems Aspects](#7-deep-dive-distributed-systems-aspects)
8. [Database Schema](#8-database-schema)
9. [API Reference](#9-api-reference)
10. [Key Design Decisions](#10-key-design-decisions)
11. [Security Considerations](#11-security-considerations)
12. [Testing Strategy](#12-testing-strategy)
13. [Interview Q&A](#13-interview-qa)
14. [Code Snippets to Memorize](#14-code-snippets-to-memorize)

---

## 1. Elevator Pitch

> "The Vault is a **client-server security platform** for Spring Boot apps. It moves JWT authentication, API key management, audit logging, and rate limiting into a standalone service. Consumer apps add a thin SDK (~5 classes) that validates tokens by talking to Vault Server over HTTP. Result: every microservice in an organization gets consistent security policy without duplicating code."

**Key numbers:**
- 5 Maven modules
- ~4000 lines of production code
- ~1500 lines of test code
- v0.2.x (redesigned from in-process library to client-server architecture)

---

## 2. Tech Stack

| Layer | Technology | Why |
|---|---|---|
| Language | Java 21 | Records, pattern matching, virtual threads (future) |
| Framework | Spring Boot 4.0.6 / Spring Framework 6.x | Latest stable |
| Build | Maven multi-module | Dependency control, separate publish cycles |
| Database | PostgreSQL 16 | Strong consistency for audit + API keys |
| Cache | Redis 7 | Token blacklist TTL, rate limit sliding window |
| JWT | JJWT 0.13.0 (io.jsonwebtoken) | RS256 signing, mature API |
| JWKS | Nimbus JOSE + JWT 10.0.2 | Standard JWK set format |
| SDK Cache | Caffeine | Local in-process cache, sub-microsecond lookup |
| Migrations | Flyway | Version-controlled schema evolution |
| Password Hashing | BCrypt (strength 12) | Slow enough for 2026 (≈200ms per hash) |
| Testing | JUnit 5, AssertJ, MockMvc, MockRestServiceServer | Standard Spring test stack |
| Dep Injection | Spring (constructor injection) | Immutable beans, testable |
| Serialization | Jackson | Default Spring Boot choice |

---

## 3. Architecture Overview

### 3.1 High-Level Diagram

```
┌─────────────────────────────────────────────────┐
│                  Client / API Consumer           │
│         Authorization: Bearer <JWT>              │
│         X-API-Key: <key>                        │
└──────────────────┬──────────────────────────────┘
                   │
                   ▼
┌──────────────────────────────────────────────────┐
│          Your Spring Boot App (consumer)          │
│                                                   │
│  ┌──────────────┐    ┌───────────────────────┐   │
│  │ @RestController│    │   VaultAuthFilter     │   │
│  │   /orders     │◄───│  (OncePerRequestFilter)│   │
│  └──────────────┘    └──────────┬────────────┘   │
│                                 │               │
│                   ┌─────────────▼───────────┐   │
│                   │   TokenValidator chain   │   │
│                   │  ┌───────────────────┐  │   │
│                   │  │JwksTokenValidator │  │   │
│                   │  │(optional, local   │  │   │
│                   │  │ JWT verify)       │  │   │
│                   │  └────────┬──────────┘  │   │
│                   │  ┌────────▼──────────┐  │   │
│                   │  │CachingVaultClient  │  │   │
│                   │  │(optional Caffeine) │  │   │
│                   │  └────────┬──────────┘  │   │
│                   │  ┌────────▼──────────┐  │   │
│                   │  │   VaultClient      │  │   │
│                   │  │ (RestClient HTTP)  │  │   │
│                   │  └───────────────────┘  │   │
│                   └─────────────┬───────────┘   │
│                                 │               │
│  ┌─────────────────────────┐   │               │
│  │  VaultAuditClient       │───┘               │
│  │  (async, bounded queue) │                   │
│  └─────────────────────────┘                   │
└──────────────────────────┬───────────────────────┘
                           │ X-Service-Key header
                           ▼
┌──────────────────────────────────────────────────┐
│              Vault Server (port 8081)             │
│                                                   │
│  ┌────────────┐  ┌────────────┐  ┌──────────┐   │
│  │ Auth       │  │ API Key    │  │ Audit    │   │
│  │ Controller │  │ Controller │  │ Service  │   │
│  ├────────────┤  ├────────────┤  ├──────────┤   │
│  │ JwtService │  │ ApiKeySrvc │  │ AuditAspect│  │
│  │ (RS256)    │  │ (BCrypt)   │  │ (AOP)    │   │
│  └─────┬──────┘  └──────┬─────┘  └─────┬────┘   │
│        │               │              │         │
│  ┌─────▼──────┐  ┌─────▼──────┐  ┌────▼─────┐  │
│  │ Token      │  │ API Keys   │  │ Audit    │  │
│  │ Blacklist  │  │ (Postgres) │  │ Logs     │  │
│  │ (Redis)    │  │            │  │ (Postgres)│  │
│  └────────────┘  └────────────┘  └──────────┘   │
│                                                   │
│  ┌──────────────────────────────────────────┐    │
│  │    RateLimitService (Redis sliding window)│    │
│  └──────────────────────────────────────────┘    │
│                                                   │
│  ┌──────────────────────────────────────────┐    │
│  │    InternalController                     │    │
│  │    POST /internal/validate                │    │
│  │    POST /internal/audit                   │    │
│  │    GET  /.well-known/jwks.json            │    │
│  └──────────────────────────────────────────┘    │
└──────────────────────────────────────────────────┘
                        │
            ┌───────────┴───────────┐
            ▼                       ▼
      ┌──────────┐           ┌──────────┐
      │ Postgres │           │  Redis   │
      │  :5433   │           │  :6379   │
      └──────────┘           └──────────┘
```

### 3.2 Module Dependency Graph

```
vault (root POM)
├── vault-protocol (pure DTOs, no deps)
│   └── TokenType, ValidateRequest, ValidateResponse, AuditRequest
│
├── vault-server (Spring Boot app)
│   ├── depends on: vault-protocol
│   ├── deps: spring-boot-starter-web, spring-security, spring-data-jpa
│   │        postgresql, flyway, redis, jjwt, lombok
│   └── responsibility: owns ALL security state
│
├── vault-sdk (thin client library)
│   ├── depends on: vault-protocol
│   ├── deps: spring-web, spring-security-web, jackson, slf4j
│   │   [optional] caffeine, nimbus-jose-jwt
│   └── responsibility: validate tokens, populate SecurityContext
│
├── vault-sdk-legacy (v0.1.x, @Deprecated forRemoval=true)
│   └── in-process security stack (removed in v0.3.0)
│
└── demo-service (example consumer)
    └── depends on: vault-sdk
```

### 3.3 Request Flow (Sequence)

```
Client          Consumer App (SDK)              Vault Server         Database/Redis
  │                    │                              │                    │
  │  POST /orders      │                              │                    │
  │  Authorization:    │                              │                    │
  │  Bearer eyJ...     │                              │                    │
  │ ─────────────────► │                              │                    │
  │                    │  VaultAuthFilter              │                    │
  │                    │  ├─ isPublic()? no            │                    │
  │                    │  ├─ extractBearer() → JWT     │                    │
  │                    │  └─ validator.validate()      │                    │
  │                    │       │                       │                    │
  │                    │  [cache check - optional]     │                    │
  │                    │       │ (miss)                │                    │
  │                    │       │                       │                    │
  │                    │       │ POST /internal/validate│                    │
  │                    │       │ X-Service-Key: ***    │                    │
  │                    │ ───────────────────────────► │                    │
  │                    │       │                       │                    │
  │                    │       │                  requireServiceKey()      │
  │                    │       │                  validateJwt():            │
  │                    │       │                  ├─ parse + verify RS256  │
  │                    │       │                  ├─ check blacklist(Redis)│
  │                    │       │                  └─ extract claims        │
  │                    │       │                       │                    │
  │                    │       │           ValidateResponse ◄──────────────┤
  │                    │       │ ◄──────────────────── │                    │
  │                    │       │                       │                    │
  │                    │  [cache store - optional]     │                    │
  │                    │       │                       │                    │
  │                    │  if valid:                     │                    │
  │                    │    VaultAuthenticationToken     │                    │
  │                    │    → SecurityContextHolder      │                    │
  │                    │       │                       │                    │
  │                    │  chain.doFilter()              │                    │
  │                    │       │                       │                    │
  │                    │  @PreAuthorize("hasRole('USER')")                   │
  │                    │  @RestController.handler()     │                    │
  │                    │       │                       │                    │
  │                    │  ← 200 OK (JSON)             │                    │
  │ ◄───────────────── │       │                       │                    │
  │                    │       │                       │                    │
  │                    │  finally: clearContext()      │                    │
  │                    │       │                       │                    │
  │                    │  ─ ─ ─ async ─ ─ ─            │                    │
  │                    │  VaultAuditClient.record()    │                    │
  │                    │  ─ ─ ─ POST /internal/audit ─►│                    │
  │                    │              (fire & forget)  │  → INSERT audit_log│
```

---

## 4. Module Breakdown

### 4.1 `vault-protocol` (4 files)

Pure DTOs shared over HTTP between server and SDK. Zero runtime dependencies.

```java
// TokenType.java
public enum TokenType { JWT, API_KEY }

// ValidateRequest.java
public record ValidateRequest(String token, TokenType type) {}

// ValidateResponse.java
public record ValidateResponse(
    boolean valid,
    String userId,
    String tenantId,
    String role,
    List<String> scopes,
    String reason
) {
    public static ValidateResponse success(...) { ... }
    public static ValidateResponse failure(String reason) {
        return new ValidateResponse(false, null, null, null, List.of(), reason);
    }
}

// AuditRequest.java
public record AuditRequest(String tenantId, String userId, String action,
                           String resource, String status) {}
```

**Why separate module?** Server and SDK must agree on the wire format. A shared module guarantees type safety. No Spring dependency means it can never leak framework concerns.

### 4.2 `vault-server` (standalone Spring Boot service)

**Package:** `com.hess.thevault`

**Responsibilities:**
- User authentication (login, refresh, logout)
- JWT signing (RS256) and validation
- API key generation, validation, revocation
- Audit logging (sync via AOP + async server-side storage)
- Rate limiting per API key (Redis sliding window)
- JWKS endpoint for public key distribution

**Key entry point — `InternalController`:**
```java
@PostMapping("/validate")
public ValidateResponse validate(
    @RequestHeader(value = "X-Service-Key", required = false) String serviceKey,
    @Valid @RequestBody ValidateRequest request
) {
    requireServiceKey(serviceKey);  // constant-time comparison
    // route to JWT validation or API key validation
}
```

### 4.3 `vault-sdk` (thin client SDK)

**Package:** `io.github.hesandaliyanage.vault.sdk`

**What it provides:**
- `VaultAuthFilter` — servlet filter that intercepts requests, validates tokens, populates `SecurityContextHolder`
- `TokenValidator` interface + implementations (VaultClient, CachingVaultClient, JwksTokenValidator)
- `VaultAuditClient` — async fire-and-forget audit dispatcher
- `VaultAutoConfiguration` — Spring Boot auto-config (activates on `vault.client.base-url`)
- `VaultPrincipal` / `VaultAuthenticationToken` — Spring Security primitives

**What it does NOT pull in:** JPA, Flyway, PostgreSQL, Redis, JJWT. The SDK stays pure HTTP.

### 4.4 `vault-sdk-legacy` (deprecated)

v0.1.x in-process security stack. Entire package annotated `@Deprecated(since="0.2.0", forRemoval=true)`. Kept for one minor release to ease migration. Removed in v0.3.0.

### 4.5 `demo-service`

Example consumer that shows integration. Endpoints:
```
GET  /public/ping          → no auth
GET  /protected/profile    → reads VaultPrincipal
GET  /protected/data       → requires orders:read scope
POST /protected/data       → requires orders:write scope
GET  /admin/stats          → requires ROLE_ADMIN
```

---

## 5. Deep Dive: JWT Internals

### 5.1 What is a JWT?

JSON Web Token — a URL-safe, self-contained way to transmit claims between parties. Three Base64url-encoded segments separated by dots:

```
header.payload.signature
```

**Header:**
```json
{
  "alg": "RS256",
  "kid": "vault-default",
  "typ": "JWT"
}
```

**Payload (custom claims):**
```json
{
  "sub": "user-uuid",
  "vaultId": "user-uuid",
  "email": "user@example.com",
  "tenantId": "tenant-uuid",
  "role": "USER",
  "type": "ACCESS",
  "iat": 1700000000,
  "exp": 1700000900
}
```

**Signature:** RS256(header + ".", payload, privateKey)

### 5.2 RS256 vs HS256 — Why RS256?

| Aspect | HS256 (HMAC) | RS256 (RSA) |
|---|---|---|
| Key type | Single shared secret | Public/private keypair |
| Signer | Anyone with secret | Only private key holder |
| Verifier | Must know secret | Anyone with public key |
| Key rotation | Must update all services | Publish new JWKS, old key cached |
| Key distribution | Out-of-band, secure channel | HTTP via `/.well-known/jwks.json` |

**Why RS256 wins for this architecture:** The SDK needs to verify tokens WITHOUT holding the signing key. With RS256, vault-server signs with private key, SDK verifies with public key fetched from JWKS endpoint. If someone steals the SDK, they can verify but cannot forge tokens.

### 5.3 Key Generation (current caveat)

```java
private static KeyPair generateRsaKeyPair() {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);  // 2048-bit RSA
    return generator.generateKeyPair();
}
```

**Current limitation (v0.2.0):** Keypair is generated at startup and stored only in memory. After restart, all existing tokens become invalid. The log even warns:
```
JwtService generated an ephemeral RSA keypair at startup.
Tokens issued by this instance will not validate after a restart.
```

**Planned fix (v0.2.1):** Load keypair from a configured keystore (JKS/PKCS12) so it survives restarts.

**What to say in interview:** "The current version generates an ephemeral keypair for development. Production must use a persistent keystore. This is a known limitation being addressed in the next release."

### 5.4 Token Creation (Login)

```java
// JwtService.java
private String generateToken(VaultUser user, String tokenType, long expirationMs) {
    Instant now = Instant.now();
    Instant expiresAt = now.plusMillis(expirationMs);

    return Jwts.builder()
            .header().keyId(keyId).and()
            .claims(Map.of(
                    "vaultId", user.getVaultId(),
                    "email", user.getEmail(),
                    "tenantId", user.getTenantId().toString(),
                    "role", user.getRole(),
                    "type", tokenType
            ))
            .subject(user.getVaultId())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .signWith(privateKey, Jwts.SIG.RS256)
            .compact();
}
```

- Access token TTL: **15 minutes** (configurable via `jwt.expiration-ms`)
- Refresh token TTL: **7 days** (configurable via `jwt.refresh-expiration-ms`)

### 5.5 Token Validation

```java
public boolean isTokenValid(String token) {
    // 1. Check Redis blacklist (was this token revoked?)
    if (tokenBlacklistService.isBlacklisted(token)) return false;

    // 2. Parse, verify RS256 signature, extract claims
    Claims claims = Jwts.parser()
            .verifyWith(publicKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();

    // 3. Validate required claims exist
    requireClaim(claims, "vaultId");
    requireClaim(claims, "email");
    requireClaim(claims, "tenantId");
    requireClaim(claims, "role");
    isRecognizedTokenType(requireClaim(claims, "type"));
    return true;
}
```

### 5.6 Token Blacklist (Logout)

```java
// TokenBlacklistService.java
public void blacklist(String token, Duration ttl) {
    String key = "blacklist:" + sha256Base64Url(token);
    redisTemplate.opsForValue().set(key, "true", ttl);
}

public boolean isBlacklisted(String token) {
    return Boolean.TRUE.equals(redisTemplate.hasKey(blacklistKey(token)));
}
```

**Why SHA-256 hash of the token as Redis key?**
- Raw JWT can be very long (signed RS256 tokens are ~500+ chars)
- Redis keys should be short for memory efficiency
- SHA-256 produces a fixed 32 bytes (Base64url → 43 chars)
- Prevents accidentally leaking token content in Redis logs/keyspace

**TTL = remaining validity of token** — the blacklist entry auto-expires when the token would have expired anyway.

### 5.7 Refresh Token Flow

```
1. Client sends refreshToken to POST /auth/refresh
2. Server validates it's a valid REFRESH-type JWT
3. Server issues a NEW access token (and optionally new refresh token)
4. Old tokens remain valid until natural expiry (no blacklist rotation in v0.2.0)
```

### 5.8 JWKS Endpoint

```
GET /.well-known/jwks.json  (unauthenticated)
```

Returns the RSA public key in standard JWK format:
```json
{
  "keys": [{
    "kty": "RSA",
    "kid": "vault-default",
    "n": "base64url-encoded-modulus",
    "e": "AQAB"
  }]
}
```

**JWKS Local Verification Mode (SDK-side):**
- SDK fetches JWKS from vault-server
- Verifies RS256 signature locally using the public key
- Optionally skips remote revocation check (`skip-remote-revocation-check=true`)
- API keys always delegated to remote (they're not JWT-signed)

---

## 6. Deep Dive: Spring Security Filter Chain

### 6.1 The Filter Chain Architecture

Spring Security uses a chain of servlet filters. Each filter either:
1. **Passes** the request to the next filter
2. **Short-circuits** (returns error, redirect, etc.)
3. **Processes** authentication and continues

**Standard order (simplified):**
```
SecurityContextPersistenceFilter     ← clears context between requests
LogoutFilter
UsernamePasswordAuthenticationFilter ← form login
DefaultLoginPageGeneratingFilter
DefaultLogoutPageGeneratingFilter
BasicAuthenticationFilter
RequestCacheAwareFilter
SecurityContextHolderAwareRequestFilter
AnonymousAuthenticationFilter
SessionManagementFilter
ExceptionTranslationFilter           ← translates AccessDeniedException → 403
FilterSecurityInterceptor            ← @PreAuthorize checks here
```

### 6.2 Where VaultAuthFilter Inserts

```java
// vault-sdk VaultAuthFilter → registered by consumer:
.addFilterBefore(vaultAuthFilter, UsernamePasswordAuthenticationFilter.class)
```

**Why before `UsernamePasswordAuthenticationFilter`?** Because we want the Vault filter to run early — it establishes the SecurityContext so `@PreAuthorize` and `@AuthenticationPrincipal` work in controllers. It runs before form login filters (which are irrelevant for a stateless API).

### 6.3 VaultAuthFilter Internals

```java
public class VaultAuthFilter extends OncePerRequestFilter {
    // 1. Check public paths (bypass)
    // 2. Extract X-API-Key header → validate as API_KEY type
    // 3. Extract Authorization: Bearer → validate as JWT type
    // 4. On success: set VaultAuthenticationToken on SecurityContextHolder
    // 5. On failure: 401 Unauthorized
    // 6. finally: clear SecurityContextHolder
}
```

**Key design details:**

**Public path bypass (with path traversal defense):**
```java
private boolean isPublic(HttpServletRequest request) {
    String path = request.getRequestURI();
    if (path == null || hasSuspiciousSegment(path)) return false;
    // hasSuspiciousSegment checks: "..", "%2e", "%2f", "\"
    for (String pattern : properties.publicPaths()) {
        if (pathMatcher.match(pattern, path)) return true;
    }
    return false;
}
```

This prevents `/public/../admin/stats` from bypassing auth.

**API key vs Bearer priority:** API key is checked first. If both are present, API key takes precedence. This allows automated systems (scripts, CI) to use API keys while browser sessions use JWTs.

**SecurityContext cleanup in `finally`:**
```java
try {
    chain.doFilter(request, response);
} finally {
    SecurityContextHolder.clearContext();
}
```

Prevents context leaking between requests in thread-pooled environments.

### 6.4 VaultAuthenticationToken

```java
public class VaultAuthenticationToken extends AbstractAuthenticationToken {
    private final VaultPrincipal principal;

    public VaultAuthenticationToken(VaultPrincipal principal) {
        super(authorities(principal));  // ROLE_ + scopes as GrantedAuthority
        this.principal = principal;
        setAuthenticated(true);
    }

    private static List<GrantedAuthority> authorities(VaultPrincipal p) {
        List<GrantedAuthority> auths = new ArrayList<>();
        auths.add(new SimpleGrantedAuthority("ROLE_" + p.role()));
        p.scopes().stream()
            .map(SimpleGrantedAuthority::new)
            .forEach(auths::add);
        return auths;
    }
}
```

This enables both:
- `@PreAuthorize("hasRole('ADMIN')")` — checks `ROLE_ADMIN` authority
- `@PreAuthorize("hasAuthority('orders:write')")` — checks scope as authority

### 6.5 How @PreAuthorize Works

1. `FilterSecurityInterceptor` (last in chain) checks if the request requires authorization
2. Uses `SecurityContextHolder.getContext().getAuthentication()` to get `VaultAuthenticationToken`
3. Calls `authorities` from the token
4. `PrePostAnnotationSecurityMetadataSource` reads `@PreAuthorize` annotations
5. `ExpressionBasedPreInvocationAdvice` evaluates SpEL expressions like `hasRole('ADMIN')`
6. If denied → `AccessDeniedException` → `ExceptionTranslationFilter` → 403

### 6.6 Server-Side SecurityConfig

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/**").permitAll()
                .requestMatchers("/internal/**").permitAll()
                .requestMatchers("/.well-known/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
```

Note: `/internal/**` is `permitAll` because `JwtAuthFilter` is configured to **skip** `/internal/**` (it relies on `X-Service-Key` header instead of JWT). The `InternalController` handles service key auth itself.

---

## 7. Deep Dive: Distributed Systems Aspects

### 7.1 Client-Server Split (v0.1.x → v0.2.x)

**Problem (v0.1.x):** The library ran in-process. Every consumer app needed PostgreSQL, Redis, Flyway, and the signing key. N apps = N copies of everything. Key management was a nightmare. Database migrations had to run N times. Upgrading required N deployments.

**Solution (v0.2.x):** One `vault-server` instance handles all security state. Consumer apps add a thin HTTP client (`vault-sdk`). The SDK has zero database dependencies.

**Trade-offs:**

| Aspect | In-Process (v0.1.x) | Client-Server (v0.2.x) |
|---|---|---|
| Latency | None (direct call) | +1 network hop (≈1-5ms) |
| Availability | Always available if app is up | Depends on vault-server |
| Consistency | Local, strong | Depends on network |
| Operations | N deployments | 1 deployment + N SDK configs |
| Key management | Distributed | Centralized |
| Upgrades | N apps must update SDK | Server updated once |
| Complexity per app | High (DB, Redis, migrations) | Low (3 config properties) |

**How they mitigate the added latency:**
1. **Caching** (Caffeine, 30s positive TTL) — most requests skip the network hop
2. **JWKS local verification** — verify JWT signature locally, only check revocation remotely
3. **Async audit** — never blocks request threads

### 7.2 Decorator Pattern for Validation Pipeline

```java
// VaultAutoConfiguration.java
TokenValidator validator = new VaultClient(restClient, serviceKey);
if (cacheEnabled) {
    validator = new CachingVaultClient(validator, cacheProperties);
}
if (jwksUri != null) {
    validator = new JwksTokenValidator(jwksUri, issuer, audience, validator, skipRemoteRevocation);
}
return validator;
```

**Resulting chain (if all enabled):**
```
VaultAuthFilter
  → JwksTokenValidator (local RS256 verify)
    → CachingVaultClient (Caffeine cache)
      → VaultClient (HTTP POST to vault-server)
```

Each layer implements `TokenValidator` interface, delegating to the next. This is the **Decorator pattern** — you stack behaviors transparently.

### 7.3 Caching Strategy

| Aspect | Positive Response (valid) | Negative Response (invalid) | Transport Failure |
|---|---|---|---|
| TTL | 30s (configurable) | 5s (configurable) | NOT cached |
| Rationale | Acceptable staleness; revocation visible within 30s | Don't poison cache with bogus tokens for long | Network errors are transient; retry immediately |
| Max cache size | 10,000 entries (configurable) | Same pool | N/A |

**Thread safety:** Caffeine is fully concurrent (uses ConcurrentHashMap internally). No locks needed.

**Cache key:** The raw token string (JWT or API key). For JWT, the token has unique `iat` claim per issuance, so each session gets its own cache entry.

### 7.4 Async Audit with Bounded Queue

**Architecture:**
```
Controller (request thread)
    ↓ offer() ← non-blocking, returns false if full
┌─────────────────────┐
│ LinkedBlockingQueue  │  capacity: 1024 (configurable)
│ (FIFO, bounded)      │
└──────────┬──────────┘
           ↓ poll() ← daemon thread
┌──────────────────────┐
│ single worker thread  │  "vault-audit-dispatcher"
│ drain loop            │
└──────────┬───────────┘
           ↓ POST /internal/audit
       vault-server
```

**Key behaviors:**
- `record()` returns immediately (non-blocking `offer()`)
- On queue full → `WARN` log, event is **dropped** (never blocks request thread)
- Worker is a **daemon thread** — won't prevent JVM shutdown
- On server unreachable → `WARN` log, event **lost** (acceptable for audit in high-throughput systems)
- On `close()` → waits up to 2s for queue to drain, then interrupts

**What to say in interview:** "Audit is designed to be best-effort. We chose to drop events rather than block request threads because blocking auth for audit would be worse than losing audit events. Production systems that need guaranteed delivery would need a more robust transport (e.g., Kafka, reliable event bus)."

### 7.5 Rate Limiting (Redis Sliding Window)

```java
public RateLimitResult consume(ApiKey apiKey) {
    String tokensKey = "ratelimit:%s:%s:tokens".formatted(
        apiKey.getTenantId(), apiKey.getId());
    String resetKey = "ratelimit:%s:%s:reset".formatted(
        apiKey.getTenantId(), apiKey.getId());

    // SET if key doesn't exist (first request in window)
    Boolean initialized = redisTemplate.opsForValue()
        .setIfAbsent(tokensKey, String.valueOf(limit - 1),
                     Duration.ofSeconds(windowSeconds));

    if (initialized) {
        // This is the first request in this window
        return new RateLimitResult(true, limit, limit - 1, resetAt);
    }

    // Decrement remaining count
    Long remaining = redisTemplate.opsForValue().decrement(tokensKey);
    return new RateLimitResult(
        remaining >= 0, limit, Math.max(remaining, 0), resetAt);
}
```

**Why Redis?** Rather than in-process:
- Rate limit state is **shared** across all instances of vault-server (if horizontally scaled)
- Atomic `DECR` prevents race conditions
- TTL-based key cleanup is automatic
- Sub-millisecond latency

**Race condition note:** Two concurrent requests could both `setIfAbsent` fail (seeing each other's key) and both `decrement` — but `decrement` is atomic, so the count never goes below 0. Worst case: slightly more requests than limit get through in the first window, but never below 0 remaining.

### 7.6 Failure Modes

| Failure | What Happens | Impact |
|---|---|---|
| vault-server down | VaultClient returns `failure("vault-server unreachable")` | All requests get 401 (unless cached or JWKS local-verify mode with skip-revocation-check) |
| Redis down | Server-side login/logout fails | AuthService throws DataAccessException, tokens cannot be blacklisted |
| Postgres down | API key validation, audit logging fail | Server returns 500 on affected endpoints |
| Network partition between SDK and server | VaultClient timeout (2s connect, 5s read) | Brief 401 spike, then cached tokens work |
| Audit queue full | Events dropped (WARN log) | Missing audit entries (acceptable) |

**Mitigation strategies:**
- Caffeine cache on the SDK keeps valid tokens working for up to 30s even if server is unreachable
- JWKS local-verify mode can be configured to skip remote revocation checks, making validation entirely local once JWKS is cached
- VaultClient never throws — always returns `ValidateResponse.failure()` — so filters have a single code path

---

## 8. Database Schema

### 8.1 `api_keys` table

```sql
CREATE TABLE api_keys (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    key_hash    VARCHAR(255) NOT NULL UNIQUE,  -- BCrypt hash of raw key
    key_prefix  VARCHAR(10)  NOT NULL,         -- first 10 chars "vault_aB3..."
    name        VARCHAR(100) NOT NULL,         -- human-readable label
    tenant_id   VARCHAR(255) NOT NULL,
    created_by  VARCHAR(255) NOT NULL,
    scopes      VARCHAR(255) NOT NULL,         -- comma-separated "READ,WRITE"
    expires_at  TIMESTAMP,                     -- nullable (never expires)
    last_used_at TIMESTAMP,                    -- nullable
    revoked     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_api_keys_hash ON api_keys (key_hash);
CREATE INDEX idx_api_keys_tenant ON api_keys (tenant_id);
```

### 8.2 `audit_logs` table

```sql
CREATE TABLE audit_logs (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    VARCHAR(255),
    user_id      VARCHAR(255),
    action       VARCHAR(100) NOT NULL,  -- "LOGIN", "API_KEY_CREATED", etc.
    resource     VARCHAR(200),           -- endpoint path
    ip_address   VARCHAR(45),            -- IPv4 or IPv6
    status       VARCHAR(20)  NOT NULL,  -- "SUCCESS", "FAILURE", "BLOCKED"
    metadata     JSONB,                  -- arbitrary JSON for extensibility
    occurred_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_tenant ON audit_logs (tenant_id, occurred_at DESC);
```

### 8.3 API Key Generation Flow

```
1. User requests POST /api-keys (name, scopes, expiresAt)
2. Server generates 32 random bytes → rawKey
3. Prefix rawKey with "vault_" → "vault_aB3x..."
4. BCrypt hash of rawKey → keyHash
5. Store { keyHash, keyPrefix("vault_aB3x..."), name, tenantId, ... }
6. Return rawKey to user (ONCE — never stored again)
7. User receives: "vault_aB3x...Z9kLmN2pQrStUvWxYz"
   └─ first 10 chars shown in UI for identification
   └─ full key can only be seen at creation time
```

---

## 9. API Reference

### 9.1 Vault Server Endpoints

**Public (no auth):**
| Method | Path | Purpose |
|---|---|---|
| POST | `/auth/login` | Email + password → `AuthResponse` (access + refresh JWT) |
| POST | `/auth/refresh` | Refresh token → new access token |
| POST | `/auth/logout` | Bearer token → blacklist it |
| POST | `/auth/validate-registration` | Check email availability |
| POST | `/auth/hash-password` | Return BCrypt hash (for client-side pre-hashing) |
| GET | `/auth/me` | Current user info from Bearer token |
| GET | `/.well-known/jwks.json` | RSA public key in JWK format |
| GET | `/actuator/health` | Health check |

**Internal (X-Service-Key auth):**
| Method | Path | Purpose |
|---|---|---|
| POST | `/internal/validate` | Validate JWT or API key → identity |
| POST | `/internal/audit` | Record audit event (returns 202) |

**Protected (JWT auth):**
| Method | Path | Required Role |
|---|---|---|
| POST | `/api-keys` | ADMIN or TENANT_ADMIN |
| GET | `/api-keys` | Any authenticated (own keys) |
| DELETE | `/api-keys/{id}` | Owner or admin |
| GET | `/admin/api-keys` | ADMIN only |

### 9.2 Demo Service Endpoints

| Method | Path | Auth |
|---|---|---|
| GET | `/public/ping` | None |
| GET | `/protected/profile` | VaultPrincipal |
| GET | `/protected/data` | `orders:read` scope |
| POST | `/protected/data` | `orders:write` scope |
| GET | `/admin/stats` | ROLE_ADMIN |

---

## 10. Key Design Decisions

### 10.1 Decorator Pattern for Validation

**What:** `TokenValidator` is an interface. `VaultClient` (base) → `CachingVaultClient` (cache decorator) → `JwksTokenValidator` (local-signature decorator) compose at startup.

**Why:** Each concern (remote call, caching, local verify) is isolated. Stacking is configured via properties without code changes. Testing each layer independently is trivial (mock the delegate).

### 10.2 Error Collapsing at Boundaries

**What:** `VaultClient.validate()` never throws — always returns `ValidateResponse.failure()`.

```java
catch (ResourceAccessException e) {
    return ValidateResponse.failure("vault-server unreachable");
}
```

**Why:** Auth filter has one branch: `if (!result.valid()) → 401`. No try-catch in the filter for transport errors. Simpler code, fewer bugs. "Fail closed" — if you can't reach the auth server, deny access.

### 10.3 Async Audit with Bounded Queue

**What:** SDK's `VaultAuditClient` uses `LinkedBlockingQueue.offer()` (non-blocking) + single daemon worker + drop-on-full.

**Why:** Audit must never block the request thread. Bounded queue prevents memory exhaustion. Dropping events is better than making the user wait. This is a common pattern in high-throughput systems (similar to how telemetry SDKs work).

### 10.4 Asymmetric Cache TTL

**What:** Valid tokens cached for 30s, invalid tokens for 5s, transport failures not cached at all.

**Why:** Revocation must be visible within reasonable time. A bad token being valid for 5 more seconds is acceptable. A good token being marked bad for 30 seconds would cause user-facing failures. Transport failures are transient — caching them would cause outages to self-perpetuate.

### 10.5 Ephemeral RSA Keys (v0.2.0)

**What:** RSA-2048 keypair generated at startup, stored only in memory.

**Why (trade-off):** Simpler startup, no keystore configuration needed. Knowingly broken — the warning log is explicit. Planned for v0.2.1 with keystore-backed keys.

### 10.6 SHA-256 Hashing for Blacklist Keys

**What:** JWT's SHA-256 hash used as Redis key, not raw token.

**Why:** Fixed key length (43 chars vs 500+). Prevents token content leakage into Redis logs. Still a one-way mapping so revocation checking is O(1).

### 10.7 Interface-Based User Repository

**What:** `VaultUserRepository` is an interface. `StubVaultUserRepository` exists for testing with `@Profile("stub")`.

**Why:** The real database schema for user management is out of scope for this project. Consumers bring their own user table. The interface + stub pattern shows how to integrate without dictating the schema.

---

## 11. Security Considerations

### 11.1 JWT Security

- **RS256** over HS256 — verification key is public, no shared secret across services
- **Required claims** — vaultId, email, tenantId, role, type — all validated at parse time
- **Token type separation** — ACCESS tokens can't be used as refresh tokens and vice versa
- **Short TTL** — 15 minutes for access tokens limits exposure window
- **Issuer/audience validation** — available in JWKS mode (optional, not yet implemented in base mode)

### 11.2 Token Blacklist

- Logout adds token to Redis with TTL = remaining token validity
- Querying the blacklist on EVERY request prevents replay of logged-out tokens
- TTL-based expiry means Redis is self-cleaning — no garbage collection needed
- **Risk:** If Redis is down, `isBlacklisted()` returns false — token considered valid (fail-open on blacklist). Mitigated by Redis being in-memory and highly available.

### 11.3 API Key Security

- Raw keys are BCrypt-hashed (strength 12) before storage — takes ~200ms per hash
- Key is returned exactly once at creation time (cannot be retrieved later)
- `key_prefix` (first 10 chars) allows identification without exposing the full key
- Rate limiting per key prevents brute force and abuse
- Expiry and revocation are supported

### 11.4 Service Key Authentication

- `X-Service-Key` header authenticates SDK→Server calls
- Validated with **constant-time comparison** (`MessageDigest.isEqual()`) to prevent timing attacks
- Configured via environment variable (`VAULT_INTERNAL_SERVICE_KEY`)

### 11.5 Path Traversal Defense

```java
private static boolean hasSuspiciousSegment(String path) {
    if (path.contains("..")) return true;
    String lower = path.toLowerCase(Locale.ROOT);
    return lower.contains("%2e") || lower.contains("%2f") || lower.contains("\\");
}
```

Defense-in-depth against misconfigured reverse proxies or containers normalizing URLs differently than expected.

### 11.6 HTTPS Enforcement

```java
private static void requireSecureUrl(String propertyName, String url, boolean allowInsecureHttp) {
    // Only https:// allowed by default
    // http:// only allowed if vault.client.allow-insecure-http=true
}
```

Service keys are sensitive credentials — they must not travel over cleartext in production.

### 11.7 VaultPrincipal.toString() Safety

```java
// VaultPrincipal.java
public String toString() {
    return "VaultPrincipal{userId=" + mask(userId) + ", tenantId=[PROTECTED], ...}";
}
```

Prevents accidental credential logging. You don't want `log.info("User: {}", principal)` to leak tenant IDs into log aggregation systems.

---

## 12. Testing Strategy

### 12.1 Unit Tests

| Test Class | What It Tests | Technique |
|---|---|---|
| `VaultClientTest` | HTTP calls, error handling | `MockRestServiceServer` |
| `CachingVaultClientTest` | Cache hits/misses, TTL, transport failure bypass | Mock delegate + controlled timing |
| `JwksTokenValidatorTest` | Signature verify, delegation for API keys | Mock delegate + real crypto |
| `VaultAuthFilterTest` | Public path bypass, auth flow, path traversal | Mock validator + `MockHttpServletRequest` |
| `VaultAuditClientTest` | Event delivery, overflow drops | Mock server |
| `VaultPrincipalTest` | toString() masking | Direct assertion |
| `VaultAutoConfigurationTest` | Conditional activation, property bindings | Spring context runner |

### 12.2 Functional Tests

| Test Class | Infrastructure |
|---|---|
| `CachingFunctionalTest` | `FakeVaultServer` (JDK HttpServer on ephemeral port) |
| `JwksLocalVerifyFunctionalTest` | `FakeVaultServer` + real RSA keypair |
| `VaultAuditClientFunctionalTest` | `FakeVaultServer` |
| `VaultAuthFilterFunctionalTest` | `FakeVaultServer` |

**`FakeVaultServer`** is a lightweight JDK `HttpServer` that simulates vault-server responses. No Spring context needed — tests start fast.

**`JwtTestSigner`** generates real RSA-2048 keypairs and signs JWTs with nimbus-jose-jwt for end-to-end testing.

### 12.3 Why This Testing Approach?

- **No database needed** — tests mock or simulate the server
- **Fast** — unit tests run in milliseconds, functional tests in ~100ms
- **Independent** — no Docker, no external services
- **High coverage** — both happy paths and failure modes tested

---

## 13. Interview Q&A

### 13.1 Architecture & Design

**Q: Why a client-server design instead of just a library?**
> The in-process approach (v0.1.x) forced every consumer app to run PostgreSQL, Redis, Flyway migrations, and hold the JWT signing key. With N microservices, that's N copies of everything. The client-server design centralizes state, simplifies operations (one deployment instead of N), and decouples security from business logic. The SDK is ~5 classes with zero database dependencies.

**Q: What happens when vault-server is down?**
> If the Caffeine cache is enabled, previously validated tokens remain valid for up to 30 seconds. Unauthenticated requests get a 401. In JWKS local-verify mode with skip-remote-revocation-check=true, tokens are validated entirely locally and the server going down has no immediate effect. The audit client also suffers no impact — events queue in memory and drain when the server returns.

**Q: Why the decorator pattern for TokenValidator?**
> Each concern (remote call, caching, local signature verification) is isolated in its own class. They implement the same interface, so they compose via constructor injection at startup. Testing each layer is trivial — mock the delegate. You can enable/disable layers via properties without touching code.

**Q: How would you scale vault-server for high traffic?**
> vault-server is stateless from the SDK's perspective — any number of instances can run behind a load balancer. The stateful backends (Postgres, Redis) handle the load. Rate limiting uses Redis atomic operations, which are consistent across instances. JWKS keys must either be shared (persistent keystore) or, for the ephemeral v0.2.0 approach, sticky sessions would be required (not ideal; persistent keys fix this).

**Q: What would you do differently if you were redesigning v0.2.0?**
> Two things: persistent keystore from day one (ephemeral keys were a short-sighted shortcut), and a proper event bus for audit instead of fire-and-forget HTTP. The bounded queue approach is acceptable for an MVP, but production systems should use Kafka or similar for guaranteed audit delivery.

**Q: How would you add support for OAuth2 / OpenID Connect?**
> The architecture supports this naturally. vault-server would become an OAuth2 authorization server issuing access tokens in standard JWT format. The SDK's `JwksTokenValidator` already verifies RS256 signatures — it would just need to accept standard claims (`sub`, `aud`, `iss`) in addition to custom vault claims. The `/internal/validate` endpoint would treat standard JWTs the same way.

### 13.2 JWT & Authentication

**Q: Explain how JWT works in this system from login to verified request.**
> 1. User POSTs email+password to `/auth/login`. Server validates against user repository, generates RSA-2048 keypair at startup (ephemeral in v0.2.0), signs a JWT with RS256 containing vaultId, email, tenantId, role, and token type as custom claims. Access token TTL: 15 minutes. Also issues a refresh token (7 days).
> 2. Consumer app sends Bearer token to protected endpoints. VaultAuthFilter extracts it, calls TokenValidator stack.
> 3. Default mode: VaultClient POSTs to `/internal/validate` with X-Service-Key. Server parses JWT, verifies RS256 signature with public key, checks Redis blacklist, validates required claims, returns user identity.
> 4. Success → `VaultAuthenticationToken` on SecurityContext. Controllers use `@AuthenticationPrincipal VaultPrincipal` or `@PreAuthorize`.

**Q: Why RS256 over HS256?**
> RS256 uses a public/private keypair. vault-server signs with the private key. The SDK verifies with the public key (published via `/.well-known/jwks.json`). If someone decompiles the SDK, they get the public key — which can only verify, not forge. With HS256, the shared secret in the SDK would allow forging tokens. RS256 also enables key rotation by publishing a new key to the JWKS endpoint while keeping the old one cached.

**Q: How is token revocation implemented?**
> When a user logs out, the server computes SHA-256 hash of the JWT and stores it in Redis with TTL = remaining token validity of the JWT (`blacklist:<sha256> = "true"`). On every validation request, the server checks if the hash exists in Redis. If yes, the token is rejected. The entry auto-expires when the token would have expired anyway, keeping Redis clean.

**Q: Why SHA-256 hash of the token as the Redis key?**
> Three reasons: 1) Fixed key length (43 chars vs 500+ for a full RS256 JWT) saves Redis memory. 2) Hashing prevents leaking token contents if Redis keyspace is logged or monitored. 3) Still a one-way deterministic mapping so lookup is O(1).

**Q: What if Redis goes down — can users still log out?**
> The `isBlacklisted()` method would return false if Redis connection fails (the `hasKey` call returns null). The token would be considered valid. This is a fail-open scenario for blacklist checking. For production, Redis should be deployed with high availability (sentinel or cluster). There's a trade-off here: failing closed would block all requests if Redis is down, which is worse.

**Q: How are refresh tokens handled?**
> Refresh tokens are also RS256-signed JWTs but with `"type": "REFRESH"` claim and 7-day TTL. When the client sends a refresh token to `/auth/refresh`, the server validates it (same RS256 verification, same blacklist check) but additionally checks `type == REFRESH`. If valid, it issues a new access token. Current version does not implement refresh token rotation (issuing a new refresh token and invalidating the old one) — that's a potential improvement.

### 13.3 Spring Security

**Q: How does VaultAuthFilter integrate with Spring Security?**
> It extends `OncePerRequestFilter` and is registered via `http.addFilterBefore(vaultAuthFilter, UsernamePasswordAuthenticationFilter.class)`. This places it early in the Spring Security filter chain. It extracts the token, validates it, and if valid, creates a `VaultAuthenticationToken` and sets it on `SecurityContextHolder`. The `finally` block clears the context to prevent thread reuse issues.

**Q: How does @PreAuthorize work with VaultPrincipal?**
> `VaultAuthenticationToken` extends `AbstractAuthenticationToken` and creates GrantedAuthority entries: one for the role (prefixed with `ROLE_`), and one for each scope. This means `@PreAuthorize("hasRole('ADMIN')")` works naturally, as does `@PreAuthorize("hasAuthority('orders:write')")`. The token stores a `VaultPrincipal` record that can be injected with `@AuthenticationPrincipal`.

**Q: Why disable `UserDetailsServiceAutoConfiguration` in vault-server?**
> vault-server doesn't use Spring Security's standard `UserDetailsService` pattern for authentication — it has its own `AuthService` and `JwtService`. The auto-configuration would expect a `UserDetailsService` bean and potentially override the custom setup. Excluding it prevents that conflict.

**Q: What's the difference between the SDK's VaultAuthFilter and the server's JwtAuthFilter?**
> The SDK's filter is for CONSUMER apps — it validates tokens by calling vault-server remotely. The server's JwtAuthFilter is for vault-server itself — it protects `/api-keys` and `/admin/**` endpoints using the server's own JWT validation (local, no HTTP call). Both do similar things but at different levels: one uses `TokenValidator` (HTTP delegate), the other uses `JwtService` (direct).

### 13.4 Database & Caching

**Q: Why JSONB for audit metadata?**
> JSONB in PostgreSQL provides schema flexibility — we can add arbitrary metadata to audit events without migrations. It also supports indexing (GIN) for querying specific metadata fields. The schema says "jsonb" in Flyway, and the metadata string is parsed as JSON on write. This is a good pattern for extensible audit systems.

**Q: Why BCrypt for API key hashing instead of SHA-256?**
> API keys are long-lived secrets (months/years). If the key_hash column is leaked (SQL injection, backup exposure), BCrypt prevents offline brute-forcing because each attempt takes ~200ms (strength 12). SHA-256 can be computed at billions of hashes per second — a 128-bit key would be brute-forced too quickly with ASIC/GPU hardware.

**Q: How does the Caffeine cache improve performance?**
> The cache stores recent `ValidateResponse` objects keyed by token string. A cache hit takes microseconds instead of a network round trip (~1-5ms). With a 30s TTL for positive responses, most requests see the cache, reducing load on vault-server and latency for the end user. The negative cache (5s TTL) prevents rapid retries with invalid tokens from hammering the server.

**Q: Why does the cache have different TTLs for positive vs negative responses?**
> Valid tokens being briefly treated as invalid (false negative) would cause user-facing 401 errors that would need retry — bad UX. Invalid tokens being briefly treated as valid (false positive) is acceptable because the TTL is short (5s) and the worst case is one extra request with a stale bad token. Asymmetric TTL optimizes for user experience.

### 13.5 Distributed Systems

**Q: How does the system handle network partitions?**
> In a partition, the SDK can't reach vault-server. Default behavior: `VaultClient` returns `failure("vault-server unreachable")` → all requests get 401. With caching enabled, previously valid tokens work for up to 30s. With JWKS local-verify + skip-remote-revocation-check, tokens work until natural expiry (15 minutes). This is intentional — we choose to deny rather than allow when uncertain (fail-closed for auth).

**Q: Is the rate limiter accurate under high concurrency?**
> The Redis `DECR` operation is atomic, so the count is always accurate. However, there's a race: two concurrent requests in an empty window both see `setIfAbsent` return false (because the other just set it), then both `decrement`. This means the first window might see limit+1 requests before the count stabilizes. For most APIs, this is acceptable (a few extra requests). For strict limits, a Redis Lua script would provide atomic initialization + consumption.

**Q: How would you deploy vault-server in production?**
> Behind a load balancer (e.g., ALB). Multiple instances for redundancy. Postgres with replication. Redis with sentinel or cluster. Environment variables for configuration (no hardcoded secrets). Health check at `/actuator/health`. JWKS keys loaded from a shared keystore (once v0.2.1 ships). The service key (`VAULT_INTERNAL_SERVICE_KEY`) managed via secrets manager.

**Q: What's the biggest bottleneck in this system?**
> Currently, every validation goes through vault-server (in default mode). For a system handling 10,000 req/s, that's 10,000 calls to `/internal/validate`. Mitigations: Caffeine cache (reduces calls), JWKS local verify (eliminates them entirely for valid tokens with skip-revocation-check). The bottleneck shifts from vault-server to the downstream state stores (Postgres for API keys, Redis for blacklist/rate limit).

### 13.6 Testing

**Q: How do you test the SDK without a running vault-server?**
> Two approaches: 1) Unit tests with `MockRestServiceServer` — intercepts HTTP calls at the RestClient layer, returns canned responses. 2) Functional tests with `FakeVaultServer` — a lightweight JDK `HttpServer` on an ephemeral port that simulates vault-server behavior. Both approaches avoid needing Docker, Postgres, or Redis for SDK tests.

**Q: How is asynchronous audit behavior tested?**
> `VaultAuditClientFunctionalTest` starts a `FakeVaultServer`, creates a `VaultAuditClient`, records events, waits for the background worker to drain the queue, and verifies the server received the events. Also tests overflow by recording more events than queue capacity and verifying the WARN log. The `close()` method is tested by verifying graceful drain on shutdown.

**Q: What test coverage would you expect for this project?**
> Core paths: validation pipeline (every combination of caching/JWKS/remote), filter behavior (public paths, missing tokens, valid/invalid tokens, path traversal), audit (happy path, overflow, server errors), rate limiting (concurrent access first/last in window). Integration tests for key flows (login → use token → logout). At minimum: 80%+ line coverage on vault-sdk, 70%+ on vault-server.

### 13.7 General Java / Spring

**Q: What Java 21 features are used in this project?**
> Records (all DTOs — `ValidateRequest`, `ValidateResponse`, `VaultPrincipal`, etc.) for concise immutable data carriers. Pattern matching in `requireClaim` (`instanceof String value`). `StringTemplate` is not used since it was preview in Java 21 and the project targets stable APIs. Virtual threads (Project Loom) would be a natural future improvement for the async audit worker.

**Q: Why constructor injection instead of @Autowired?**
> Immutable beans (fields are `final`), explicit dependencies, easier testing (no reflection), clear compile-time safety. It's the recommended approach in modern Spring.

**Q: Explain the auto-configuration in vault-sdk. How does it decide what to wire?**
> `VaultAutoConfiguration` is gated by `@ConditionalOnProperty(prefix = "vault.client", name = "base-url")` — it only activates when the consumer configures `vault.client.base-url`. It wires the validation pipeline based on other properties: cache is enabled via `vault.client.cache.enabled`, JWKS via `vault.client.jwks.uri`, audit via `vault.audit.enabled`. Each bean uses `@ConditionalOnMissingBean` so consumers can override any piece.

**Q: What does `@EnableMethodSecurity` do?**
> It replaces the older `@EnableGlobalMethodSecurity` and integrates with Spring Security's authorization manager pattern. It enables `@PreAuthorize`, `@PostAuthorize`, `@PreFilter`, `@PostFilter` annotations on beans. Combined with `VaultAuthenticationToken` having proper GrantedAuthority entries, it allows role and scope-based access control with minimal code.

### 13.8 Career / General

**Q: What's the most important lesson you learned building this?**
> That an in-process library vs client-server architecture is not just a technical choice — it's an operational one. The v0.1.x approach was technically simpler but made operations harder (N databases, N migrations, N key deployments). Sometimes adding network complexity simplifies everything else. Also: always plan for key management from day one.

**Q: What would you add next?**
> Persistent keystore (v0.2.1 priority). OAuth2/OIDC support. Redis for cache mode (shared across SDK instances). Refresh token rotation. Webhook-based audit delivery as alternative to HTTP poll. Admin UI for API key management. Rate limiting on JWT-based requests (currently only on API keys). Prometheus metrics export for the validation pipeline.

**Q: How would you explain this project to a non-technical interviewer?**
> "It's like a bouncer for a building with many doors. Instead of hiring a bouncer for every door (which would mean each microservice manages its own security), we have one central security desk. All doors (microservices) call the desk to check IDs (tokens). The desk decides who gets in, keeps a log of everyone who entered, and can stop someone if they cause trouble (rate limiting)."

---

## 14. Code Snippets to Memorize

### 14.1 Validation Pipeline Composition

```java
// VaultAutoConfiguration.vaultTokenValidator()
TokenValidator validator = new VaultClient(vaultRestClient, clientProperties.serviceKey());
if (cacheProperties.enabled()) {
    validator = new CachingVaultClient(validator, cacheProperties);
}
if (jwksProperties.uri() != null) {
    validator = new JwksTokenValidator(..., validator, ...);
}
return validator;
```

### 14.2 VaultAuthFilter Core Logic

```java
// doFilterInternal
if (isPublic(request)) { chain.doFilter(request, response); return; }

String apiKey = request.getHeader(properties.apiKeyHeader());
if (hasText(apiKey)) { authenticate(apiKey, API_KEY, request, response, chain); return; }

String bearer = extractBearer(request);
if (hasText(bearer)) { authenticate(bearer, JWT, request, response, chain); return; }

response.sendError(SC_UNAUTHORIZED, "Missing authentication");
```

### 14.3 JWT Signing

```java
return Jwts.builder()
    .header().keyId(keyId).and()
    .claims(Map.of("vaultId", id, "email", email, "tenantId", tenant, "role", role, "type", type))
    .subject(vaultId)
    .issuedAt(Date.from(now))
    .expiration(Date.from(expiresAt))
    .signWith(privateKey, Jwts.SIG.RS256)
    .compact();
```

### 14.4 Token Blacklist (SHA-256 Key)

```java
private String blacklistKey(String token) {
    return "blacklist:" + sha256Base64Url(token);
}

private String sha256Base64Url(String token) {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
    return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
}
```

### 14.5 Rate Limiting (Redis Decrement)

```java
Boolean initialized = redisTemplate.opsForValue()
    .setIfAbsent(tokensKey, String.valueOf(limit - 1), Duration.ofSeconds(windowSeconds));

if (Boolean.TRUE.equals(initialized)) {
    return new RateLimitResult(true, limit, limit - 1, resetAt);
}

Long remaining = redisTemplate.opsForValue().decrement(tokensKey);
return new RateLimitResult(remaining >= 0, limit, Math.max(remaining, 0), resetAt);
```

### 14.6 Constant-Time Service Key Comparison

```java
private boolean constantTimeEquals(String expected, String actual) {
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8),
        actual.getBytes(StandardCharsets.UTF_8)
    );
}
```

### 14.7 Async Audit Queue Drain

```java
private void drain() {
    while (running.get() || !queue.isEmpty()) {
        AuditRequest event = queue.poll(500, TimeUnit.MILLISECONDS);
        if (event == null) continue;
        send(event);
    }
}

public void record(AuditRequest event) {
    if (!queue.offer(event)) {
        log.warn("Audit queue full, dropping event action={}", event.action());
    }
}
```

### 14.8 Path Traversal Defense

```java
private static boolean hasSuspiciousSegment(String path) {
    if (path.contains("..")) return true;
    String lower = path.toLowerCase(Locale.ROOT);
    return lower.contains("%2e") || lower.contains("%2f") || lower.contains("\\");
}
```

---

## Quick Reference: Key Terminology

| Term | Meaning in This Project |
|---|---|
| Service Key | Shared secret authenticating SDK→Server calls (`X-Service-Key` header) |
| VaultPrincipal | Immutable record holding userId, tenantId, role, scopes after auth |
| VaultAuthenticationToken | Spring Security `Authentication` wrapping a `VaultPrincipal` |
| TokenValidator | Interface for validation pipeline (decorator pattern base) |
| VaultClient | HTTP client that POSTs to `/internal/validate` |
| CachingVaultClient | Caffeine cache decorator around TokenValidator |
| JwksTokenValidator | Local RS256 signature verifier using fetched public key |
| VaultAuditClient | Async fire-and-forget audit dispatcher with bounded queue |
| Internal endpoints | Server endpoints for SDK consumption, authenticated by service key |
| Access token | Short-lived (15min) JWT for request authentication |
| Refresh token | Long-lived (7d) JWT for obtaining new access tokens |
