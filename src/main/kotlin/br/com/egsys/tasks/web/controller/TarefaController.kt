package br.com.egsys.tasks.web.controller

import br.com.egsys.tasks.application.usecase.AlterarStatusTarefaCommand
import br.com.egsys.tasks.application.usecase.AlterarStatusTarefaUseCase
import br.com.egsys.tasks.application.usecase.AtualizarTarefaCommand
import br.com.egsys.tasks.application.usecase.AtualizarTarefaUseCase
import br.com.egsys.tasks.application.usecase.BuscarTarefaUseCase
import br.com.egsys.tasks.application.usecase.CriarTarefaCommand
import br.com.egsys.tasks.application.usecase.CriarTarefaUseCase
import br.com.egsys.tasks.application.usecase.ExcluirTarefaUseCase
import br.com.egsys.tasks.application.usecase.ListarHistoricoTarefaUseCase
import br.com.egsys.tasks.application.usecase.ListarTarefasUseCase
import br.com.egsys.tasks.domain.exception.InvalidDomainValueException
import br.com.egsys.tasks.domain.model.Tarefa
import br.com.egsys.tasks.domain.model.TarefaStatus
import br.com.egsys.tasks.infrastructure.security.currentUserId
import br.com.egsys.tasks.web.dto.AtualizarTarefaRequest
import br.com.egsys.tasks.web.dto.CriarTarefaRequest
import br.com.egsys.tasks.web.dto.TarefaHistoricoResponse
import br.com.egsys.tasks.web.dto.TarefaPageResponse
import br.com.egsys.tasks.web.dto.TarefaResponse
import br.com.egsys.tasks.web.mapper.CursorCodec
import br.com.egsys.tasks.web.mapper.TarefaCursor
import br.com.egsys.tasks.web.mapper.toResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import java.nio.charset.StandardCharsets
import java.util.UUID

