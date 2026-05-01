package br.com.egsys.tasks.application.usecase

import br.com.egsys.tasks.domain.model.Categoria
import br.com.egsys.tasks.domain.port.CategoriaRepository

class ListarCategoriasUseCase(
    private val categorias: CategoriaRepository,
) {
    fun execute(): List<Categoria> = categorias.findAll()
}
