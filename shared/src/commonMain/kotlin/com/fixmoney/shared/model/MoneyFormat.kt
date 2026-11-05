package com.fixmoney.shared.model

/**
 * Which currency the amounts on screen are in, read when a number is formatted.
 * Set once from `App.kt` to the profile's currency; a plain function rather than
 * a CompositionLocal because [formatIndian] is called from non-composable code.
 */
object MoneyFormat {
    var currencyCode: () -> String = { "INR" }

    /** Lakh/crore grouping is South Asian; everyone else groups in thousands. */
    private val SOUTH_ASIAN = setOf("INR", "PKR", "BDT", "NPR", "LKR")

    val indianGrouping: Boolean get() = currencyCode().uppercase() in SOUTH_ASIAN
}

/**
 * Digit grouping for the profile's currency: Indian (`1,00,000`) for rupee
 * currencies, Western (`100,000`) for the rest. It used to be Indian always, so a
 * USD user saw "$1,00,00,629".
 *
 */
fun formatGrouped(value: Long): String {
    val digits = (if (value < 0) -value else value).toString()
    val grouped = when {
        digits.length <= 3 -> digits
        MoneyFormat.indianGrouping -> {
            val last3 = digits.takeLast(3)
            val head = digits.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
            "$head,$last3"
        }
        else -> digits.reversed().chunked(3).joinToString(",").reversed()
    }
    return if (value < 0) "-$grouped" else grouped
}

/**
 * 12480 -> "₹12.4k", 940 -> "₹940" - the donut centre has no room for more.
 * Lakh/crore suffixes only for rupee currencies; "$1.0cr" meant nothing to a USD user.
 */
fun compactAmount(symbol: String, value: Int): String = when {
    !MoneyFormat.indianGrouping -> when {
        value >= 1_000_000_000 -> "$symbol${value / 1_000_000_000}.${(value % 1_000_000_000) / 100_000_000}B"
        value >= 1_000_000 -> "$symbol${value / 1_000_000}.${(value % 1_000_000) / 100_000}M"
        value >= 1_000 -> "$symbol${value / 1_000}.${(value % 1_000) / 100}k"
        else -> "$symbol$value"
    }
    value >= 10_000_000 -> "$symbol${value / 10_000_000}.${(value % 10_000_000) / 1_000_000}cr"
    value >= 100_000 -> "$symbol${value / 100_000}.${(value % 100_000) / 10_000}L"
    value >= 1_000 -> "$symbol${value / 1_000}.${(value % 1_000) / 100}k"
    else -> "$symbol$value"
}
