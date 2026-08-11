package com.cuentasclaras.domain.model

/**
 * Monetary amount in the smallest currency unit (e.g. cents).
 * Never use floating-point for money.
 */
data class Money(
    val amountMinor: Long,
    val currency: Currency,
) {
    init {
        require(currency.code.isNotBlank()) { "Currency code must not be blank" }
    }

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return copy(amountMinor = amountMinor + other.amountMinor)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return copy(amountMinor = amountMinor - other.amountMinor)
    }

    operator fun unaryMinus(): Money = copy(amountMinor = -amountMinor)

    fun isZero(): Boolean = amountMinor == 0L

    fun isPositive(): Boolean = amountMinor > 0L

    fun isNegative(): Boolean = amountMinor < 0L

    fun abs(): Money = copy(amountMinor = kotlin.math.abs(amountMinor))

    private fun requireSameCurrency(other: Money) {
        require(currency == other.currency) {
            "Currency mismatch: ${currency.code} vs ${other.currency.code}"
        }
    }

    companion object {
        fun zero(currency: Currency): Money = Money(0L, currency)
    }
}
