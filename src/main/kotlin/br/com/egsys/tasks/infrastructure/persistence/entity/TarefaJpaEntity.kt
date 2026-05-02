package br.com.egsys.tasks.infrastructure.persistence.entity

import br.com.egsys.tasks.domain.model.TarefaStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Suppress("LongParameterList")
@Entity
@Table(
    name = "tarefas",
    indexes = [
        Index(name = "idx_tarefas_categoria_id", columnList = "categoria_id"),
        Index(name = "idx_tarefas_status_active", columnList = "status"),
    ],
)
class TarefaJpaEntity(
    @Id
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    var id: UUID? = null,
    @Column(name = "owner_id", nullable = false, columnDefinition = "uuid")
    var ownerId: UUID? = null,
    @Column(name = "titulo", nullable = false, length = 200)
    var titulo: String = "",
    @Column(name = "descricao", length = 2_000)
    var descricao: String? = null,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    var categoria: CategoriaJpaEntity? = null,
    @Column(name = "data_hora", nullable = false)
    var dataHora: Instant? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: TarefaStatus = TarefaStatus.PENDENTE,
    @Column(name = "criada_em", nullable = false, updatable = false)
    var criadaEm: Instant? = null,
    @Column(name = "atualizada_em", nullable = false)
    var atualizadaEm: Instant? = null,
    @Column(name = "excluida_em")
    var excluidaEm: Instant? = null,
)
