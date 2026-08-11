package com.cuentasclaras.domain.finance

import com.cuentasclaras.domain.model.MemberBalance
import com.cuentasclaras.domain.model.Money
import com.cuentasclaras.domain.model.SettlementPayment
import com.cuentasclaras.domain.model.UserId

/**
 * Applies recorded settlement payments onto expense-derived balances
 * to obtain outstanding balances for suggestion calculation.
 *
 * A payment from A to B of X means A paid X to B:
 * - A's net balance increases by X (less debt / more credit)
 * - B's net balance decreases by X
 *
 * Does not change the authoritative expense rows shown in the UI;
 * returned balances are only for [SettlementCalculator].
 */
object SettlementPaymentApplicator {

    fun apply(
        balances: List<MemberBalance>,
        payments: List<SettlementPayment>,
    ): List<MemberBalance> {
        if (balances.isEmpty()) {
            require(payments.isEmpty()) { "Cannot apply payments without member balances" }
            return emptyList()
        }

        val currency = balances.first().balance.currency
        require(balances.all { it.balance.currency == currency }) {
            "All balances must share the same currency"
        }
        require(payments.all { it.amount.currency == currency }) {
            "Payment currency must match balance currency"
        }

        val memberIds = balances.map { it.userId }.toSet()
        payments.forEach { payment ->
            require(payment.fromUserId in memberIds) {
                "Payment fromUser must be a member: ${payment.fromUserId.value}"
            }
            require(payment.toUserId in memberIds) {
                "Payment toUser must be a member: ${payment.toUserId.value}"
            }
        }

        val nets = balances.associate { it.userId to it.balance.amountMinor }.toMutableMap()
        for (payment in payments) {
            nets[payment.fromUserId] =
                nets.getValue(payment.fromUserId) + payment.amount.amountMinor
            nets[payment.toUserId] =
                nets.getValue(payment.toUserId) - payment.amount.amountMinor
        }

        require(nets.values.sum() == 0L) {
            "Outstanding balances must sum to zero after payments"
        }

        return balances
            .sortedBy { it.userId.value }
            .map { balance ->
                outstandingMemberBalance(balance.userId, nets.getValue(balance.userId), currency)
            }
    }

    private fun outstandingMemberBalance(
        userId: UserId,
        netMinor: Long,
        currency: com.cuentasclaras.domain.model.Currency,
    ): MemberBalance {
        return if (netMinor >= 0L) {
            MemberBalance(
                userId = userId,
                amountPaid = Money(netMinor, currency),
                amountOwed = Money.zero(currency),
                balance = Money(netMinor, currency),
            )
        } else {
            MemberBalance(
                userId = userId,
                amountPaid = Money.zero(currency),
                amountOwed = Money(-netMinor, currency),
                balance = Money(netMinor, currency),
            )
        }
    }
}
