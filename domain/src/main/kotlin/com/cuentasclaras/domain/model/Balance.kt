package com.cuentasclaras.domain.model

data class MemberBalance(
    val userId: UserId,
    val amountPaid: Money,
    val amountOwed: Money,
    val balance: Money,
) {
    init {
        require(amountPaid.currency == amountOwed.currency)
        require(amountPaid.currency == balance.currency)
        require(balance.amountMinor == amountPaid.amountMinor - amountOwed.amountMinor) {
            "balance must equal amountPaid - amountOwed"
        }
    }
}

/**
 * Suggested transfer to bring balances toward zero.
 * This is NOT a recorded payment — only a recommendation derived from balances.
 */
data class SuggestedTransfer(
    val fromUserId: UserId,
    val toUserId: UserId,
    val amount: Money,
) {
    init {
        require(fromUserId != toUserId) { "Transfer parties must differ" }
        require(amount.amountMinor > 0L) { "Transfer amount must be positive" }
    }
}

data class PeriodSummary(
    val totalSpent: Money,
    /** Expense-derived balances (ignores recorded settlement payments). */
    val memberBalances: List<MemberBalance>,
    /** Pending transfers after applying recorded payments for the period. */
    val suggestedTransfers: List<SuggestedTransfer>,
    /** Settlement payments recorded for this period. */
    val recordedPayments: List<SettlementPayment> = emptyList(),
)
