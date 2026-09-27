package com.fixmoney.shared.model

/**
 * The same 14 currencies the old app's `CurrencyConstants.CURRENCIES` listed.
 *
 * Symbols are hardcoded rather than looked up: the old app used
 * `java.util.Currency.getInstance(code).symbol`, which is JVM-only and has no
 * Kotlin/Native equivalent.
 */
data class Currency(val code: String, val name: String, val symbol: String)

object CurrencyConstants {

    val CURRENCIES = listOf(
        Currency("INR", "Indian Rupee", "₹"),
        Currency("USD", "US Dollar", "$"),
        Currency("EUR", "Euro", "€"),
        Currency("GBP", "British Pound", "£"),
        Currency("JPY", "Japanese Yen", "¥"),
        Currency("CAD", "Canadian Dollar", "CA$"),
        Currency("AUD", "Australian Dollar", "A$"),
        Currency("SGD", "Singapore Dollar", "S$"),
        Currency("AED", "UAE Dirham", "د.إ"),
        Currency("CNY", "Chinese Yuan", "CN¥"),
        Currency("CHF", "Swiss Franc", "CHF"),
        Currency("KRW", "South Korean Won", "₩"),
        Currency("MXN", "Mexican Peso", "MX$"),
        Currency("BRL", "Brazilian Real", "R$")
    )

    fun byCode(code: String): Currency =
        CURRENCIES.firstOrNull { it.code == code } ?: CURRENCIES.first()

    fun symbolFor(code: String): String = byCode(code).symbol
}
