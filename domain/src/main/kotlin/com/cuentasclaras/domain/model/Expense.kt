package com.cuentasclaras.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

enum class SplitType {
    EQUAL,
    PERCENTAGE,
    FIXED_AMOUNT,
}

data class Expense(
    val id: ExpenseId,
    val groupId: GroupId,
    val description: String,
    val amount: Money,
    val paidBy: UserId,
    val date: LocalDate,
    val createdBy: UserId,
    val createdAt: Instant,
    val updatedAt: Instant,
    val splits: List<ExpenseSplit>,
) {
    val period: YearMonth get() = YearMonth.from(date)

    init {
        require(description.isNotBlank()) { "Expense description must not be blank" }
        require(amount.amountMinor > 0L) { "Expense amount must be greater than zero" }
        require(splits.isNotEmpty()) { "Expense must have at least one split" }
        require(splits.sumOf { it.share.amountMinor } == amount.amountMinor) {
            "Sum of splits must equal expense amount"
        }
        require(splits.all { it.share.currency == amount.currency }) {
            "All splits must use the expense currency"
        }
    }
}

@JvmInline
value class ExpenseId(val value: String) {
    init {
        require(value.isNotBlank()) { "ExpenseId must not be blank" }
    }
}

data class ExpenseSplit(
    val userId: UserId,
    val splitType: SplitType,
    val share: Money,
)

/**
 * Input for creating/updating an expense before persistence assigns IDs/timestamps.
 */
data class ExpenseDraft(
    val groupId: GroupId,
    val description: String,
    val amount: Money,
    val paidBy: UserId,
    val date: LocalDate,
    val createdBy: UserId,
    val participantIds: List<UserId>,
)
