CREATE TABLE categorias (
    id UUID PRIMARY KEY,
    descricao VARCHAR(120) NOT NULL,
    criada_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizada_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_categorias_descricao UNIQUE (descricao),
    CONSTRAINT ck_categorias_descricao_not_blank CHECK (LENGTH(BTRIM(descricao)) > 0)
);

CREATE INDEX idx_categorias_descricao ON categorias (descricao);

CREATE TABLE tarefas (
    id UUID PRIMARY KEY,
    titulo VARCHAR(200) NOT NULL,
    descricao VARCHAR(2000),
    categoria_id UUID NOT NULL,
    data_hora TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL,
    criada_em TIMESTAMPTZ NOT NULL,
    atualizada_em TIMESTAMPTZ NOT NULL,
    excluida_em TIMESTAMPTZ,
    CONSTRAINT fk_tarefas_categoria
        FOREIGN KEY (categoria_id)
        REFERENCES categorias (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_tarefas_titulo_not_blank CHECK (LENGTH(BTRIM(titulo)) > 0),
    CONSTRAINT ck_tarefas_status
        CHECK (status IN ('PENDENTE', 'EM_ANDAMENTO', 'CONCLUIDA', 'CANCELADA')),
    CONSTRAINT ck_tarefas_atualizada_depois_criada CHECK (atualizada_em >= criada_em),
    CONSTRAINT ck_tarefas_excluida_depois_criada CHECK (excluida_em IS NULL OR excluida_em >= criada_em)
);

CREATE INDEX idx_tarefas_categoria_id ON tarefas (categoria_id);
CREATE INDEX idx_tarefas_data_hora_id_active ON tarefas (data_hora, id) WHERE excluida_em IS NULL;
CREATE INDEX idx_tarefas_status_active ON tarefas (status) WHERE excluida_em IS NULL;
