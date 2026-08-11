package com.cuentasclaras.domain.finance

import com.cuentasclaras.domain.model.Currency
import com.cuentasclaras.domain.model.Expense
import com.cuentasclaras.domain.model.ExpenseDraft
import com.cuentasclaras.domain.model.ExpenseId
import com.cuentasclaras.domain.model.PeriodSummary
import com.cuentasclaras.domain.model.SettlementPayment
import com.cuentasclaras.domain.model.UserId
import java.time.Instant
import java.time.YearMonth

object PeriodSummaryCalculator {

    fun summarize(
        expenses: List<Expense>,
        memberIds: List<UserId>,
        currency: Currency,
        period: YearMonth? = null,
        payments: List<SettlementPayment> = emptyList(),
    ): PeriodSummary {
        val filteredExpenses = if (period == null) {
            expenses
        } else {
            expenses.filter { it.period == period }
        }
        val filteredPayments = if (period == null) {
            payments
        } else {
            payments.filter { it.period == period }
        }

        val participantIds = participantsForSummary(memberIds, filteredExpenses, filteredPayments)
        val expenseBalances = BalanceCalculator.calculate(filteredExpenses, participantIds, currency)
        val outstanding = SettlementPaymentApplicator.apply(expenseBalances, filteredPayments)

        return PeriodSummary(
            totalSpent = BalanceCalculator.totalSpent(filteredExpenses, currency),
            memberBalances = expenseBalances,
            suggestedTransfers = SettlementCalculator.calculate(outstanding),
            recordedPayments = filteredPayments.sortedBy { it.createdAt },
        )
    }

    /**
     * Active members plus anyone involved in the period's expenses/payments
     * (e.g. former members whose historical splits must still balance).
     */
    fun participantsForSummary(
        memberIds: List<UserId>,
        expenses: List<Expense>,
        payments: List<SettlementPayment>,
    ): List<UserId> {
        val ids = linkedSetOf<UserId>()
        memberIds.forEach { ids += it }
        expenses.forEach { expense ->
            ids += expense.paidBy
            expense.splits.forEach { ids += it.userId }
        }
        payments.forEach { payment ->
            ids += payment.fromUserId
            ids += payment.toUserId
        }
        require(ids.isNotEmpty()) { "At least one participant is required" }
        return ids.toList()
    }
}

object ExpenseFactory {

    fun createFromDraft(
        draft: ExpenseDraft,
        id: ExpenseId,
        createdAt: Instant,
        updatedAt: Instant = createdAt,
    ): Expense {
        val splits = EqualSplitCalculator.split(draft.amount, draft.participantIds)
        return Expense(
            id = id,
            groupId = draft.groupId,
            description = draft.description.trim(),
            amount = draft.amount,
            paidBy = draft.paidBy,
            date = draft.date,
            createdBy = draft.createdBy,
            createdAt = createdAt,
            updatedAt = updatedAt,
            splits = splits,
        )
    }
}

fun List<Expense>.without(expenseId: ExpenseId): List<Expense> =
    filterNot { it.id == expenseId }

fun List<Expense>.replacing(updated: Expense): List<Expense> =
    map { if (it.id == updated.id) updated else it }
