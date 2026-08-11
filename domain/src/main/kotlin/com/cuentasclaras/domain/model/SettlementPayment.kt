package com.cuentasclaras.domain.model

import java.time.Instant
import java.time.YearMonth

@JvmInline
value class SettlementPaymentId(val value: String) {
    init {
        require(value.isNotBlank()) { "SettlementPaymentId must not be blank" }
    }
}

/**
 * Recorded transfer of money between members.
 * Separate from expense-derived balances / suggested transfers.
 */
data class SettlementPayment(
    val id: SettlementPaymentId,
    val groupId: GroupId,
    val fromUserId: UserId,
    val toUserId: UserId,
    val amount: Money,
    val period: YearMonth,
    val createdBy: UserId,
    val createdAt: Instant,
) {
    init {
        require(fromUserId != toUserId) { "Payment parties must differ" }
        require(amount.amountMinor > 0L) { "Payment amount must be greater than zero" }
    }
}
