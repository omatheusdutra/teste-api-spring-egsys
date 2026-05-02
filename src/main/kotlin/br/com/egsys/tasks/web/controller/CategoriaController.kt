package br.com.egsys.tasks.web.controller

import br.com.egsys.tasks.application.usecase.ListarCategoriasUseCase
import br.com.egsys.tasks.web.dto.CategoriaResponse
import br.com.egsys.tasks.web.mapper.toResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/categorias", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "Categorias")
class CategoriaController(
    private val listarCategorias: ListarCategoriasUseCase,
) {
    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Lista categorias de tarefas")
    fun listar(): List<CategoriaResponse> =
        listarCategorias
            .execute()
            .map { it.toResponse() }
}
