package br.com.egsys.tasks.domain.exception

open class DomainException(
    message: String,
) : RuntimeException(message)

class InvalidDomainValueException(
    message: String,
) : DomainException(message)

class InvalidTaskStateTransitionException(
    message: String,
) : DomainException(message)
