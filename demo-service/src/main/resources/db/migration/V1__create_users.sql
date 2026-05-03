CREATE TABLE users (
                       id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       email             VARCHAR(255) UNIQUE NOT NULL,
                       password_hash     VARCHAR(255) NOT NULL,
                       first_name        VARCHAR(100),
                       company_name      VARCHAR(200),
                       subscription_tier VARCHAR(50) NOT NULL DEFAULT 'FREE',
                       role              VARCHAR(50) NOT NULL DEFAULT 'USER',
                       tenant_id         UUID NOT NULL,
                       enabled           BOOLEAN NOT NULL DEFAULT true,
                       created_at        TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_tenant ON users(tenant_id);
