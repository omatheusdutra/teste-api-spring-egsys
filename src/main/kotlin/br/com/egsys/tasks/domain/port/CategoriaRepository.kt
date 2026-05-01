package br.com.egsys.tasks.domain.port

import br.com.egsys.tasks.domain.model.Categoria
import br.com.egsys.tasks.domain.model.CategoriaId

interface CategoriaRepository {
    fun findAll(): List<Categoria>

    fun findById(id: CategoriaId): Categoria?
}
