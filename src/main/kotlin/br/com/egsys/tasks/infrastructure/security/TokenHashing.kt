package br.com.egsys.tasks.infrastructure.security

import java.security.MessageDigest

object TokenHashing {
    fun sha256Hex(value: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
