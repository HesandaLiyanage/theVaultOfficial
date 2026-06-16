# Release cadence

This document is the release policy for theVaultOfficial. It exists because v0.1.x shipped as one large initial drop — starter, dead server module, demo app, conflicting docs, Maven Central publishing — which was too much surface area for outside reviewers to evaluate usefully. v0.2.0 onward, releases are smaller and more frequent, with a tagged GitHub release for every Central publication.

## Versioning

Standard SemVer: `MAJOR.MINOR.PATCH`. The library is pre-1.0, so MINOR bumps may include breaking changes that would otherwise warrant a MAJOR. This is called out explicitly in each release's notes.

| Bump   | Meaning                                                                                              |
|--------|------------------------------------------------------------------------------------------------------|
| MAJOR  | Reserved for v1.0 (API stability commitment). No MAJOR bumps below that.                            |
| MINOR  | New features. May break the public API while we're pre-1.0; breaks are listed in release notes.     |
| PATCH  | Bug fixes only. No new features, no new properties, no new public methods.                          |

## v0.2.x → v1.0 roadmap

| Version   | Scope                                                                                              |
|-----------|----------------------------------------------------------------------------------------------------|
| **0.2.0** | The redesign. Real client SDK against vault-server. RS256 JWTs + JWKS. Async audit. Caffeine cache. |
| 0.2.x     | Bug fixes only.                                                                                    |
| **0.2.1** | Persistent RSA keypair for vault-server (load from PEM / JKS / env). Removes the "restart invalidates tokens" caveat from 0.2.0. |
| **0.3.0** | Remove `vault-sdk-legacy`. Split `vault-sdk` audit and rate-limit into their own optional modules so consumers can opt out further. |
| **0.4.0** | mTLS between SDK and server. Manual key rotation tooling.                                          |
| **0.5.0** | OpenAPI spec generation for `/auth/*` and `/internal/*`. Optional spring-cloud-gateway integration. |
| **1.0.0** | Public API freeze. Wire-format compatibility commitment.                                           |

This list is a plan, not a contract — items may be reordered or dropped if real usage shows different priorities.

## Per-release checklist

For every Central publication:

1. Branch from `main`, e.g. `release/0.2.0`.
2. Update version in the root `pom.xml` (drop `-SNAPSHOT`).
3. Update each child module's `<version>` block if it pins one explicitly.
4. Update `CHANGELOG.md` (or rely on the release-notes draft on GitHub).
5. Smoke test:
   - `docker compose up -d`
   - `mvn -pl vault-server spring-boot:run` in one terminal
   - `mvn -pl demo-service spring-boot:run` in another
   - Exercise `/public/ping`, `/protected/*`, `/admin/*` with a freshly issued JWT
6. Tag: `git tag -a v0.2.0 -m "v0.2.0"` and push.
7. Run the Central publish profile: `mvn -P central-release deploy`.
8. Push the GitHub release with the same notes that went on Central.
9. Bump `main` to the next `-SNAPSHOT`.

## What goes in a PATCH release

- Compilation fixes against newer Spring Boot patch versions.
- Bug fixes that don't change behaviour anyone could be relying on.
- Documentation fixes.

What does **not** go in a PATCH release: new properties, new public classes, dependency upgrades that change transitives, behaviour changes — those wait for the next MINOR.

## Communicating breaking changes

While we're pre-1.0, every breaking change in a MINOR release ships with:

- A `@Deprecated(since = "X.Y.0", forRemoval = true)` on the old API kept for at least one MINOR.
- A migration note in the release-notes for that version.
- Where practical, an opt-in flag so consumers can adopt the new behaviour at their own pace.

The v0.1.x → v0.2.0 transition is an exception — v0.1.x had near-zero adoption, the entire identity of the project changed, and the deprecated surface lives in its own module (`vault-sdk-legacy`) rather than littered through the new SDK.