@Validated
@RestController
@RequestMapping("/api/v1/tarefas", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "Tarefas")
@Suppress("TooManyFunctions")
class TarefaController(
    private val criarTarefa: CriarTarefaUseCase,
    private val atualizarTarefa: AtualizarTarefaUseCase,
    private val buscarTarefa: BuscarTarefaUseCase,
    private val listarTarefas: ListarTarefasUseCase,
    private val excluirTarefa: ExcluirTarefaUseCase,
    private val alterarStatusTarefa: AlterarStatusTarefaUseCase,
    private val listarHistoricoTarefa: ListarHistoricoTarefaUseCase,
) {
    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Cria uma tarefa")
    fun criar(
        @Valid @RequestBody request: CriarTarefaRequest,
    ): ResponseEntity<TarefaResponse> {
        val tarefa =
            criarTarefa.execute(
                CriarTarefaCommand(
                    ownerId = currentUserId(),
                    titulo = requireNotNull(request.titulo),
                    descricao = request.descricao,
                    categoriaId = requireNotNull(request.categoriaId),
                    dataHora = requireNotNull(request.dataHora),
                ),
            )
        val location =
            ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(tarefa.id.value)
                .toUri()

        return ResponseEntity.created(location).body(tarefa.toResponse())
    }

    @GetMapping("/{id:[0-9a-fA-F\\-]{36}}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Busca uma tarefa ativa por ID")
    fun buscar(
        @PathVariable id: UUID,
    ): TarefaResponse = buscarTarefa.execute(id, currentUserId()).toResponse()

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Lista tarefas ativas com paginacao cursor-based")
    fun listar(
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "50") @Min(1) @Max(100) limit: Int,
    ): TarefaPageResponse {
        val decodedCursor = cursor?.let(CursorCodec::decode)
        val ordered =
            listarTarefas
                .execute(currentUserId())
                .sortedWith(compareBy<Tarefa> { it.dataHora.value }.thenBy { it.id.value })
        val pageSource = ordered.after(decodedCursor)
        val items = pageSource.take(limit)

        return TarefaPageResponse(
            items = items.map { it.toResponse() },
            nextCursor = items.lastOrNull()?.takeIf { pageSource.size > limit }?.let(CursorCodec::encode),
        )
    }

    @GetMapping(value = ["/export.csv"], produces = ["text/csv"])
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Exporta tarefas ativas em CSV")
    fun exportarCsv(): ResponseEntity<String> {
        val rows =
            listarTarefas
                .execute(currentUserId())
                .sortedWith(compareBy<Tarefa> { it.dataHora.value }.thenBy { it.id.value })
                .joinToString(separator = "\n", prefix = CSV_HEADER + "\n") { it.toCsvRow() }

        return ResponseEntity
            .ok()
            .contentType(MediaType("text", "csv", StandardCharsets.UTF_8))
            .body(rows)
    }

    @PutMapping("/{id:[0-9a-fA-F\\-]{36}}", consumes = [MediaType.APPLICATION_JSON_VALUE])
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Atualiza uma tarefa ativa")
    fun atualizar(
        @PathVariable id: UUID,
        @Valid @RequestBody request: AtualizarTarefaRequest,
    ): TarefaResponse =
        atualizarTarefa
            .execute(
                AtualizarTarefaCommand(
                    id = id,
                    ownerId = currentUserId(),
                    titulo = requireNotNull(request.titulo),
                    descricao = request.descricao,
                    categoriaId = requireNotNull(request.categoriaId),
                    dataHora = requireNotNull(request.dataHora),
                ),
            ).toResponse()

    @DeleteMapping("/{id:[0-9a-fA-F\\-]{36}}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Exclui uma tarefa ativa usando soft delete")
    fun excluir(
        @PathVariable id: UUID,
    ) {
        excluirTarefa.execute(id, currentUserId())
    }

    @PostMapping("/{id:[0-9a-fA-F\\-]{36}}/status/{status}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Altera status usando maquina de estados validada")
    fun alterarStatus(
        @PathVariable id: UUID,
        @PathVariable status: String,
    ): TarefaResponse =
        alterarStatusTarefa
            .execute(
                AlterarStatusTarefaCommand(
                    id = id,
                    ownerId = currentUserId(),
                    status = status.toTarefaStatus(),
                ),
            ).toResponse()

    @GetMapping("/{id:[0-9a-fA-F\\-]{36}}/historico")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Lista historico auditavel da tarefa")
    fun historico(
        @PathVariable id: UUID,
    ): List<TarefaHistoricoResponse> =
        listarHistoricoTarefa
            .execute(id, currentUserId())
            .map { it.toResponse() }

    private fun currentUserId(): UUID = SecurityContextHolder.getContext().authentication.currentUserId()

    private fun String.toTarefaStatus(): TarefaStatus =
        when (lowercase()) {
            "em-andamento" -> TarefaStatus.EM_ANDAMENTO
            "concluida" -> TarefaStatus.CONCLUIDA
            "cancelada" -> TarefaStatus.CANCELADA
            else -> throw InvalidDomainValueException("status de tarefa invalido: $this")
        }

    private fun Tarefa.toCsvRow(): String =
        listOf(
            id.value.toString(),
            titulo.value,
            descricao?.value.orEmpty(),
            categoria.descricao.value,
            status.name,
            dataHora.value.toString(),
            criadaEm.toString(),
            atualizadaEm.toString(),
        ).joinToString(",") { it.csvEscape() }

    private fun String.csvEscape(): String {
        val escaped = replace("\"", "\"\"")
        val requiresQuotes = any { it in CSV_ESCAPED_CHARS }
        return if (requiresQuotes) {
            "\"$escaped\""
        } else {
            escaped
        }
    }

    private fun List<Tarefa>.after(cursor: TarefaCursor?): List<Tarefa> {
        if (cursor == null) {
            return this
        }

        return filter {
            it.dataHora.value > cursor.dataHora ||
                (it.dataHora.value == cursor.dataHora && it.id.value > cursor.id)
        }
    }

    private companion object {
        const val CSV_HEADER = "id,titulo,descricao,categoria,status,dataHora,criadaEm,atualizadaEm"
        val CSV_ESCAPED_CHARS = setOf(',', '"', '\n', '\r')
    }
}
