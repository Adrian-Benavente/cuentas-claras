package com.cuentasclaras.domain.model

import java.time.Instant

data class User(
    val id: UserId,
    val name: String,
    val email: String?,
    val avatarUrl: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@JvmInline
value class UserId(val value: String) {
    init {
        require(value.isNotBlank()) { "UserId must not be blank" }
    }
}
