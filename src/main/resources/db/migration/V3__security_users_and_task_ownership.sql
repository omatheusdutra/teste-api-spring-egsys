CREATE TABLE usuarios (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(40) NOT NULL,
    criada_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizada_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_usuarios_email UNIQUE (email),
    CONSTRAINT ck_usuarios_email_not_blank CHECK (LENGTH(BTRIM(email)) > 0),
    CONSTRAINT ck_usuarios_password_hash_not_blank CHECK (LENGTH(BTRIM(password_hash)) > 0),
    CONSTRAINT ck_usuarios_role CHECK (role IN ('ROLE_USER', 'ROLE_ADMIN'))
);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    family_id UUID NOT NULL,
    user_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id)
        REFERENCES usuarios (id)
        ON DELETE RESTRICT,
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash)
);

INSERT INTO usuarios (id, email, password_hash, role)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'system@egsys.local',
    'disabled-system-account',
    'ROLE_ADMIN'
);

ALTER TABLE tarefas
    ADD COLUMN owner_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001';

ALTER TABLE tarefas
    ALTER COLUMN owner_id DROP DEFAULT;

ALTER TABLE tarefas
    ADD CONSTRAINT fk_tarefas_owner
        FOREIGN KEY (owner_id)
        REFERENCES usuarios (id)
        ON DELETE RESTRICT;

CREATE INDEX idx_usuarios_email ON usuarios (email);
CREATE INDEX idx_refresh_tokens_user_family ON refresh_tokens (user_id, family_id);
CREATE INDEX idx_tarefas_owner_data_hora_id_active ON tarefas (owner_id, data_hora, id) WHERE excluida_em IS NULL;
