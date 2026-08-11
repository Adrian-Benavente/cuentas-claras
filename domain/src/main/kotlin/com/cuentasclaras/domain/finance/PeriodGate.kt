package com.cuentasclaras.domain.finance

import com.cuentasclaras.domain.model.PeriodStatus
import java.time.LocalDate
import java.time.YearMonth

/**
 * Pure helpers for UI/client gates around period open/closed.
 * Server enforcement remains authoritative.
 */
object PeriodGate {
    fun statusOf(period: YearMonth, closedPeriods: Set<YearMonth>): PeriodStatus =
        if (period in closedPeriods) PeriodStatus.CLOSED else PeriodStatus.OPEN

    fun isClosed(period: YearMonth, closedPeriods: Set<YearMonth>): Boolean =
        statusOf(period, closedPeriods) == PeriodStatus.CLOSED

    fun canMutateExpense(date: LocalDate, closedPeriods: Set<YearMonth>): Boolean =
        !isClosed(YearMonth.from(date), closedPeriods)

    fun canMutateSettlement(period: YearMonth, closedPeriods: Set<YearMonth>): Boolean =
        !isClosed(period, closedPeriods)

    /**
     * FAB for new expenses: hide only when the selected period is closed AND today
     * falls in that same period (new expenses default to today).
     */
    fun showCreateExpenseFab(
        selectedPeriod: YearMonth,
        selectedPeriodClosed: Boolean,
        today: LocalDate = LocalDate.now(),
    ): Boolean {
        if (!selectedPeriodClosed) return true
        return YearMonth.from(today) != selectedPeriod
    }

    fun coerceNotFuture(period: YearMonth, now: YearMonth = YearMonth.now()): YearMonth =
        if (period.isAfter(now)) now else period

    fun canGoToNextPeriod(period: YearMonth, now: YearMonth = YearMonth.now()): Boolean =
        period.isBefore(now)
}
