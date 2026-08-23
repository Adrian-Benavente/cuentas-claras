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
     * `.` is always a thousands separator; `,` is always the decimal separator.
     */
    fun parseToMinor(input: String, currency: Currency = Currency.ARS): Long? {
        val cleaned = input.trim()
            .replace("$", "")
            .replace(" ", "")
            .replace(".", "")
            .replace(Regex("[^0-9,]"), "")
        if (cleaned.isBlank() || cleaned == ",") return null

        val normalized = cleaned.replace(',', '.')
        val parts = normalized.split('.')
        val major = parts[0].toLongOrNull() ?: return null
        val minorPart = parts.getOrNull(1).orEmpty().padEnd(2, '0').take(2)
        val cents = if (minorPart.isEmpty()) 0L else minorPart.toLongOrNull() ?: return null
        if (major < 0 || cents < 0) return null
        return major * 100 + cents
    }

    /**
     * Digits and at most one comma (cents). Typed or pasted `.` is dropped.
     */
    fun sanitizeAmountInput(raw: String): String {
        val withoutDots = raw.replace(".", "")
        val out = StringBuilder()
        var seenComma = false
        var decimalDigits = 0
        for (char in withoutDots) {
            when {
                char.isDigit() -> {
                    if (seenComma) {
                        if (decimalDigits < 2) {
                            out.append(char)
                            decimalDigits++
                        }
                    } else {
                        out.append(char)
                    }
                }
                char == ',' && !seenComma -> {
                    out.append(',')
                    seenComma = true
                }
            }
        }
        return out.toString()
    }

    /**
     * Groups the integer part with `.` for display. [sanitized] must have no thousand dots.
     */
    fun groupThousands(sanitized: String): String {
        val commaIndex = sanitized.indexOf(',')
        val intPart = if (commaIndex >= 0) sanitized.substring(0, commaIndex) else sanitized
        val decPart = if (commaIndex >= 0) sanitized.substring(commaIndex) else ""
        if (intPart.isEmpty()) return decPart
        val grouped = intPart.reversed().chunked(3).joinToString(".").reversed()
        return grouped + decPart
    }

    fun formatMajorInput(amountMinor: Long): String {
        val major = amountMinor / 100
        val cents = abs(amountMinor % 100)
        return if (cents == 0L) major.toString() else "%d,%02d".format(major, cents)
    }
}
