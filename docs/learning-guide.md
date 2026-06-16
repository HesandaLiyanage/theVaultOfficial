# Learning guide — what v0.2.0 actually does, in your own words

This is a walkthrough of what changed from v0.1 (which you wrote yourself) to v0.2 (which is mostly new code), aimed at giving you a real mental model of how the pieces fit together. Read top to bottom; each section assumes the previous ones.

---

## 1. The shift in one paragraph

In v0.1, the SDK *was* the security system. Every consumer app that pulled it in started its own JWT signer, its own API-key table, its own audit log, its own rate limiter, all running in-process. The "SDK" was a misnomer — it was a Spring Boot starter that needed Postgres + Redis + Flyway to function.

In v0.2, the SDK is genuinely a *client*. There is now a separate service called `vault-server` that owns all of that — issues JWTs, validates them, stores API keys, writes audit logs. The SDK's only job is to make HTTP calls to that server. The consumer app no longer runs JWT logic; it asks vault-server "is this token valid?" and gets a yes/no with the user's identity attached.

That's the whole shift. Everything else in this guide is the consequence of that shift.

---

## 2. The five modules and why each exists

Before v0.2 there was one module (`vault-sdk`) plus an orphaned `vault-server/` that didn't even build. Now there are five:

### `vault-protocol`
A pure-Java jar with four record types: `ValidateRequest`, `ValidateResponse`, `AuditRequest`, `TokenType`. That's it. No Spring, no jackson, no JJWT — only the spec jar `jakarta.validation-api` at `provided` scope so the records can carry `@NotBlank` annotations.

**Why it exists:** v0.1's architecture doc described `/internal/validate` and the code didn't match it. The reason was that there was no single place where "this is the wire format" was written down. If you put the DTOs in `vault-server`, the SDK has to redeclare them and they drift. If you put them in `vault-sdk`, the server has to declare them and they drift. The solution is a third module both modules depend on. That's all this is — a sanity anchor.

### `vault-server`
The standalone Spring Boot service. It's the only thing that runs Postgres queries, the only thing that signs JWTs, the only thing that issues API keys. If you delete every consumer app from existence, vault-server still works on its own — that's the test of whether you actually have a "server."

**Why it exists separately:** because security state (token blacklist, API key store, audit log, user table) is shared infrastructure. If every consumer app runs its own copy you can't revoke a token, you can't see audits across services, and you can't enforce a rate limit across services. Centralizing it is the entire point.

### `vault-sdk`
What consumer apps depend on. Five public classes worth of code (filter, principal, two clients, autoconfig) plus four config records.

**Why it's so small:** because almost nothing happens here anymore. The JWT signing, key storage, blacklist, audit DB — all of that moved to vault-server. The SDK just translates "HTTP request with a Bearer token" into "Spring `SecurityContextHolder` populated with the user's identity," via one HTTP call to vault-server in the middle.

### `vault-sdk-legacy`
Every class from v0.1's `vault-sdk` lives here, marked `@Deprecated(since = "0.2.0", forRemoval = true)`. The whole module is doomed — it goes away in v0.3.0.

**Why it exists:** the v0.1.x users (essentially you, but on principle) shouldn't have their build break the day they pull 0.2.0. They can switch their dependency from `vault-sdk` to `vault-sdk-legacy` and keep working for one release while migrating. It's a graveyard with a calendar entry.

### `demo-service`
An example consumer. It does nothing security-relevant on its own — it just shows the integration: pull in `vault-sdk`, add the filter to your security chain, read `VaultPrincipal` in your controllers. Same as any real consumer app would do.

---

## 3. Trace one request through the SDK

This is the most important section. Once you can mentally run a request through the code, the rest falls out.

Say a client sends `GET /orders` with `Authorization: Bearer eyJ...` to a consumer app that uses vault-sdk.

