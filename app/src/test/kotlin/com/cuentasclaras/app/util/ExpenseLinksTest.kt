package com.cuentasclaras.app.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ExpenseLinksTest {
    @Test
    fun uri_matchesCustomScheme() {
        assertThat(ExpenseLinks.uri("g1", "e2"))
            .isEqualTo("cuentasclaras://group/g1/expense/e2")
    }

    @Test
    fun parse_readsGroupAndExpense() {
        val link = ExpenseLinks.parse(
            scheme = "cuentasclaras",
            host = "group",
            pathSegments = listOf("g1", "expense", "e2"),
        )
        assertThat(link).isEqualTo(ExpenseDeepLink("g1", "e2"))
    }

    @Test
    fun parse_ignoresJoinLinks() {
        assertThat(
            ExpenseLinks.parse(
                scheme = "cuentasclaras",
                host = "join",
                pathSegments = listOf("AB12"),
            ),
        ).isNull()
    }
}
