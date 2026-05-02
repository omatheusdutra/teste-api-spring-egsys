package br.com.egsys.tasks.infrastructure.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "tarefa_historico",
    indexes = [
        Index(name = "idx_tarefa_historico_tarefa_owner_occurred", columnList = "tarefa_id, owner_id, occurred_at, id"),
    ],
)
class TarefaHistoricoJpaEntity(
    @Id
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    var id: UUID? = null,
    @Column(name = "tarefa_id", nullable = false, columnDefinition = "uuid")
    var tarefaId: UUID? = null,
    @Column(name = "owner_id", nullable = false, columnDefinition = "uuid")
    var ownerId: UUID? = null,
    @Column(name = "event_type", nullable = false, length = 80)
    var eventType: String = "",
    @Column(name = "changed_fields", nullable = false, length = 500)
    var changedFields: String = "",
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    var payload: String = "{}",
    @Column(name = "occurred_at", nullable = false)
    var occurredAt: Instant? = null,
)
