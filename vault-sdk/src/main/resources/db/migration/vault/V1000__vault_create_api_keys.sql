CREATE TABLE IF NOT EXISTS vault_api_keys (
                                               id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                               key_hash     VARCHAR(255) UNIQUE NOT NULL,
                                               key_prefix   VARCHAR(10) NOT NULL,
                                               name         VARCHAR(100) NOT NULL,
                                               tenant_id    VARCHAR(255) NOT NULL,
                                               created_by   VARCHAR(255) NOT NULL,
                                               scopes       VARCHAR(255) NOT NULL,
                                               expires_at   TIMESTAMP,
                                               last_used_at TIMESTAMP,
                                               revoked      BOOLEAN NOT NULL DEFAULT false,
                                               created_at   TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_vault_api_keys_hash ON vault_api_keys(key_hash);
CREATE INDEX IF NOT EXISTS idx_vault_api_keys_prefix ON vault_api_keys(key_prefix);
CREATE INDEX IF NOT EXISTS idx_vault_api_keys_tenant ON vault_api_keys(tenant_id);
