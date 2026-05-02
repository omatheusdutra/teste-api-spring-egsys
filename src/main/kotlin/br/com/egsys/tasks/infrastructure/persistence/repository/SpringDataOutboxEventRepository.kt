package br.com.egsys.tasks.infrastructure.persistence.repository

import br.com.egsys.tasks.infrastructure.persistence.entity.OutboxEventJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpringDataOutboxEventRepository : JpaRepository<OutboxEventJpaEntity, UUID>
