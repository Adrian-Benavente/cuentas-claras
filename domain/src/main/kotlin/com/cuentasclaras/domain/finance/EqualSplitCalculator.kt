package com.cuentasclaras.domain.finance

import com.cuentasclaras.domain.model.Currency
import com.cuentasclaras.domain.model.ExpenseSplit
import com.cuentasclaras.domain.model.Money
import com.cuentasclaras.domain.model.SplitType
import com.cuentasclaras.domain.model.UserId

/**
 * Equal split with deterministic remainder distribution.
 *
 * Participants are sorted by [UserId.value] ascending.
 * Each gets `base = amount / n`; the first `amount % n` participants
 * receive one extra minor unit so that `sum(shares) == amount`.
 */
object EqualSplitCalculator {

    fun split(
        amountMinor: Long,
        currency: Currency,
        participantIds: List<UserId>,
    ): List<ExpenseSplit> {
        require(amountMinor > 0L) { "Amount must be greater than zero" }
        require(participantIds.isNotEmpty()) { "At least one participant is required" }
        require(participantIds.distinct().size == participantIds.size) {
            "Participant IDs must be unique"
        }

        val sorted = participantIds.sortedBy { it.value }
        val n = sorted.size
        val base = amountMinor / n
        val remainder = amountMinor % n

        return sorted.mapIndexed { index, userId ->
            val shareMinor = base + if (index < remainder) 1L else 0L
            ExpenseSplit(
                userId = userId,
                splitType = SplitType.EQUAL,
                share = Money(shareMinor, currency),
            )
        }
    }

    fun split(amount: Money, participantIds: List<UserId>): List<ExpenseSplit> =
        split(amount.amountMinor, amount.currency, participantIds)
}
