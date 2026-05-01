CREATE TABLE audit_logs (
                            id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            tenant_id   UUID,
                            user_id     UUID,
                            action      VARCHAR(100) NOT NULL,   -- LOGIN, API_KEY_CREATED, RATE_LIMITED
                            resource    VARCHAR(200),            -- endpoint path
                            ip_address  VARCHAR(45),
                            status      VARCHAR(20) NOT NULL,    -- SUCCESS | FAILURE | BLOCKED
                            metadata    JSONB,
                            occurred_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_tenant ON audit_logs(tenant_id, occurred_at DESC);
