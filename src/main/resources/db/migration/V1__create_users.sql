CREATE TABLE users (
                       id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       email         VARCHAR(255) UNIQUE NOT NULL,
                       password_hash VARCHAR(255) NOT NULL,
                       tenant_id     UUID NOT NULL,
                       role          VARCHAR(50)  NOT NULL DEFAULT 'USER',
                       enabled       BOOLEAN NOT NULL DEFAULT true,
                       created_at    TIMESTAMP NOT NULL DEFAULT now()
);