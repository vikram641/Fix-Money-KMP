package com.fixmoney.shared.platform

/**
 * Date arithmetic on the app's `yyyy-MM-dd` strings, in **common** Kotlin.
 *
 * `DateTime.kt` deliberately stayed at "the two facts the UI needs" and said to
 * reach for kotlinx-datetime if real arithmetic was ever required. It is now -
 * the week window on Home and the insight engine's rolling baselines both need
 * it - but the answer is still not a dependency: converting a civil date to a
 * day number and back is the whole job, it is exact, and it needs no `expect`
 * /`actual` and no klib that has to clear the `iosX64` + Kotlin-version gate.
 *
 * The two conversions are Howard Hinnant's `days_from_civil` /
 * `civil_from_days`, which are proleptic-Gregorian exact over any range this app
 * can see. Day 0 is 1970-01-01.
 */

/** "2026-10-03" -> day number, or null if it isn't a well-formed date. */
fun isoToEpochDay(iso: String): Long? {
    val parts = iso.split("-")
    if (parts.size != 3) return null
    val y = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    val d = parts[2].toIntOrNull() ?: return null
    if (m !in 1..12 || d !in 1..31) return null
    return epochDayOf(y, m, d)
}

private fun epochDayOf(year: Int, month: Int, day: Int): Long {
    val y = (if (month <= 2) year - 1 else year).toLong()
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val doy = (153 * (month + (if (month > 2) -3 else 9)) + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097 + doe - 719468
}

/** Day number -> "2026-10-03". */
fun epochDayToIso(epochDay: Long): String {
    var z = epochDay + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    var y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = mp + (if (mp < 10) 3 else -9)
    if (m <= 2) y += 1
    return "$y-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
}

/** Shifts an ISO date by whole days; returns it unchanged if it can't be parsed. */
fun plusDaysIso(iso: String, days: Int): String =
    isoToEpochDay(iso)?.let { epochDayToIso(it + days) } ?: iso

/** Inclusive day count between two ISO dates, or null if either is malformed. */
fun daysBetween(fromIso: String, toIso: String): Long? {
    val from = isoToEpochDay(fromIso) ?: return null
    val to = isoToEpochDay(toIso) ?: return null
    return to - from
}

/** 28/29/30/31 for a `yyyy-MM` key. */
fun daysInMonth(monthApi: String): Int {
    val parts = monthApi.split("-")
    val y = parts.getOrNull(0)?.toIntOrNull() ?: return 30
    val m = parts.getOrNull(1)?.toIntOrNull() ?: return 30
    return when (m) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if ((y % 4 == 0 && y % 100 != 0) || y % 400 == 0) 29 else 28
        else -> 30
    }
}

/** The day-of-month from an ISO date, or 1 if it can't be read. */
fun dayOfMonth(iso: String): Int = iso.split("-").getOrNull(2)?.toIntOrNull() ?: 1

/** The `yyyy-MM` key an ISO date falls in. */
fun monthOf(iso: String): String = iso.substringBeforeLast("-")
