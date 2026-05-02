CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(80) NOT NULL,
    tarefa_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMPTZ,
    CONSTRAINT fk_outbox_tarefa
        FOREIGN KEY (tarefa_id)
        REFERENCES tarefas (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_outbox_owner
        FOREIGN KEY (owner_id)
        REFERENCES usuarios (id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_outbox_unprocessed_created_at
    ON outbox_events (created_at, id)
    WHERE processed_at IS NULL;
CREATE INDEX idx_outbox_tarefa_id ON outbox_events (tarefa_id);

CREATE TABLE tarefa_historico (
    id UUID PRIMARY KEY,
    tarefa_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    changed_fields VARCHAR(500) NOT NULL DEFAULT '',
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_tarefa_historico_tarefa
        FOREIGN KEY (tarefa_id)
        REFERENCES tarefas (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_tarefa_historico_owner
        FOREIGN KEY (owner_id)
        REFERENCES usuarios (id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_tarefa_historico_tarefa_owner_occurred
    ON tarefa_historico (tarefa_id, owner_id, occurred_at, id);