```
HTTP request hits Tomcat
    ↓
Spring's filter chain runs in order; VaultAuthFilter is one of them
    ↓
VaultAuthFilter.doFilterInternal()           ← vault-sdk/.../VaultAuthFilter.java
    ↓
Step A: is the path public?
    For each pattern in vault.filter.public-paths, AntPathMatcher.match(pattern, requestUri).
    If yes, chain.doFilter and return. We're done.
    ↓ (no)
Step B: is there an X-API-Key header?
    If yes, call validator.validate(apiKey, TokenType.API_KEY).
    ↓ (no)
Step C: is there an Authorization: Bearer header?
    Extract the token after "Bearer ". If empty, sendError(401).
    Call validator.validate(token, TokenType.JWT).
    ↓
The validator is some TokenValidator. Could be:
    - VaultClient (default — every request hits vault-server)
    - CachingVaultClient wrapping VaultClient (Caffeine cache in front)
    - JwksTokenValidator wrapping either (verify signature locally first)
    The autoconfig composes these based on what you set in application.yml.
    The filter doesn't know or care which one it has.
    ↓
validator.validate returns a ValidateResponse.
    valid=true → build VaultPrincipal, wrap in VaultAuthenticationToken,
                 SecurityContextHolder.getContext().setAuthentication(...).
    valid=false → SecurityContextHolder.clearContext(), sendError(401).
    ↓
chain.doFilter(request, response)
    ↓
Spring routes to your @RestController.
Your controller method runs.
@PreAuthorize annotations check ROLE_ and authority claims against the
SecurityContext the filter populated.
@AuthenticationPrincipal VaultPrincipal binds to the principal you set.
    ↓
controller returns
    ↓
finally { SecurityContextHolder.clearContext() }   ← prevents leak across thread reuse
    ↓
HTTP response goes back
```

That's the whole flow. Two things to internalize:

1. **The filter is the gate.** Spring's `authorizeHttpRequests` in your `SecurityConfig` is set to `permitAll()` because *the filter* decides who's authenticated. If you also added `auth.anyRequest().authenticated()` Spring would re-check at its `AuthorizationFilter` step — which is fine if the filter populated the SecurityContext, but redundant. We do the simpler thing: filter says yes/no, that's the truth.

2. **`@PreAuthorize` is doing fine-grained checks, not auth.** The filter has already authenticated by the time `@PreAuthorize` runs. `@PreAuthorize("hasRole('ADMIN')")` is asking "does the already-authenticated user have ROLE_ADMIN?" — that comes from `ValidateResponse.role()` which came from vault-server which came from the JWT claim.

---

## 4. The SDK class by class

Now you can read this knowing where each piece lands in the flow above.

### `TokenValidator` (interface)
One method: `ValidateResponse validate(String token, TokenType type)`. The whole point of having this interface is that the filter doesn't know whether the validator behind it is hitting the network, hitting a cache, or verifying a JWT locally. The autoconfig assembles the right chain; the filter sees one thing.

You'll see this kind of "interface with one method, multiple implementations stacked like middleware" pattern a lot in Spring. It's the same shape as `Filter`, `HandlerInterceptor`, `Authentication`, etc.

### `VaultClient`
The class that actually does the HTTP call. Uses Spring's `RestClient` (introduced in Spring 6.1). Posts a `ValidateRequest` to `/internal/validate` with the `X-Service-Key` header.

The most interesting thing about it is what it does with errors. Read `validate()` — it catches `RestClientResponseException` (4xx/5xx) and `ResourceAccessException` (connection refused, timeout). Both turn into `ValidateResponse.failure("...")`. **It doesn't throw.** The reason: the auth filter would otherwise have to know about HTTP errors specifically and translate them to 401. By making `VaultClient` always return a `ValidateResponse`, the filter has one branch: `if (!result.valid()) → 401`. Whether the token was actually bad or the server was on fire is logged but invisible to the filter logic.

This is a pattern worth keeping: at the boundary between "transport" and "business logic," collapse error types into the domain language. The filter speaks "valid or not." HTTP errors are translated into that language at the lowest layer where they make sense.

### `CachingVaultClient`
Wraps a `TokenValidator` (in practice, `VaultClient`) with a Caffeine cache. If the cache has a result for `(token, type)`, return it. Otherwise call the inner validator and store the response, *unless* the response is a transport failure.

That `isCacheable` check is the subtle bit. Read it (`CachingVaultClient.java`):

```java
private boolean isCacheable(ValidateResponse response) {
    if (response.valid()) {
        return true;             // happy path, cache it
    }
    String reason = response.reason();
    return reason != null && !reason.contains("unreachable");
}
```

So a real "this token is invalid" response *is* cached (you don't want bad tokens hammering the server). A "vault-server unreachable" response is *not* cached (you don't want a 30-second outage to mean 30 seconds of all-tokens-rejected after the server comes back). The dividing line is whether the response represents a *decision* by vault-server or a *failure to reach* vault-server.

This is one of those small decisions that doesn't matter in v0.1 but matters a lot in production once you have any real load. Good to internalize.

### `JwksTokenValidator`
The other `TokenValidator`. Verifies the JWT signature locally using vault-server's published public key (from `/.well-known/jwks.json`). Uses the `nimbus-jose-jwt` library, which has built-in JWKS fetching and caching — you give it a URL and it handles the refresh.

