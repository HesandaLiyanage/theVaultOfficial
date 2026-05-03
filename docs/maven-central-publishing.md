# Maven Central Publishing

This project is configured for Maven Central with the `central-release` Maven profile.

## 1. Install GPG

On macOS:

```bash
brew install gnupg
gpg --version
```

## 2. Create a Signing Key

```bash
gpg --full-generate-key
```

Recommended choices:

- Key type: RSA and RSA
- Key size: 4096
- Expiration: 1y or 2y
- Name/email: your public developer identity
- Passphrase: strong and saved securely

List your key:

```bash
gpg --list-secret-keys --keyid-format LONG
```

Copy the long key id from the `sec` line.

## 3. Publish Your Public Key

```bash
gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_LONG_KEY_ID
```

## 4. Configure Maven Central Credentials

Add your Sonatype Central token to `~/.m2/settings.xml`:

```xml
<settings>
    <servers>
        <server>
            <id>central</id>
            <username>YOUR_CENTRAL_TOKEN_USERNAME</username>
            <password>YOUR_CENTRAL_TOKEN_PASSWORD</password>
        </server>
    </servers>
</settings>
```

The `<id>` must match `publishingServerId` in `pom.xml`.

## 5. Release

Set a non-SNAPSHOT version before publishing:

```bash
mvn versions:set -DnewVersion=0.1.0
```

Build, sign, and upload to Central Portal:

```bash
mvn clean deploy -Pcentral-release
```

The profile uses `autoPublish=false`, so the deployment uploads to Central Portal for manual review/publish. After it validates, publish it from the Central Portal UI.

## Notes

- Maven Central versions are immutable. If `0.1.0` is published, the next fix must be `0.1.1`.
- Keep the GPG private key and Central token secret.
- If signing fails locally, first check that `gpg --version` works.
