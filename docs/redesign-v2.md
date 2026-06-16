# Vault v0.2.0 redesign

This document describes the v0.2.0 redesign of theVaultOfficial. It is the authoritative source for the target architecture; the README and `architecture.md` are written against this design.

## Background

v0.1.x was published to Maven Central as `io.github.hesandaliyanage:vault-sdk`. It was labelled an "embedded SDK" but in practice it was a Spring Boot starter: every consumer app ran its own JWT signing, API-key store, audit logger, and rate limiter in-process, backed by Postgres + Redis + Flyway. That mismatch between the "SDK" label and the in-process starter behaviour was the central piece of feedback received after release. A second piece of feedback was that the architecture doc described a client/server model that did not exist in code (`vault-server` was on disk but was not listed in the parent pom's `<modules>` and did not build with the reactor).

v0.2.0 picks one identity — **real client SDK against a standalone vault-server** — and aligns the code, docs, and module layout to it.

## Target architecture

```
vault-protocol       Pure-Java DTOs shared on the wire. No Spring, no transitive deps.
vault-server         Spring Boot service. Owns JWT issuance, API keys, audit, rate limit,
                     Postgres, Redis, Flyway. Exposes /internal/validate and /internal/audit
                     guarded by X-Service-Key.
vault-sdk            Thin client. Spring RestClient + auth filter + SecurityContext.
                     Depends only on spring-web, spring-security-web, jackson,
                     and vault-protocol. Optional: caffeine (cache), nimbus-jose-jwt (JWKS).
vault-sdk-legacy     v0.1.x in-process classes, marked @Deprecated. Heavy deps kept here.
                     Scheduled for removal in v0.3.0.
demo-service         Example consumer. Uses vault-sdk to validate against vault-server.
```

## Validation modes

The SDK supports two validation modes, selectable per app:

1. **Remote-only + cache** (default). Every request triggers a call to `/internal/validate` on vault-server, backed by an in-memory Caffeine cache with a short TTL. Zero crypto code in the SDK.
2. **JWKS local verify** (opt-in). vault-server exposes `/.well-known/jwks.json`. The SDK fetches it, verifies JWT signatures locally using nimbus-jose-jwt, and still calls `/internal/validate` for revocation checks unless explicitly skipped.

API-key validation is always remote (there is no local-verify equivalent for opaque keys).

## Audit dispatch

The SDK records audit events asynchronously. A bounded in-memory queue feeds a single worker thread that POSTs to `/internal/audit`. On overflow the oldest event is dropped and the drop is logged at WARN. Request threads never block on audit I/O.

## Wire contract

| Endpoint                          | Auth header        | Purpose                                                        |
|-----------------------------------|--------------------|----------------------------------------------------------------|
| `POST /internal/validate`         | `X-Service-Key`    | Validate a JWT or API key. Returns vault id, tenant, role, scopes. |
| `POST /internal/audit`            | `X-Service-Key`    | Record an audit event (fire-and-forget).                       |
| `GET  /.well-known/jwks.json`     | none               | Public keys for JWKS local-verify mode.                        |
| `POST /auth/register`             | none               | Register a new user (vault-server's own surface).              |
| `POST /auth/login`                | none               | Issue access + refresh tokens.                                 |
| `POST /auth/refresh`              | none               | Exchange a refresh token for a new access token.               |
| `POST /auth/logout`               | Bearer             | Blacklist the current access token.                            |

DTOs for the `/internal/*` endpoints live in `vault-protocol` and are shared verbatim by server and SDK. Other endpoints are consumer-facing and serialize their own JSON.

## Release cadence

v0.2.0 ships the full architecture above as a single release because v0.1.x had near-zero adoption and the redesign breaks the public API. From v0.2.0 forward, releases follow a slower ladder:

- v0.2.x — bug fixes only.
- v0.3.0 — remove `vault-sdk-legacy`, split rate-limit and audit into their own optional modules.
- v0.4.0 — mTLS between SDK and server, key rotation tooling.

See `docs/release-cadence.md` for the policy.

## Deprecation policy

Every class moved to `vault-sdk-legacy` carries `@Deprecated(since = "0.2.0", forRemoval = true)` and a javadoc `@see` pointing to its replacement in the new SDK (or to vault-server if the responsibility has moved server-side). The legacy module will be removed in v0.3.0.
