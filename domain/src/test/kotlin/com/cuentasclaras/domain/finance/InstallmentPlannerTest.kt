package com.cuentasclaras.domain.finance

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class InstallmentPlannerTest {

    @Test
    fun plan_splitsTotalWithRemainderOnFirstInstallments() {
        val slices = InstallmentPlanner.plan(
            totalMinor = 10_000L,
            count = 3,
            startDate = LocalDate.of(2026, 8, 15),
        )

        assertThat(slices).hasSize(3)
        assertThat(slices.map { it.amountMinor }).containsExactly(3_334L, 3_333L, 3_333L).inOrder()
        assertThat(slices.sumOf { it.amountMinor }).isEqualTo(10_000L)
        assertThat(slices.map { it.index }).containsExactly(1, 2, 3).inOrder()
        assertThat(slices.map { it.date }).containsExactly(
            LocalDate.of(2026, 8, 15),
            LocalDate.of(2026, 9, 15),
            LocalDate.of(2026, 10, 15),
        ).inOrder()
    }

    @Test
    fun plan_twoInstallmentsEvenSplit() {
        val slices = InstallmentPlanner.plan(10_000L, 2, LocalDate.of(2026, 1, 10))
        assertThat(slices.map { it.amountMinor }).containsExactly(5_000L, 5_000L).inOrder()
    }

    @Test
    fun plan_clampsDayToMonthLength() {
        val slices = InstallmentPlanner.plan(
            totalMinor = 3_000L,
            count = 3,
            startDate = LocalDate.of(2026, 1, 31),
        )

        assertThat(slices.map { it.date }).containsExactly(
            LocalDate.of(2026, 1, 31),
            LocalDate.of(2026, 2, 28),
            LocalDate.of(2026, 3, 31),
        ).inOrder()
    }

    @Test
    fun plan_rejectsInvalidCountOrTotal() {
        assertThrows(IllegalArgumentException::class.java) {
            InstallmentPlanner.plan(100L, 1, LocalDate.of(2026, 8, 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            InstallmentPlanner.plan(100L, 49, LocalDate.of(2026, 8, 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            InstallmentPlanner.plan(0L, 3, LocalDate.of(2026, 8, 1))
        }
    }

    @Test
    fun labeledDescription_appendsIndex() {
        assertThat(InstallmentPlanner.labeledDescription("Notebook", 2, 12))
            .isEqualTo("Notebook (2/12)")
    }
}
