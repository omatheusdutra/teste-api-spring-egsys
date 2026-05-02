package br.com.egsys.tasks.web.mapper

import br.com.egsys.tasks.domain.model.Categoria
import br.com.egsys.tasks.domain.model.Tarefa
import br.com.egsys.tasks.domain.model.TarefaHistorico
import br.com.egsys.tasks.web.dto.CategoriaResponse
import br.com.egsys.tasks.web.dto.TarefaHistoricoResponse
import br.com.egsys.tasks.web.dto.TarefaResponse

fun Categoria.toResponse(): CategoriaResponse =
    CategoriaResponse(
        id = id.value,
        descricao = descricao.value,
    )

fun Tarefa.toResponse(): TarefaResponse =
    TarefaResponse(
        id = id.value,
        titulo = titulo.value,
        descricao = descricao?.value,
        categoria = categoria.toResponse(),
        dataHora = dataHora.value,
        status = status,
        criadaEm = criadaEm,
        atualizadaEm = atualizadaEm,
    )

fun TarefaHistorico.toResponse(): TarefaHistoricoResponse =
    TarefaHistoricoResponse(
        id = id,
        tarefaId = tarefaId.value,
        eventType = eventType,
        changedFields = changedFields,
        occurredAt = occurredAt,
    )
