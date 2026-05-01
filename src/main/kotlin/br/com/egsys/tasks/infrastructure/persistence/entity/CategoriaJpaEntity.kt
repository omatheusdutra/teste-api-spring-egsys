package br.com.egsys.tasks.infrastructure.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(
    name = "categorias",
    indexes = [
        Index(name = "idx_categorias_descricao", columnList = "descricao"),
    ],
)
class CategoriaJpaEntity(
    @Id
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    var id: UUID? = null,
    @Column(name = "descricao", nullable = false, unique = true, length = 120)
    var descricao: String = "",
)
