# ADR 0006 - Inovacoes: Outbox, historico, status e CSV

## Status

Aceita

## Contexto

O edital valoriza criatividade, mas a API deve continuar segura e manutenivel. As inovacoes escolhidas precisam agregar
valor operacional sem criar atalhos inseguros ou dependencia de infraestrutura externa ainda nao entregue.

## Decisao

Implementamos quatro diferenciais:

- Maquina de estados exposta por endpoint: `PENDENTE -> EM_ANDAMENTO -> CONCLUIDA` ou `CANCELADA`, validada no dominio.
- Outbox Pattern: eventos de dominio da tarefa sao gravados em `outbox_events` na mesma transacao da alteracao.
- Historico auditavel por tarefa: os mesmos eventos alimentam `tarefa_historico`, filtrado por `owner_id` para evitar IDOR.
- Exportacao CSV: tarefas ativas do usuario autenticado podem ser exportadas em `text/csv`, com escape seguro de campos.

Soft delete ja existia nas etapas anteriores e agora passa a deixar rastro no historico quando a exclusao gera
`TarefaExcluida`.

## Consequencias

- Notificacoes futuras podem consumir `outbox_events` sem acoplar casos de uso a mensageria.
- Auditoria deixa de depender somente de logs e passa a ter trilha transacional no banco.
- CSV oferece integracao simples para avaliacao e uso administrativo sem ampliar escopo para upload/importacao.
- Transicoes invalidas continuam bloqueadas no dominio, independentemente do cliente HTTP.
