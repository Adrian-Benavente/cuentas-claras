package com.cuentasclaras.domain.model

import java.time.Instant

@JvmInline
value class ExpenseCategoryId(val value: String) {
    init {
        require(value.isNotBlank()) { "ExpenseCategoryId must not be blank" }
    }
}

data class ExpenseCategory(
    val id: ExpenseCategoryId,
    val groupId: GroupId,
    val name: String,
    val icon: CategoryIcon,
    val createdBy: UserId,
    val createdAt: Instant,
    val updatedAt: Instant,
    val isUncategorized: Boolean = false,
) {
    init {
        require(name.isNotBlank()) { "Category name must not be blank" }
        require(name.length <= 40) { "Category name must be at most 40 characters" }
    }
}
