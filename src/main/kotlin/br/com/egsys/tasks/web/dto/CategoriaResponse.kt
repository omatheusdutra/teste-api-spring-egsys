package br.com.egsys.tasks.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

@Schema(name = "CategoriaResponse")
data class CategoriaResponse(
    @field:Schema(example = "018f95df-0c7b-7af2-a199-447f82f36912")
    val id: UUID,
    @field:Schema(example = "Casa")
    val descricao: String,
)
