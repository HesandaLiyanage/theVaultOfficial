CREATE TABLE api_keys (
                          id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                          key_hash     VARCHAR(255) UNIQUE NOT NULL,  -- BCrypt hash of the raw key
                          key_prefix   VARCHAR(10) NOT NULL,           -- first 8 chars for display only
                          name         VARCHAR(100) NOT NULL,          -- human label e.g. 'production'
                          tenant_id    UUID NOT NULL,
                          created_by   UUID NOT NULL REFERENCES users(id),
                          scopes       VARCHAR(255) NOT NULL,          -- READ,WRITE,DELETE
                          expires_at   TIMESTAMP,                      -- NULL = never expires
                          last_used_at TIMESTAMP,
                          revoked      BOOLEAN NOT NULL DEFAULT false,
                          created_at   TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_api_keys_hash   ON api_keys(key_hash);
CREATE INDEX idx_api_keys_tenant ON api_keys(tenant_id);
