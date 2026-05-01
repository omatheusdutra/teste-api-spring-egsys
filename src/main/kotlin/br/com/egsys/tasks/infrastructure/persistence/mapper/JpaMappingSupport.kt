package br.com.egsys.tasks.infrastructure.persistence.mapper

internal fun <T : Any> requireJpaField(
    value: T?,
    fieldName: String,
): T = value ?: throw PersistenceMappingException("Campo obrigatorio ausente na entidade JPA: $fieldName")
