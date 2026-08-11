package com.cuentasclaras.domain.model

import java.time.Instant

data class Group(
    val id: GroupId,
    val name: String,
    val currency: Currency,
    val inviteCode: String,
    val createdBy: UserId,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@JvmInline
value class GroupId(val value: String) {
    init {
        require(value.isNotBlank()) { "GroupId must not be blank" }
    }
}

enum class MemberRole {
    OWNER,
    MEMBER,
}

data class GroupMember(
    val groupId: GroupId,
    val userId: UserId,
    val role: MemberRole,
    val joinedAt: Instant,
    val displayName: String = "",
)
