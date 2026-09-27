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
