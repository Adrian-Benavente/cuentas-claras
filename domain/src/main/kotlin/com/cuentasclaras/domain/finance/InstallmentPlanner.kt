package com.cuentasclaras.domain.finance

import java.time.LocalDate
import java.time.YearMonth

data class InstallmentSlice(
    val index: Int,
    val count: Int,
    val date: LocalDate,
    val amountMinor: Long,
) {
    init {
        require(index in 1..count) { "Installment index must be between 1 and count" }
        require(count >= 2) { "Installment count must be at least 2" }
        require(amountMinor > 0L) { "Installment amount must be greater than zero" }
    }
}

/**
 * Plans equal installment amounts (minor units) and monthly dates from a start date.
 *
 * Amount remainder: first `total % n` installments get +1 minor unit (on the full 1..N plan).
 * Dates: same day-of-month clamped to each month's length.
 */
object InstallmentPlanner {
    const val MIN_COUNT = 2
    const val MAX_COUNT = 48

    fun plan(
        totalMinor: Long,
        count: Int,
        startDate: LocalDate,
    ): List<InstallmentSlice> = planRemaining(
        totalMinor = totalMinor,
        count = count,
        startIndex = 1,
        startDate = startDate,
    )

    /**
     * Plans remaining installments starting at [startIndex] of [count].
     *
     * Amounts match the full 1..N plan; only indices [startIndex]..[count] are returned.
     * [startDate] is the date of cuota [startIndex]; later cuotas are +1 month each.
     */
    fun planRemaining(
        totalMinor: Long,
        count: Int,
        startIndex: Int,
        startDate: LocalDate,
    ): List<InstallmentSlice> {
        require(totalMinor > 0L) { "Total amount must be greater than zero" }
        require(count in MIN_COUNT..MAX_COUNT) {
            "Installment count must be between $MIN_COUNT and $MAX_COUNT"
        }
        require(startIndex in 1..count) {
            "Installment start index must be between 1 and count"
        }

        val base = totalMinor / count
        val remainder = totalMinor % count
        val startDay = startDate.dayOfMonth

        return (startIndex..count).map { index ->
            val amountOffset = index - 1
            val dateOffset = index - startIndex
            val amountMinor = base + if (amountOffset < remainder) 1L else 0L
            val period = YearMonth.from(startDate).plusMonths(dateOffset.toLong())
            val day = minOf(startDay, period.lengthOfMonth())
            InstallmentSlice(
                index = index,
                count = count,
                date = period.atDay(day),
                amountMinor = amountMinor,
            )
        }
    }

    fun labeledDescription(baseDescription: String, index: Int, count: Int): String =
        "${baseDescription.trim()} ($index/$count)"
}
