package com.cuentasclaras.domain.finance

import com.cuentasclaras.domain.model.Currency
import com.cuentasclaras.domain.model.Expense
import com.cuentasclaras.domain.model.ExpenseDraft
import com.cuentasclaras.domain.model.ExpenseId
import com.cuentasclaras.domain.model.GroupId
import com.cuentasclaras.domain.model.Money
import com.cuentasclaras.domain.model.UserId
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class FinancialDomainTest {

    private val ars = Currency.ARS
    private val groupId = GroupId("group-1")
    private val adrian = UserId("user-adrian")
    private val pareja = UserId("user-pareja")
    private val carlos = UserId("user-carlos")
    private val diana = UserId("user-diana")
    private val now = Instant.parse("2026-08-11T12:00:00Z")
    private val today = LocalDate.of(2026, 8, 11)

    private fun money(minor: Long) = Money(minor, ars)

    private fun expense(
        id: String,
        amountMinor: Long,
        paidBy: UserId,
        participants: List<UserId>,
        description: String = "Gasto",
        date: LocalDate = today,
    ): Expense {
        val draft = ExpenseDraft(
            groupId = groupId,
            description = description,
            amount = money(amountMinor),
            paidBy = paidBy,
            date = date,
            createdBy = paidBy,
            participantIds = participants,
        )
        return ExpenseFactory.createFromDraft(draft, ExpenseId(id), now)
    }

    private fun balancesOf(expenses: List<Expense>, members: List<UserId>) =
        BalanceCalculator.calculate(expenses, members, ars)

    private fun balanceOf(expenses: List<Expense>, members: List<UserId>, userId: UserId): Long =
        balancesOf(expenses, members).first { it.userId == userId }.balance.amountMinor

    // --- Equal split ---

    @Test
    fun equalSplit_twoPeople_dividesExactly() {
        val splits = EqualSplitCalculator.split(8_000_000L, ars, listOf(adrian, pareja))
        assertThat(splits).hasSize(2)
        assertThat(splits.sumOf { it.share.amountMinor }).isEqualTo(8_000_000L)
        assertThat(splits.map { it.share.amountMinor }.toSet()).containsExactly(4_000_000L)
    }

    @Test
    fun equalSplit_remainder_goesToFirstSortedUserIds() {
        // 100 cents / 3 → 34, 33, 33 with remainder to first by userId
        val a = UserId("a")
        val b = UserId("b")
        val c = UserId("c")
        val splits = EqualSplitCalculator.split(100L, ars, listOf(c, a, b))
        assertThat(splits.map { it.userId to it.share.amountMinor })
            .containsExactly(
                a to 34L,
                b to 33L,
                c to 33L,
            )
            .inOrder()
        assertThat(splits.sumOf { it.share.amountMinor }).isEqualTo(100L)
    }

    @Test
    fun equalSplit_rejectsZeroAmount() {
        assertThrows(IllegalArgumentException::class.java) {
            EqualSplitCalculator.split(0L, ars, listOf(adrian))
        }
    }

    @Test
    fun equalSplit_singleMember_getsFullAmount() {
        val splits = EqualSplitCalculator.split(5_000L, ars, listOf(adrian))
        assertThat(splits).hasSize(1)
        assertThat(splits.single().share.amountMinor).isEqualTo(5_000L)
    }

    // --- Case 1: two people, one expense ---

    @Test
    fun twoPeople_oneExpense_payerHasPositiveBalance() {
        val expenses = listOf(
            expense("e1", 8_000_000L, adrian, listOf(adrian, pareja), "Supermercado"),
        )
        val members = listOf(adrian, pareja)

        assertThat(balanceOf(expenses, members, adrian)).isEqualTo(4_000_000L)
        assertThat(balanceOf(expenses, members, pareja)).isEqualTo(-4_000_000L)
        assertThat(balancesOf(expenses, members).sumOf { it.balance.amountMinor }).isEqualTo(0L)

        val transfers = SettlementCalculator.calculate(balancesOf(expenses, members))
        assertThat(transfers).hasSize(1)
        assertThat(transfers.single().fromUserId).isEqualTo(pareja)
        assertThat(transfers.single().toUserId).isEqualTo(adrian)
        assertThat(transfers.single().amount.amountMinor).isEqualTo(4_000_000L)
    }

    // --- Case 2: two people, multiple expenses ---

    @Test
    fun twoPeople_multipleExpenses_accumulatesBalances() {
        val members = listOf(adrian, pareja)
        val expenses = listOf(
            expense("e1", 8_000_000L, adrian, members),
            expense("e2", 3_200_000L, pareja, members),
            expense("e3", 2_500_000L, adrian, members),
        )
        // Total 13_700_000 / 2 = 6_850_000 each
        // Adrian paid 10_500_000 → balance +3_650_000
        // Pareja paid 3_200_000 → balance -3_650_000
        assertThat(balanceOf(expenses, members, adrian)).isEqualTo(3_650_000L)
        assertThat(balanceOf(expenses, members, pareja)).isEqualTo(-3_650_000L)
    }

    // --- Case 3: three people ---

    @Test
    fun threePeople_onePayer_othersOweShare() {
        val members = listOf(adrian, pareja, carlos)
        val expenses = listOf(expense("e1", 15_000L, adrian, members))
        // 15000/3 = 5000 each; Adrian +10000, others -5000
        assertThat(balanceOf(expenses, members, adrian)).isEqualTo(10_000L)
        assertThat(balanceOf(expenses, members, pareja)).isEqualTo(-5_000L)
        assertThat(balanceOf(expenses, members, carlos)).isEqualTo(-5_000L)
    }

    // --- Case 4: four or more ---

    @Test
    fun fourPeople_balancesAndSettlementsConserveMoney() {
        val members = listOf(adrian, pareja, carlos, diana)
        val expenses = listOf(
            expense("e1", 10_000L, adrian, members),
            expense("e2", 4_000L, pareja, members),
            expense("e3", 2_000L, carlos, members),
        )
        val balances = balancesOf(expenses, members)
        assertThat(balances.sumOf { it.balance.amountMinor }).isEqualTo(0L)

        val transfers = SettlementCalculator.calculate(balances)
        assertThat(transfers.sumOf { it.amount.amountMinor })
            .isEqualTo(balances.filter { it.balance.isPositive() }.sumOf { it.balance.amountMinor })

        val after = applyTransfers(balances.associate { it.userId to it.balance.amountMinor }.toMutableMap(), transfers)
        assertThat(after.values.all { it == 0L }).isTrue()
    }

    // --- Case 5: one person pays everything ---

    @Test
    fun onePersonPaysAll_othersAreDebtors() {
        val members = listOf(adrian, pareja, carlos)
        val expenses = listOf(
            expense("e1", 9_000L, adrian, members),
            expense("e2", 6_000L, adrian, members),
        )
        assertThat(balanceOf(expenses, members, adrian)).isEqualTo(10_000L)
        assertThat(balanceOf(expenses, members, pareja)).isEqualTo(-5_000L)
        assertThat(balanceOf(expenses, members, carlos)).isEqualTo(-5_000L)
    }

    // --- Case 6: everyone pays the same ---

    @Test
    fun everyonePaysSame_allBalancesZero() {
        val members = listOf(adrian, pareja)
        val expenses = listOf(
            expense("e1", 4_000L, adrian, members),
            expense("e2", 4_000L, pareja, members),
        )
        assertThat(balanceOf(expenses, members, adrian)).isEqualTo(0L)
        assertThat(balanceOf(expenses, members, pareja)).isEqualTo(0L)
        assertThat(SettlementCalculator.calculate(balancesOf(expenses, members))).isEmpty()
    }

    // --- Case 7: different payers ---

    @Test
    fun differentPayers_threePeople_settlementClearsBalances() {
        val a = UserId("A")
        val b = UserId("B")
        val c = UserId("C")
        val members = listOf(a, b, c)
        val expenses = listOf(
            expense("e1", 200L, a, members),
            expense("e2", 100L, b, members),
        )
        val balances = balancesOf(expenses, members)
        assertThat(balances.sumOf { it.balance.amountMinor }).isEqualTo(0L)
        assertThat(balanceOf(expenses, members, b)).isEqualTo(0L)

        val transfers = SettlementCalculator.calculate(balances)
        assertThat(transfers).hasSize(1)
        assertThat(transfers.single().fromUserId).isEqualTo(c)
        assertThat(transfers.single().toUserId).isEqualTo(a)

        val after = applyTransfers(
            balances.associate { it.userId to it.balance.amountMinor }.toMutableMap(),
            transfers,
        )
        assertThat(after.values.all { it == 0L }).isTrue()
    }

    @Test
    fun differentPayers_nonDivisibleAmounts_remaindersDoNotBreakInvariants() {
        val a = UserId("A")
        val b = UserId("B")
        val c = UserId("C")
        val members = listOf(a, b, c)
        val expenses = listOf(
            expense("e1", 10_000L, a, members),
            expense("e2", 5_000L, b, members),
        )
        val balances = balancesOf(expenses, members)
        assertThat(balances.sumOf { it.balance.amountMinor }).isEqualTo(0L)
        val after = applyTransfers(
            balances.associate { it.userId to it.balance.amountMinor }.toMutableMap(),
            SettlementCalculator.calculate(balances),
        )
        assertThat(after.values.all { it == 0L }).isTrue()
    }

    // --- Case 8–9: zero / mixed ---

    @Test
    fun emptyExpenses_allBalancesZero() {
        val members = listOf(adrian, pareja)
        val balances = balancesOf(emptyList(), members)
        assertThat(balances.all { it.balance.isZero() }).isTrue()
    }

    // --- Case 10–11: multiple creditors / debtors ---

    @Test
    fun multipleCreditorsAndDebtors_settlementsClearAll() {
        val a = UserId("A")
        val b = UserId("B")
        val c = UserId("C")
        val d = UserId("D")
        // Direct balances matching brief example: +100, +50, -80, -70
        // Construct via expenses carefully:
        // Total paid must equal total owed.
        // Use artificial construction through MemberBalance path via expenses:
        // Members A,B,C,D share equally expenses paid by A and B.
        val members = listOf(a, b, c, d)
        val expenses = listOf(
            expense("e1", 400L, a, members), // each owes 100; A +300
            expense("e2", 200L, b, members), // each owes 50; B +150; A +250 net so far from e1+e2 wait
        )
        // After e1: A+300, B-100, C-100, D-100
        // After e2: A+250, B+50, C-150, D-150  (paid 200, owed +50 each)
        // Not the brief numbers — use SettlementCalculator with constructed balances via a helper expense set
        // that yields +100,+50,-80,-70:
        // total positive 150 = total negative 150.
        // We'll test the calculator directly with MemberBalance list for the brief example.
        val balances = listOf(
            memberBalance(a, paid = 100, owed = 0),
            memberBalance(b, paid = 50, owed = 0),
            memberBalance(c, paid = 0, owed = 80),
            memberBalance(d, paid = 0, owed = 70),
        )
        // Fix: MemberBalance requires balance = paid - owed, and sum must be 0.
        // A: paid 100 owed 0 → +100
        // B: paid 50 owed 0 → +50
        // C: paid 0 owed 80 → -80
        // D: paid 0 owed 70 → -70
        // But sum of paid (150) != sum of owed (150) — wait 100+50=150, 80+70=150 OK.

        val transfers = SettlementCalculator.calculate(balances)
        assertThat(transfers).isNotEmpty()
        val after = applyTransfers(
            balances.associate { it.userId to it.balance.amountMinor }.toMutableMap(),
            transfers,
        )
        assertThat(after.values.all { it == 0L }).isTrue()
        assertThat(transfers.none { it.toUserId == c || it.toUserId == d }).isTrue()
        assertThat(transfers.none { it.fromUserId == a || it.fromUserId == b }).isTrue()
    }

    private fun memberBalance(userId: UserId, paid: Long, owed: Long) =
        com.cuentasclaras.domain.model.MemberBalance(
            userId = userId,
            amountPaid = money(paid),
            amountOwed = money(owed),
            balance = money(paid - owed),
        )

    // --- Case 12: decimal amounts (minor units) ---

    @Test
    fun decimalAmounts_representedAsMinorUnits() {
        // $1250.50 ARS = 125050 minor
        val members = listOf(adrian, pareja)
        val expenses = listOf(expense("e1", 125_050L, adrian, members))
        assertThat(balanceOf(expenses, members, adrian)).isEqualTo(62_525L)
        assertThat(balanceOf(expenses, members, pareja)).isEqualTo(-62_525L)
    }

    // --- Case 13: zero amount rejected ---

    @Test
    fun zeroAmountExpense_rejectedByFactory() {
        assertThrows(IllegalArgumentException::class.java) {
            expense("e1", 0L, adrian, listOf(adrian, pareja))
        }
    }

    // --- Case 14: single member group ---

    @Test
    fun singleMemberGroup_balanceAlwaysZero() {
        val members = listOf(adrian)
        val expenses = listOf(expense("e1", 10_000L, adrian, members))
        assertThat(balanceOf(expenses, members, adrian)).isEqualTo(0L)
        assertThat(SettlementCalculator.calculate(balancesOf(expenses, members))).isEmpty()
    }

    // --- Case 15: delete expense ---

    @Test
    fun deletingExpense_recalculatesFromSource() {
        val members = listOf(adrian, pareja)
        val e1 = expense("e1", 8_000L, adrian, members)
        val e2 = expense("e2", 4_000L, pareja, members)
        val afterDelete = listOf(e1, e2).without(ExpenseId("e2"))
        assertThat(balanceOf(afterDelete, members, adrian)).isEqualTo(4_000L)
        assertThat(balanceOf(afterDelete, members, pareja)).isEqualTo(-4_000L)
    }

    // --- Case 16: edit expense ---

    @Test
    fun editingExpense_recalculatesFromSource() {
        val members = listOf(adrian, pareja)
        val original = expense("e1", 8_000L, adrian, members)
        val edited = expense("e1", 10_000L, pareja, members, description = "Editado")
        val list = listOf(original).replacing(edited)
        assertThat(balanceOf(list, members, pareja)).isEqualTo(5_000L)
        assertThat(balanceOf(list, members, adrian)).isEqualTo(-5_000L)
    }

    // --- Case 17: rounding edge cases ---

    @Test
    fun rounding_oneCentAmongMany_neverLosesRemainder() {
        val members = (1..7).map { UserId("u$it") }
        val splits = EqualSplitCalculator.split(1L, ars, members)
        assertThat(splits.sumOf { it.share.amountMinor }).isEqualTo(1L)
        assertThat(splits.count { it.share.amountMinor == 1L }).isEqualTo(1)
        assertThat(splits.count { it.share.amountMinor == 0L }).isEqualTo(6)
    }

    @Test
    fun periodSummary_filtersByMonth() {
        val members = listOf(adrian, pareja)
        val expenses = listOf(
            expense("e1", 8_000L, adrian, members, date = LocalDate.of(2026, 8, 5)),
            expense("e2", 4_000L, pareja, members, date = LocalDate.of(2026, 7, 20)),
        )
        val august = PeriodSummaryCalculator.summarize(
            expenses,
            members,
            ars,
            java.time.YearMonth.of(2026, 8),
        )
        assertThat(august.totalSpent.amountMinor).isEqualTo(8_000L)
        assertThat(august.memberBalances.first { it.userId == adrian }.balance.amountMinor)
            .isEqualTo(4_000L)
    }

    @Test
    fun periodSummary_includesFormerMemberFromExpenseSplits() {
        val whenTogether = listOf(adrian, pareja)
        val expenses = listOf(expense("e1", 10_000L, adrian, whenTogether))
        // pareja left the group: only adrian remains as active member
        val summary = PeriodSummaryCalculator.summarize(
            expenses = expenses,
            memberIds = listOf(adrian),
            currency = ars,
            period = java.time.YearMonth.of(2026, 8),
        )
        assertThat(summary.memberBalances.map { it.userId }).containsExactly(adrian, pareja)
        assertThat(summary.memberBalances.sumOf { it.balance.amountMinor }).isEqualTo(0L)
        assertThat(summary.memberBalances.first { it.userId == adrian }.balance.amountMinor)
            .isEqualTo(5_000L)
        assertThat(summary.memberBalances.first { it.userId == pareja }.balance.amountMinor)
            .isEqualTo(-5_000L)
        assertThat(summary.suggestedTransfers).hasSize(1)
        assertThat(summary.suggestedTransfers.single().fromUserId).isEqualTo(pareja)
        assertThat(summary.suggestedTransfers.single().toUserId).isEqualTo(adrian)
    }

    @Test
    fun periodSummary_twoActiveMembers_unchanged() {
        val members = listOf(adrian, pareja)
        val expenses = listOf(expense("e1", 8_000L, adrian, members))
        val summary = PeriodSummaryCalculator.summarize(expenses, members, ars)
        assertThat(summary.memberBalances).hasSize(2)
        assertThat(summary.memberBalances.sumOf { it.balance.amountMinor }).isEqualTo(0L)
    }

@Test
    fun money_rejectsCurrencyMismatch() {
        assertThrows(IllegalArgumentException::class.java) {
            money(100) + Money(50, Currency.USD)
        }
    }

    // --- Settlement payments ---

    @Test
    fun settlementPayment_exactMatch_clearsSuggestedTransfer() {
        val members = listOf(adrian, pareja)
        val expenses = listOf(expense("e1", 8_000_000L, adrian, members))
        val payment = settlementPayment(
            id = "p1",
            from = pareja,
            to = adrian,
            amountMinor = 4_000_000L,
            period = java.time.YearMonth.of(2026, 8),
        )
        val summary = PeriodSummaryCalculator.summarize(
            expenses = expenses,
            memberIds = members,
            currency = ars,
            period = java.time.YearMonth.of(2026, 8),
            payments = listOf(payment),
        )
        assertThat(summary.memberBalances.first { it.userId == pareja }.balance.amountMinor)
            .isEqualTo(-4_000_000L)
        assertThat(summary.suggestedTransfers).isEmpty()
        assertThat(summary.recordedPayments).hasSize(1)
    }

    @Test
    fun settlementPayment_doesNotChangeExpenseDerivedMemberRows() {
        val members = listOf(adrian, pareja)
        val expenses = listOf(expense("e1", 8_000L, adrian, members))
        val payment = settlementPayment(
            id = "p1",
            from = pareja,
            to = adrian,
            amountMinor = 4_000L,
            period = java.time.YearMonth.of(2026, 8),
        )
        val summary = PeriodSummaryCalculator.summarize(
            expenses,
            members,
            ars,
            java.time.YearMonth.of(2026, 8),
            listOf(payment),
        )
        assertThat(summary.memberBalances.first { it.userId == adrian }.amountPaid.amountMinor)
            .isEqualTo(8_000L)
        assertThat(summary.memberBalances.first { it.userId == pareja }.amountPaid.amountMinor)
            .isEqualTo(0L)
    }

    @Test
    fun settlementPayment_otherPeriod_doesNotAffectSuggestions() {
        val members = listOf(adrian, pareja)
        val expenses = listOf(
            expense("e1", 8_000L, adrian, members, date = LocalDate.of(2026, 8, 5)),
        )
        val julyPayment = settlementPayment(
            id = "p1",
            from = pareja,
            to = adrian,
            amountMinor = 4_000L,
            period = java.time.YearMonth.of(2026, 7),
        )
        val august = PeriodSummaryCalculator.summarize(
            expenses,
            members,
            ars,
            java.time.YearMonth.of(2026, 8),
            listOf(julyPayment),
        )
        assertThat(august.suggestedTransfers).hasSize(1)
        assertThat(august.recordedPayments).isEmpty()
    }

    @Test
    fun settlementPaymentApplicator_preservesZeroSum() {
        val a = UserId("A")
        val b = UserId("B")
        val c = UserId("C")
        val balances = listOf(
            memberBalance(a, paid = 200, owed = 100),
            memberBalance(b, paid = 100, owed = 100),
            memberBalance(c, paid = 0, owed = 100),
        )
        val payments = listOf(
            settlementPayment("p1", c, a, 50L, java.time.YearMonth.of(2026, 8)),
        )
        val outstanding = SettlementPaymentApplicator.apply(balances, payments)
        assertThat(outstanding.sumOf { it.balance.amountMinor }).isEqualTo(0L)
        val transfers = SettlementCalculator.calculate(outstanding)
        val after = applyTransfers(
            outstanding.associate { it.userId to it.balance.amountMinor }.toMutableMap(),
            transfers,
        )
        assertThat(after.values.all { it == 0L }).isTrue()
    }

    @Test
    fun settlementPayment_multiplePayments_clearAllSuggestions() {
        val a = UserId("A")
        val b = UserId("B")
        val c = UserId("C")
        val d = UserId("D")
        val balances = listOf(
            memberBalance(a, paid = 100, owed = 0),
            memberBalance(b, paid = 50, owed = 0),
            memberBalance(c, paid = 0, owed = 80),
            memberBalance(d, paid = 0, owed = 70),
        )
        val period = java.time.YearMonth.of(2026, 8)
        val outstandingBefore = SettlementPaymentApplicator.apply(balances, emptyList())
        val suggestions = SettlementCalculator.calculate(outstandingBefore)
        assertThat(suggestions).isNotEmpty()

        val payments = suggestions.mapIndexed { index, transfer ->
            settlementPayment(
                id = "p$index",
                from = transfer.fromUserId,
                to = transfer.toUserId,
                amountMinor = transfer.amount.amountMinor,
                period = period,
            )
        }
        val outstandingAfter = SettlementPaymentApplicator.apply(balances, payments)
        assertThat(SettlementCalculator.calculate(outstandingAfter)).isEmpty()
        assertThat(outstandingAfter.all { it.balance.isZero() }).isTrue()
    }

    private fun settlementPayment(
        id: String,
        from: UserId,
        to: UserId,
        amountMinor: Long,
        period: java.time.YearMonth,
    ) = com.cuentasclaras.domain.model.SettlementPayment(
        id = com.cuentasclaras.domain.model.SettlementPaymentId(id),
        groupId = groupId,
        fromUserId = from,
        toUserId = to,
        amount = money(amountMinor),
        period = period,
        createdBy = from,
        createdAt = now,
    )

    private fun applyTransfers(
        balances: MutableMap<UserId, Long>,
        transfers: List<com.cuentasclaras.domain.model.SuggestedTransfer>,
    ): Map<UserId, Long> {
        for (t in transfers) {
            balances[t.fromUserId] = balances.getValue(t.fromUserId) + t.amount.amountMinor
            balances[t.toUserId] = balances.getValue(t.toUserId) - t.amount.amountMinor
        }
        return balances
    }
}
