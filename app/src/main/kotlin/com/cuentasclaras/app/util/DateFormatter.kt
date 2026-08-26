package com.cuentasclaras.app.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter

object DateFormatter {
    private val expenseDate: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    fun format(date: LocalDate): String = date.format(expenseDate)
}
