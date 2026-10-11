package com.fixmoney.shared.calc

import kotlin.math.pow

/** One SIP projection. All amounts in whole currency units. */
data class SipResult(val futureValue: Long, val invested: Long, val returns: Long, val gainPercent: Int)

/**
 * Standard SIP future value, monthly compounding, contribution at the start of
 * each month (annuity due): `P * ((1+i)^n - 1) / i * (1+i)`.
 * Long throughout - ₹1,00,000 a month for 35 years at 30% is far past Int.MAX.
 */
fun sipProjection(monthly: Int, annualRatePercent: Double, years: Int): SipResult {
    val months = years * 12
    if (monthly <= 0 || months <= 0) return SipResult(0, 0, 0, 0)
    val invested = monthly.toLong() * months
    val i = annualRatePercent / 12.0 / 100.0
    val future = if (i == 0.0) invested else {
        val fv = monthly * (((1 + i).pow(months) - 1) / i) * (1 + i)
        if (fv >= Long.MAX_VALUE.toDouble()) Long.MAX_VALUE else fv.toLong()
    }
    val returns = (future - invested).coerceAtLeast(0)
    val gain = if (invested <= 0) 0 else ((returns.toDouble() * 100) / invested).coerceAtMost(Int.MAX_VALUE.toDouble()).toInt()
    return SipResult(future, invested, returns, gain)
}
