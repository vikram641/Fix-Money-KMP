package com.fixmoney.shared.platform

/**
 * The two date facts the UI needs. Deliberately `expect`/`actual` rather than a
 * kotlinx-datetime dependency: every third-party KMP library so far has had to
 * clear an `iosX64` + Kotlin-version gate, and this is 20 lines of platform code.
 * Swap for kotlinx-datetime if real date arithmetic is ever needed.
 */
expect fun currentHour(): Int

/** "2026-09" - the `yyyy-MM` key the old app used for its month column. */
expect fun currentMonthApi(): String

/** Epoch milliseconds now - used for `localCreatedAt` and expense ids. */
expect fun currentTimeMillis(): Long

/** "2026-09-27" for today, the `yyyy-MM-dd` format the old app stored. */
expect fun todayIso(): String
