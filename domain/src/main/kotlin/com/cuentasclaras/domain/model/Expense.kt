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
    /** Optional note; legacy expenses without category use this as the full title. */
    val description: String,
    val amount: Money,
    val paidBy: UserId,
    val date: LocalDate,
    val createdBy: UserId,
    val createdAt: Instant,
    val updatedAt: Instant,
    val splits: List<ExpenseSplit>,
    val installmentSeriesId: String? = null,
    val installmentIndex: Int? = null,
    val installmentCount: Int? = null,
    val categoryId: ExpenseCategoryId? = null,
    val categoryName: String? = null,
    val categoryIcon: CategoryIcon? = null,
) {
    val period: YearMonth get() = YearMonth.from(date)
    val isInstallment: Boolean get() = installmentSeriesId != null

    init {
        require(description.isNotBlank() || categoryId != null) {
            "Expense must have a note or a category"
        }
        require(amount.amountMinor > 0L) { "Expense amount must be greater than zero" }
        require(splits.isNotEmpty()) { "Expense must have at least one split" }
        require(splits.sumOf { it.share.amountMinor } == amount.amountMinor) {
            "Sum of splits must equal expense amount"
        }
        require(splits.all { it.share.currency == amount.currency }) {
            "All splits must use the expense currency"
        }
        val seriesFields = listOf(installmentSeriesId, installmentIndex, installmentCount)
        require(seriesFields.all { it == null } || seriesFields.all { it != null }) {
            "Installment fields must all be null or all set"
        }
        if (installmentSeriesId != null) {
            require(installmentCount!! >= 2) { "Installment count must be at least 2" }
            require(installmentIndex!! in 1..installmentCount) {
                "Installment index must be between 1 and count"
            }
        }
        if (categoryId != null) {
            require(!categoryName.isNullOrBlank()) { "Category name required when category is set" }
            require(categoryIcon != null) { "Category icon required when category is set" }
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
