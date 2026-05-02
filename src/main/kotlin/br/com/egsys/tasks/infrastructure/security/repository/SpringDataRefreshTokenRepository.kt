package br.com.egsys.tasks.infrastructure.security.repository

import br.com.egsys.tasks.infrastructure.security.entity.RefreshTokenJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpringDataRefreshTokenRepository : JpaRepository<RefreshTokenJpaEntity, UUID> {
    fun findByTokenHash(tokenHash: String): RefreshTokenJpaEntity?

    fun findAllByFamilyId(familyId: UUID): List<RefreshTokenJpaEntity>

    fun findAllByUserId(userId: UUID): List<RefreshTokenJpaEntity>
}
