package com.cuentasclaras.domain.model

/**
 * ISO 4217 currency code. MVP supports ARS only in the product,
 * but the type remains open for future currencies.
 */
@JvmInline
value class Currency(val code: String) {
    init {
        require(code.length == 3) { "Currency code must be ISO 4217 (3 letters): $code" }
        require(code.all { it.isUpperCase() }) { "Currency code must be uppercase: $code" }
    }

    companion object {
        val ARS: Currency = Currency("ARS")
        val USD: Currency = Currency("USD")
        val EUR: Currency = Currency("EUR")
    }
}
