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
@Suppress("LongParameterList")
@Table(
    name = "outbox_events",
    indexes = [
        Index(name = "idx_outbox_tarefa_id", columnList = "tarefa_id"),
    ],
)
class OutboxEventJpaEntity(
    @Id
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    var id: UUID? = null,
    @Column(name = "aggregate_type", nullable = false, length = 80)
    var aggregateType: String = "",
    @Column(name = "tarefa_id", nullable = false, columnDefinition = "uuid")
    var tarefaId: UUID? = null,
    @Column(name = "owner_id", nullable = false, columnDefinition = "uuid")
    var ownerId: UUID? = null,
    @Column(name = "event_type", nullable = false, length = 80)
    var eventType: String = "",
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    var payload: String = "{}",
    @Column(name = "occurred_at", nullable = false)
    var occurredAt: Instant? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,
    @Column(name = "processed_at")
    var processedAt: Instant? = null,
)
