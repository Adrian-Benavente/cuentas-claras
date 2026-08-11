package com.cuentasclaras.app.util

import com.cuentasclaras.domain.model.Currency
import com.cuentasclaras.domain.model.Money
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

object MoneyFormatter {
    private val arLocale = Locale.forLanguageTag("es-AR")

    fun format(money: Money, withSign: Boolean = false): String {
        val major = money.amountMinor / 100.0
        val formatted = NumberFormat.getCurrencyInstance(arLocale).apply {
            currency = java.util.Currency.getInstance(money.currency.code)
            maximumFractionDigits = 2
            minimumFractionDigits = 2
        }.format(abs(major))

        return when {
            withSign && money.amountMinor > 0 -> "+$formatted"
            withSign && money.amountMinor < 0 -> "-$formatted"
            money.amountMinor < 0 -> "-$formatted"
            else -> formatted
        }
    }

    /**
     * Parses user input like "80000", "80000,50", "1.250,50" into minor units.
     */
    fun parseToMinor(input: String, currency: Currency = Currency.ARS): Long? {
        val cleaned = input.trim()
            .replace("$", "")
            .replace(" ", "")
            .replace(Regex("[^0-9,.]"), "")
        if (cleaned.isBlank()) return null

        val normalized = when {
            cleaned.contains(',') && cleaned.contains('.') ->
                cleaned.replace(".", "").replace(',', '.')
            cleaned.contains(',') ->
                cleaned.replace(',', '.')
            else -> cleaned
        }

        val parts = normalized.split('.')
        val major = parts[0].toLongOrNull() ?: return null
        val minorPart = parts.getOrNull(1).orEmpty().padEnd(2, '0').take(2)
        val cents = if (minorPart.isEmpty()) 0L else minorPart.toLongOrNull() ?: return null
        if (major < 0 || cents < 0) return null
        return major * 100 + cents
    }

    fun formatMajorInput(amountMinor: Long): String {
        val major = amountMinor / 100
        val cents = abs(amountMinor % 100)
        return if (cents == 0L) major.toString() else "%d,%02d".format(major, cents)
    }
}
