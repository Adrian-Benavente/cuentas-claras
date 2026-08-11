package com.cuentasclaras.domain.finance

import com.cuentasclaras.domain.model.Currency
import com.cuentasclaras.domain.model.Expense
import com.cuentasclaras.domain.model.MemberBalance
import com.cuentasclaras.domain.model.Money
import com.cuentasclaras.domain.model.UserId

/**
 * Derives member balances from a list of expenses.
 *
 * balance = amountPaid - amountOwed
 * - positive → should receive money
 * - negative → should pay money
 * - zero → settled
 */
object BalanceCalculator {

    fun calculate(
        expenses: List<Expense>,
        memberIds: List<UserId>,
        currency: Currency,
    ): List<MemberBalance> {
        require(memberIds.isNotEmpty()) { "At least one member is required" }
        require(memberIds.distinct().size == memberIds.size) { "Member IDs must be unique" }
        expenses.forEach { expense ->
            require(expense.amount.currency == currency) {
                "Expense currency mismatch: expected ${currency.code}"
            }
        }

        val paid = mutableMapOf<UserId, Long>().withDefault { 0L }
        val owed = mutableMapOf<UserId, Long>().withDefault { 0L }

        memberIds.forEach { id ->
            paid[id] = 0L
            owed[id] = 0L
        }

        for (expense in expenses) {
            paid[expense.paidBy] = paid.getValue(expense.paidBy) + expense.amount.amountMinor
            for (split in expense.splits) {
                owed[split.userId] = owed.getValue(split.userId) + split.share.amountMinor
            }
        }

        return memberIds
            .sortedBy { it.value }
            .map { userId ->
                val amountPaid = Money(paid.getValue(userId), currency)
                val amountOwed = Money(owed.getValue(userId), currency)
                MemberBalance(
                    userId = userId,
                    amountPaid = amountPaid,
                    amountOwed = amountOwed,
                    balance = Money(amountPaid.amountMinor - amountOwed.amountMinor, currency),
                )
            }
    }

    fun totalSpent(expenses: List<Expense>, currency: Currency): Money {
        val total = expenses.sumOf { expense ->
            require(expense.amount.currency == currency)
            expense.amount.amountMinor
        }
        return Money(total, currency)
    }
}
