package com.cuentasclaras.domain.finance

import com.cuentasclaras.domain.model.Expense

/**
 * Builds the user-visible expense title from category + optional note + installment.
 */
object ExpenseLabels {
    fun title(expense: Expense): String = title(
        categoryName = expense.categoryName,
        note = expense.description,
        installmentIndex = expense.installmentIndex,
        installmentCount = expense.installmentCount,
    )

    fun title(
        categoryName: String?,
        note: String,
        installmentIndex: Int? = null,
        installmentCount: Int? = null,
    ): String {
        val trimmedNote = note.trim()
        val base = when {
            categoryName.isNullOrBlank() -> trimmedNote
            trimmedNote.isEmpty() -> categoryName.trim()
            else -> "${categoryName.trim()} · $trimmedNote"
        }
        if (installmentIndex == null || installmentCount == null) return base
        val suffix = "($installmentIndex/$installmentCount)"
        if (base.endsWith(suffix) || base.contains(" $suffix")) return base
        return "$base $suffix".trim()
    }
}
