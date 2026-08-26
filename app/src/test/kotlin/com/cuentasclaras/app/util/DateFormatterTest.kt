package com.cuentasclaras.app.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class DateFormatterTest {
    @Test
    fun format_ddMMyyyy() {
        assertThat(DateFormatter.format(LocalDate.of(2026, 8, 26))).isEqualTo("26/08/2026")
        assertThat(DateFormatter.format(LocalDate.of(2026, 1, 5))).isEqualTo("05/01/2026")
    }
}
