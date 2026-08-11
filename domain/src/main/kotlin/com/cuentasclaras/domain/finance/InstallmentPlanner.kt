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
 * Amount remainder: first `total % n` installments get +1 minor unit.
 * Dates: same day-of-month clamped to each month's length.
 */
object InstallmentPlanner {
    const val MIN_COUNT = 2
    const val MAX_COUNT = 48

    fun plan(
        totalMinor: Long,
        count: Int,
        startDate: LocalDate,
    ): List<InstallmentSlice> {
        require(totalMinor > 0L) { "Total amount must be greater than zero" }
        require(count in MIN_COUNT..MAX_COUNT) {
            "Installment count must be between $MIN_COUNT and $MAX_COUNT"
        }

        val base = totalMinor / count
        val remainder = totalMinor % count
        val startDay = startDate.dayOfMonth

        return (0 until count).map { offset ->
            val amountMinor = base + if (offset < remainder) 1L else 0L
            val period = YearMonth.from(startDate).plusMonths(offset.toLong())
            val day = minOf(startDay, period.lengthOfMonth())
            InstallmentSlice(
                index = offset + 1,
                count = count,
                date = period.atDay(day),
                amountMinor = amountMinor,
            )
        }
    }

    fun labeledDescription(baseDescription: String, index: Int, count: Int): String =
        "${baseDescription.trim()} ($index/$count)"
}
