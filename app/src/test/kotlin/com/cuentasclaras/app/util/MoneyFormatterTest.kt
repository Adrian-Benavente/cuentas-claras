package com.cuentasclaras.app.util

import com.cuentasclaras.domain.model.Currency
import com.cuentasclaras.domain.model.Money
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MoneyFormatterTest {
    @Test
    fun parseToMinor_plainMajor() {
        assertThat(MoneyFormatter.parseToMinor("80000")).isEqualTo(8_000_000L)
    }

    @Test
    fun parseToMinor_commaCents() {
        assertThat(MoneyFormatter.parseToMinor("80000,50")).isEqualTo(8_000_050L)
    }

    @Test
    fun parseToMinor_thousandsAndCents() {
        assertThat(MoneyFormatter.parseToMinor("1.250,50")).isEqualTo(125_050L)
    }

    @Test
    fun parseToMinor_blankOrInvalid_returnsNull() {
        assertThat(MoneyFormatter.parseToMinor("")).isNull()
        assertThat(MoneyFormatter.parseToMinor("abc")).isNull()
    }

    @Test
    fun formatMajorInput_withoutCents() {
        assertThat(MoneyFormatter.formatMajorInput(8_000_000L)).isEqualTo("80000")
    }

    @Test
    fun formatMajorInput_withCents() {
        assertThat(MoneyFormatter.formatMajorInput(8_000_050L)).isEqualTo("80000,50")
    }

    @Test
    fun format_arsIncludesCurrencySymbol() {
        val formatted = MoneyFormatter.format(Money(10_000L, Currency.ARS))
        assertThat(formatted).contains("100")
        assertThat(formatted.contains("ARS") || formatted.contains("$")).isTrue()
    }
}
