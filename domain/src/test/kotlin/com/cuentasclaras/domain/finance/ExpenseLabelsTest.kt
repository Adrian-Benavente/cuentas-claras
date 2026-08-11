package com.cuentasclaras.domain.finance

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ExpenseLabelsTest {

    @Test
    fun title_categoryOnly() {
        assertThat(ExpenseLabels.title("Luz", "")).isEqualTo("Luz")
    }

    @Test
    fun title_categoryAndNote() {
        assertThat(ExpenseLabels.title("Luz", "factura marzo"))
            .isEqualTo("Luz · factura marzo")
    }

    @Test
    fun title_legacyNoteOnly() {
        assertThat(ExpenseLabels.title(null, "Supermercado")).isEqualTo("Supermercado")
    }

    @Test
    fun title_withInstallment() {
        assertThat(ExpenseLabels.title("Tarjeta", "notebook", 3, 12))
            .isEqualTo("Tarjeta · notebook (3/12)")
    }

    @Test
    fun title_installmentWithoutNote() {
        assertThat(ExpenseLabels.title("Tarjeta", "", 1, 6))
            .isEqualTo("Tarjeta (1/6)")
    }

    @Test
    fun title_doesNotDoubleInstallmentSuffix() {
        assertThat(ExpenseLabels.title(null, "Notebook (2/12)", 2, 12))
            .isEqualTo("Notebook (2/12)")
    }
}
