package com.cuentasclaras.domain.finance

import com.cuentasclaras.domain.model.MemberBalance
import com.cuentasclaras.domain.model.Money
import com.cuentasclaras.domain.model.SuggestedTransfer
import com.cuentasclaras.domain.model.UserId
import kotlin.math.min

/**
 * Greedy matching of debtors to creditors.
 *
 * Produces a reasonable set of transfers that bring all balances to zero
 * without creating or destroying money.
 */
object SettlementCalculator {

    fun calculate(balances: List<MemberBalance>): List<SuggestedTransfer> {
        if (balances.isEmpty()) return emptyList()

        val currency = balances.first().balance.currency
        require(balances.all { it.balance.currency == currency }) {
            "All balances must share the same currency"
        }

        val sum = balances.sumOf { it.balance.amountMinor }
        require(sum == 0L) { "Sum of balances must be zero, was $sum" }

        data class MutableBalance(val userId: UserId, var amountMinor: Long)

        val debtors = balances
            .filter { it.balance.isNegative() }
            .map { MutableBalance(it.userId, -it.balance.amountMinor) }
            .sortedWith(compareByDescending<MutableBalance> { it.amountMinor }.thenBy { it.userId.value })
            .toMutableList()

        val creditors = balances
            .filter { it.balance.isPositive() }
            .map { MutableBalance(it.userId, it.balance.amountMinor) }
            .sortedWith(compareByDescending<MutableBalance> { it.amountMinor }.thenBy { it.userId.value })
            .toMutableList()

        val transfers = mutableListOf<SuggestedTransfer>()
        var i = 0
        var j = 0

        while (i < debtors.size && j < creditors.size) {
            val debtor = debtors[i]
            val creditor = creditors[j]
            val transferMinor = min(debtor.amountMinor, creditor.amountMinor)

            if (transferMinor > 0L) {
                transfers += SuggestedTransfer(
                    fromUserId = debtor.userId,
                    toUserId = creditor.userId,
                    amount = Money(transferMinor, currency),
                )
                debtor.amountMinor -= transferMinor
                creditor.amountMinor -= transferMinor
            }

            if (debtor.amountMinor == 0L) i++
            if (creditor.amountMinor == 0L) j++
        }

        return transfers
    }
}
