package com.cuentasclaras.app.data.mapper

import com.cuentasclaras.app.data.remote.ExpenseDto
import com.cuentasclaras.app.data.remote.ExpenseSplitDto
import com.cuentasclaras.app.data.remote.GroupDto
import com.cuentasclaras.app.data.remote.GroupMemberDto
import com.cuentasclaras.app.data.remote.ProfileDto
import com.cuentasclaras.app.data.remote.SettlementPaymentDto
import com.cuentasclaras.domain.model.MemberRole
import com.cuentasclaras.domain.model.SplitType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class MappersTest {
    @Test
    fun groupDto_toDomain() {
        val group = GroupDto(
            id = "g1",
            name = "Casa",
            currency = "ARS",
            inviteCode = "AB12CD",
            createdBy = "u1",
            createdAt = "2026-08-11T12:00:00Z",
            updatedAt = "2026-08-11T13:00:00Z",
        ).toDomain()

        assertThat(group.id.value).isEqualTo("g1")
        assertThat(group.name).isEqualTo("Casa")
        assertThat(group.currency.code).isEqualTo("ARS")
        assertThat(group.inviteCode).isEqualTo("AB12CD")
        assertThat(group.createdBy.value).isEqualTo("u1")
    }

    @Test
    fun groupMemberDto_mapsOwnerRoleAndDisplayName() {
        val member = GroupMemberDto(
            groupId = "g1",
            userId = "u1",
            role = "OWNER",
            joinedAt = "2026-08-11T12:00:00Z",
            profiles = ProfileDto(id = "u1", displayName = "Ada"),
        ).toDomain()

        assertThat(member.role).isEqualTo(MemberRole.OWNER)
        assertThat(member.displayName).isEqualTo("Ada")
    }

    @Test
    fun expenseDto_toDomain_withSplitsAndMoney() {
        val expense = ExpenseDto(
            id = "e1",
            groupId = "g1",
            description = "Super",
            amountMinor = 10_000L,
            currency = "ARS",
            paidBy = "u1",
            expenseDate = "2026-08-11",
            createdBy = "u1",
            createdAt = "2026-08-11T12:00:00Z",
            updatedAt = "2026-08-11T12:00:00Z",
            splits = listOf(
                ExpenseSplitDto(
                    userId = "u1",
                    splitType = "EQUAL",
                    shareAmountMinor = 5_000L,
                ),
                ExpenseSplitDto(
                    userId = "u2",
                    splitType = "EQUAL",
                    shareAmountMinor = 5_000L,
                ),
            ),
        ).toDomain()

        assertThat(expense.amount.amountMinor).isEqualTo(10_000L)
        assertThat(expense.date).isEqualTo(LocalDate.of(2026, 8, 11))
        assertThat(expense.period).isEqualTo(YearMonth.of(2026, 8))
        assertThat(expense.splits).hasSize(2)
        assertThat(expense.splits.map { it.splitType }.toSet()).containsExactly(SplitType.EQUAL)
        assertThat(expense.splits.sumOf { it.share.amountMinor }).isEqualTo(10_000L)
    }

    @Test
    fun settlementPaymentDto_toDomain_period() {
        val payment = SettlementPaymentDto(
            id = "p1",
            groupId = "g1",
            fromUserId = "u2",
            toUserId = "u1",
            amountMinor = 4_000L,
            currency = "ARS",
            periodYear = 2026,
            periodMonth = 8,
            createdBy = "u2",
            createdAt = "2026-08-11T12:00:00Z",
        ).toDomain()

        assertThat(payment.period).isEqualTo(YearMonth.of(2026, 8))
        assertThat(payment.amount.amountMinor).isEqualTo(4_000L)
        assertThat(payment.fromUserId.value).isEqualTo("u2")
        assertThat(payment.toUserId.value).isEqualTo("u1")
    }
}