It wraps another validator (`remote`). Two modes:

1. `skipRemoteRevocationCheck = false` (default): verify signature locally, *then* call the remote validator anyway for revocation. This is faster than pure remote because if the signature is bad we reject without calling vault-server, but a valid signature still costs you the network hop. Win on garbage tokens, breakeven on real ones.

2. `skipRemoteRevocationCheck = true`: verify signature, build a `ValidateResponse` from the JWT claims, return. No network call. Tokens stay valid until expiry even if they were blacklisted on the server. Use this only when your access-token TTLs are short enough (5–10 minutes) that the revocation window doesn't matter.

API keys always go remote — there's no signature to verify on an opaque key, so JWKS mode is a no-op for them.

### `VaultAuthFilter`
The filter you saw in the request walkthrough. Five things worth pointing out:

- It uses `OncePerRequestFilter`, Spring's base class that ensures the filter only runs once per request even if forwarded internally. Standard pattern.
- The `try { chain.doFilter } finally { SecurityContextHolder.clearContext() }` block is intentional. Tomcat reuses threads. If you don't clear the context, request A's identity can leak into request B because they ran on the same thread. Spring Security does this in its own filter chain but we do it explicitly because we set the authentication in our filter.
- `AntPathMatcher.match` is what does `/public/**` glob matching. Standard Spring utility.
- API-key header is preferred over Bearer if both are present. Arbitrary but consistent — pick one and stick with it.
- Public paths are matched against `request.getRequestURI()`, which is the path without the query string. That's almost always what you want.

### `VaultPrincipal` and `VaultAuthenticationToken`
`VaultPrincipal` is the record that holds identity: `userId`, `tenantId`, `role`, `scopes`. That's literally everything you can know about the caller.

`VaultAuthenticationToken` is the Spring Security `Authentication` implementation. It extends `AbstractAuthenticationToken`, exposes the `VaultPrincipal` as `getPrincipal()`, and crucially maps:

- `role = "ADMIN"` → `ROLE_ADMIN` authority → `@PreAuthorize("hasRole('ADMIN')")` works.
- `scopes = ["orders:read", "orders:write"]` → flat authorities → `@PreAuthorize("hasAuthority('orders:read')")` works.

The `"ROLE_"` prefix on roles is a Spring Security convention; `hasRole('X')` literally looks for an authority named `ROLE_X`. If you forget the prefix, `hasRole` mysteriously fails. We add it in `buildAuthorities`.

### `VaultAuditClient`
The async audit dispatcher. Read its `drain()` method:

```java
while (running.get() || !queue.isEmpty()) {
    AuditRequest event = queue.poll(500, TimeUnit.MILLISECONDS);
    if (event == null) continue;
    send(event);
}
```

A bounded `LinkedBlockingQueue` and a single daemon thread that polls it and posts to `/internal/audit`. Two things worth absorbing:

1. **It's a daemon thread.** That means the JVM will exit even if this thread is still alive — good, audit shouldn't keep the process up. The `close()` method (called by Spring on shutdown because the autoconfig declares `destroyMethod = "close"`) gives it 2 seconds to drain remaining events, then gives up.

2. **`queue.offer` returns false on full.** That's how overflow handling works — `record()` doesn't block, it just drops and logs. The alternative would be `queue.put` (block until space) which would mean a slow audit endpoint could lock up all your request threads. We deliberately chose drop-and-log; audit failures must not affect the user's request.

The whole class is ~80 lines but it's the kind of code where every line is making a decision. Worth re-reading slowly.

### `VaultAutoConfiguration`
This is where everything gets assembled. It's the longest single piece of "business logic" in the SDK — but most of it is `@Bean` declarations with conditionals.

Two things to study:

**The `@ConditionalOnProperty(prefix = "vault.client", name = "base-url")` on the class.** If you don't set that property, the entire auto-config is skipped — no beans, no filter, no nothing. This is what lets people pull `vault-sdk` in transitively (e.g. through another library) without it activating itself. Pretty much every Spring Boot starter does this; it's the polite default.

**The `vaultTokenValidator` method.** This is where the chain gets stacked:

```java
TokenValidator validator = new VaultClient(...);
if (cache.enabled) validator = new CachingVaultClient(validator, ...);
if (jwks.uri != null) validator = new JwksTokenValidator(jwks.uri, validator, ...);
return validator;
```

That's it — the whole choice between "remote only," "remote + cache," "JWKS verify with remote revocation," "JWKS verify with cache fallback for revocation" is just three `if` statements. The validator chain is built once at startup based on what's in `application.yml`.

