CREATE TABLE IF NOT EXISTS vault_audit_logs (
                                                id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                                tenant_id   VARCHAR(255),
                                                user_id     VARCHAR(255),
                                                action      VARCHAR(100) NOT NULL,
                                                resource    VARCHAR(200),
                                                ip_address  VARCHAR(45),
                                                status      VARCHAR(20) NOT NULL,
                                                metadata    JSONB,
                                                occurred_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_vault_audit_tenant ON vault_audit_logs(tenant_id, occurred_at DESC);
