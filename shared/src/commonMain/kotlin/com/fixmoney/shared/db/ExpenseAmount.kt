package com.fixmoney.shared.db

import kotlin.math.roundToLong

/**
 * Reads a stored amount, whatever the app that wrote it.
 *
 * This build only ever writes whole numbers ("450"), but the old app's amount
 * field was `numberDecimal` and stored the typed text, so an updated install can
 * hold "12.50", and older rows carry "₹1,250" or spaces. `toIntOrNull()` read all
 * of those as 0, which silently dropped them out of every total, budget and chart.
 * Rounded to the nearest unit, matching the old app's `Money.kt`.
 */
fun parseStoredAmount(raw: String): Int {
    val cleaned = raw.filter { it.isDigit() || it == '.' || it == '-' }
    val value = cleaned.toDoubleOrNull() ?: return 0
    if (value.isNaN() || value.isInfinite()) return 0
    return value.roundToLong().coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
}

/** The row's amount as a whole number of currency units. */
val ExpenseEntity.amountValue: Int get() = parseStoredAmount(amount)

/**
 * Whether this row counts towards totals shown in [currency]. Rows keep the
 * currency they were recorded in and nothing converts between currencies, so
 * a total must never add ₹ to $. A blank currency (very old rows) counts as
 * the profile's.
 */
fun ExpenseEntity.isIn(currency: String): Boolean =
    this.currency.isBlank() || this.currency.equals(currency, ignoreCase = true)

fun List<ExpenseEntity>.inCurrency(currency: String): List<ExpenseEntity> = filter { it.isIn(currency) }