There was an interesting bug here during development worth remembering. I originally had `VaultClient` as its own bean and the wrapper as a separate `TokenValidator` bean with `@ConditionalOnMissingBean(TokenValidator.class)`. That conditional looks at *all bean types* — and because `VaultClient implements TokenValidator`, Spring saw "there's already a TokenValidator bean" (namely the `VaultClient` one) and skipped creating the wrapper. So the cache and JWKS validators were never actually wired up. Fix was to stop exposing `VaultClient` as a bean — it's now an implementation detail of `vaultTokenValidator`. The lesson: `@ConditionalOnMissingBean(SomeInterface.class)` is sensitive to *any* bean of that type, including ones you registered yourself for unrelated reasons.

---

## 5. The server pieces that matter

Most of `vault-server` is the same code from v0.1 — `ApiKeyService`, `AuditService`, `TokenBlacklistService`, the `/auth/*` controllers. You wrote those. Three pieces changed in v0.2.

### `JwtService` switched from HS256 to RS256
v0.1 signed JWTs with a shared HMAC secret. That works fine when "the thing that signs" and "the thing that verifies" are the same process. But for JWKS, the SDK needs to verify *without* having the signing secret — so we need asymmetric crypto.

So:
- The constructor used to take `@Value("${jwt.secret}") String jwtSecret`. Now it takes nothing crypto-related and generates an RSA 2048 keypair at startup with `KeyPairGenerator`.
- Signing changed from `.signWith(signingKey)` (HMAC) to `.signWith(privateKey, Jwts.SIG.RS256)` (RSA).
- Verification changed from `.verifyWith(signingKey)` to `.verifyWith(publicKey)`.
- We now write a `kid` (key ID) header on every JWT (`.header().keyId(keyId).and()`). The SDK uses this to look up which key in the JWKS to verify against when there's eventually more than one (key rotation). For now there's always exactly one, hardcoded to `"vault-default"`.

The keypair is generated *at startup*. That means when you restart vault-server, all JWTs it previously issued become invalid (their signatures verify against a different public key now). This is a v0.2.0 caveat called out in the warning log line and in the redesign doc — persistent keys are a v0.2.1 task.

### `JwksController` is new
Twenty lines. Reads `JwtService.getPublicKey()` (an `RSAPublicKey`) and serves it as a JWK at `GET /.well-known/jwks.json`. The math is:

- A JWK for an RSA public key has fields `kty=RSA`, `alg=RS256`, `use=sig`, `kid`, `n` (the modulus), `e` (the public exponent).
- `n` and `e` are base64url-encoded big-endian byte strings of the unsigned integer.
- Java's `BigInteger.toByteArray()` returns *signed* two's-complement bytes, which can include a leading zero byte to disambiguate positive from negative. JWK wants the unsigned representation, so we strip the leading zero if present.

That's the entire `base64Url` helper in `JwksController`. If you ever wonder why this kind of code is fiddly, it's because the JWK spec and Java's BigInteger disagree about sign representation.

### `SecurityConfig` learned about `/.well-known/**`
One line added. The JWKS endpoint must be reachable without authentication, because it publishes a *public* key. The whole point is that anyone can fetch it.

---

## 6. The tricky bits, in plain words

A few things that aren't obvious from reading the code:

### Why is Caffeine an optional dependency?
If you don't enable the cache, the `CachingVaultClient` class is never instantiated, which means the Caffeine classes are never loaded by the classloader. Marking the dep `<optional>true</optional>` tells Maven: "ship this dep but don't drag it into consumers transitively." A consumer that doesn't use the cache doesn't even download Caffeine. Same idea for nimbus-jose-jwt and JWKS. It's how you ship features without paying their cost when unused.

The tradeoff: if a consumer *does* enable the cache, they have to add Caffeine to their own pom (or the autoconfig will fail at startup with `ClassNotFoundException`). The error message is clear enough that this is fine in practice.

### Why isn't `VaultClient` a public Spring bean anymore?
See the "interesting bug" callout under `VaultAutoConfiguration`. Short version: exposing `VaultClient` as a bean broke the `@ConditionalOnMissingBean` logic for the higher-level `TokenValidator` bean. Hiding `VaultClient` inside the assembly method made the bean graph correct again. There's no downside — consumers who want to customize the validator override `vaultTokenValidator` as a whole, which is cleaner than overriding pieces of it.

