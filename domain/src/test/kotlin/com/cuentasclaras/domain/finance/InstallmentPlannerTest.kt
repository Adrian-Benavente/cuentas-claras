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

    @Test
    fun planRemaining_midSeries_usesFullPlanAmountsAndRebasesDates() {
        val full = InstallmentPlanner.plan(12_000L, 12, LocalDate.of(2026, 1, 10))
        val remaining = InstallmentPlanner.planRemaining(
            totalMinor = 12_000L,
            count = 12,
            startIndex = 3,
            startDate = LocalDate.of(2026, 8, 10),
        )

        assertThat(remaining).hasSize(10)
        assertThat(remaining.map { it.index }).containsExactly(
            3, 4, 5, 6, 7, 8, 9, 10, 11, 12,
        ).inOrder()
        assertThat(remaining.map { it.amountMinor })
            .containsExactlyElementsIn(full.drop(2).map { it.amountMinor })
            .inOrder()
        assertThat(remaining.map { it.date }).containsExactly(
            LocalDate.of(2026, 8, 10),
            LocalDate.of(2026, 9, 10),
            LocalDate.of(2026, 10, 10),
            LocalDate.of(2026, 11, 10),
            LocalDate.of(2026, 12, 10),
            LocalDate.of(2027, 1, 10),
            LocalDate.of(2027, 2, 10),
            LocalDate.of(2027, 3, 10),
            LocalDate.of(2027, 4, 10),
            LocalDate.of(2027, 5, 10),
        ).inOrder()
    }

    @Test
    fun planRemaining_startIndexOne_matchesPlan() {
        val start = LocalDate.of(2026, 8, 15)
        val fromPlan = InstallmentPlanner.plan(10_000L, 3, start)
        val remaining = InstallmentPlanner.planRemaining(10_000L, 3, 1, start)
        assertThat(remaining).isEqualTo(fromPlan)
    }

    @Test
    fun planRemaining_startIndexEqualsCount_singleSlice() {
        val slices = InstallmentPlanner.planRemaining(
            totalMinor = 10_001L,
            count = 3,
            startIndex = 3,
            startDate = LocalDate.of(2026, 10, 1),
        )
        assertThat(slices).hasSize(1)
        assertThat(slices.single().index).isEqualTo(3)
        assertThat(slices.single().amountMinor).isEqualTo(3_333L)
        assertThat(slices.single().date).isEqualTo(LocalDate.of(2026, 10, 1))
    }

    @Test
    fun planRemaining_earlyRemainderDoesNotInflateLaterSlices() {
        // 10000 / 3 => 3334, 3333, 3333 — starting at 2 keeps base amounts
        val slices = InstallmentPlanner.planRemaining(
            totalMinor = 10_000L,
            count = 3,
            startIndex = 2,
            startDate = LocalDate.of(2026, 9, 1),
        )
        assertThat(slices.map { it.amountMinor }).containsExactly(3_333L, 3_333L).inOrder()
        assertThat(slices.sumOf { it.amountMinor }).isEqualTo(6_666L)
    }

    @Test
    fun planRemaining_rejectsInvalidStartIndex() {
        assertThrows(IllegalArgumentException::class.java) {
            InstallmentPlanner.planRemaining(100L, 3, 0, LocalDate.of(2026, 8, 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            InstallmentPlanner.planRemaining(100L, 3, 4, LocalDate.of(2026, 8, 1))
        }
    }
}
