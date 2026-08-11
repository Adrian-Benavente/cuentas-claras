package com.cuentasclaras.domain.finance

import com.cuentasclaras.domain.model.PeriodStatus
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class PeriodGateTest {

    private val august = YearMonth.of(2026, 8)
    private val july = YearMonth.of(2026, 7)
    private val closed = setOf(august)

    @Test
    fun statusDefaultsToOpen() {
        assertThat(PeriodGate.statusOf(july, closed)).isEqualTo(PeriodStatus.OPEN)
        assertThat(PeriodGate.statusOf(august, closed)).isEqualTo(PeriodStatus.CLOSED)
        assertThat(PeriodGate.statusOf(august, emptySet())).isEqualTo(PeriodStatus.OPEN)
    }

    @Test
    fun canMutateExpenseUsesExpenseDateMonth() {
        assertThat(PeriodGate.canMutateExpense(LocalDate.of(2026, 8, 15), closed)).isFalse()
        assertThat(PeriodGate.canMutateExpense(LocalDate.of(2026, 7, 1), closed)).isTrue()
    }

    @Test
    fun canMutateSettlementUsesPeriod() {
        assertThat(PeriodGate.canMutateSettlement(august, closed)).isFalse()
        assertThat(PeriodGate.canMutateSettlement(july, closed)).isTrue()
    }

    @Test
    fun fabHiddenOnlyWhenSelectedClosedPeriodIsCurrentMonth() {
        val todayInAugust = LocalDate.of(2026, 8, 11)
        assertThat(
            PeriodGate.showCreateExpenseFab(
                selectedPeriod = august,
                selectedPeriodClosed = true,
                today = todayInAugust,
            ),
        ).isFalse()
        assertThat(
            PeriodGate.showCreateExpenseFab(
                selectedPeriod = july,
                selectedPeriodClosed = true,
                today = todayInAugust,
            ),
        ).isTrue()
        assertThat(
            PeriodGate.showCreateExpenseFab(
                selectedPeriod = august,
                selectedPeriodClosed = false,
                today = todayInAugust,
            ),
        ).isTrue()
    }

    @Test
    fun coerceNotFuture_clampsFutureMonths() {
        val now = YearMonth.of(2026, 8)
        assertThat(PeriodGate.coerceNotFuture(YearMonth.of(2026, 9), now)).isEqualTo(now)
        assertThat(PeriodGate.coerceNotFuture(now, now)).isEqualTo(now)
        assertThat(PeriodGate.coerceNotFuture(july, now)).isEqualTo(july)
    }

    @Test
    fun canGoToNextPeriod_falseOnCurrentMonth() {
        val now = YearMonth.of(2026, 8)
        assertThat(PeriodGate.canGoToNextPeriod(july, now)).isTrue()
        assertThat(PeriodGate.canGoToNextPeriod(now, now)).isFalse()
        assertThat(PeriodGate.canGoToNextPeriod(YearMonth.of(2026, 9), now)).isFalse()
    }
}