### Why does the audit client use a custom thread instead of `@Async` or a `TaskExecutor`?
Two reasons. First, the lifecycle is cleaner — `close()` on shutdown drains the queue, then exits. `@Async` and `TaskExecutor` rely on Spring's bean shutdown ordering and can lose in-flight events. Second, a single worker thread per VaultAuditClient is exactly the right size — audit isn't parallelizable per-event (no point), and a thread pool would add complexity without value. Sometimes the answer is just "one thread, one queue, one worker."

### Why is `vault-protocol` allowed to depend on `jakarta.validation-api`?
The whole pitch of `vault-protocol` is "no transitive deps." But the records carry `@NotBlank` and `@NotNull` annotations so that vault-server's `@Valid @RequestBody` on the controller actually validates incoming requests. `jakarta.validation-api` is just the spec interfaces — no implementation, ~50KB, no transitive deps of its own. And it's at `<scope>provided</scope>`, meaning consumers don't even pull it; they're expected to have it via Spring already.

If you really wanted zero deps you'd strip the annotations and validate manually in the server. The tradeoff for one tiny provided-scope dep didn't seem worth it.

### Why is the legacy module deprecated at the *package* level via `package-info.java`?
Putting `@Deprecated` on a package gives you a single source of truth — every class in `com.vault.sdk.*` is implicitly part of the deprecated surface. We *also* put `@Deprecated` on the most visible top-level types (autoconfig, filter, properties, security context, user repository) because the package-level annotation doesn't trigger compiler warnings on its own — those need to be on individual classes. Belt and braces.

### Why does the autoconfig wire `SecurityFilterChain` to `permitAll()` in the demo?
Spring Security's `authorizeHttpRequests` is enforced by its own `AuthorizationFilter`, which runs late in the chain. If we said `auth.anyRequest().authenticated()`, that would *also* check whether the SecurityContext has an Authentication when the chain reaches it. Since our `VaultAuthFilter` runs earlier and either populates the context or short-circuits with a 401, the `authenticated()` check would be redundant. `permitAll()` makes the design intent clearer: the SDK filter is the gate, not Spring's authorization layer.

(If a consumer prefers to use Spring's authorization layer for path-based rules — e.g. `requestMatchers("/api/admin/**").hasRole("ADMIN")` — they're free to. It still works. It's just not necessary.)

---

## 7. What's still TODO and why

Three things called out for v0.2.1+:

### Persistent RSA keys
Right now `JwtService` regenerates the keypair at startup, which means tokens don't survive a restart and the SDK's JWKS cache will return stale keys after a restart. Fix: load a keypair from a configured PEM file or JKS keystore, fall back to generation only in dev.

This needs a small chunk of design work: where do we put the key, how do we handle key rotation when we eventually do that (multiple kids in JWKS, oldest stays valid for a grace period). Worth doing properly in v0.2.1 rather than ad-hoc.

### Splitting audit / rate-limit out of `vault-sdk`
The SDK currently ships `VaultAuditClient` in the same jar as the auth filter. A consumer that doesn't care about audit still gets the class on their classpath. It works (the bean isn't created unless `vault.audit.enabled=true`), but it's slightly off-brand for "thin SDK."

v0.3.0 plan: split into `vault-sdk-core` (filter + validator), `vault-sdk-audit`, `vault-sdk-jwks` etc. Then your `pom.xml` only contains the bits you actually use. Wait until the API stabilizes before doing this — splitting too early locks decisions in.

### Public API ergonomics
Things like `VaultPrincipal` exposing `tenantId` as `String` rather than a domain type, or `VaultClient`'s error responses using string-matching on `reason` to detect "unreachable" — these are workable but rough. A v0.3 pass should clean them up before v1.0 freezes the API.

---

## 8. The one-screen summary

If you forget everything else, remember this:

> **vault-sdk is a `RestClient` plus a servlet filter.** The filter pulls a token off the request, asks vault-server "is this valid?", and either populates `SecurityContextHolder` or sends a 401. Everything else — caching, JWKS verification, async audit — is optional wrapping around those two ideas.
>
> **vault-server is a normal Spring Boot app** that signs RS256 JWTs, manages API keys, and exposes `/internal/validate` for the SDK to call.
>
> **vault-protocol is the shared wire format.** It exists so the server's controllers and the SDK's HTTP client can't drift apart.
>
> **vault-sdk-legacy is your old v0.1 code preserved in amber** for one minor release, then it's gone.
>
> **Everything reactive is for performance** (cache, async audit, JWKS local-verify). Everything else is for correctness.

Read the code top-down starting from `VaultAuthFilter.doFilterInternal()` — that's the entry point everything else exists to support. The rest will make sense once you can run that one method in your head.
