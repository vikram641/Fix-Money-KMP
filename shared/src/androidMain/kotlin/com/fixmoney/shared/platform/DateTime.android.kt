package com.fixmoney.shared.platform

import java.util.Calendar

actual fun currentHour(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

actual fun currentMonthApi(): String {
    val c = Calendar.getInstance()
    val month = c.get(Calendar.MONTH) + 1
    return "${c.get(Calendar.YEAR)}-${month.toString().padStart(2, '0')}"
}

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun todayIso(): String {
    val c = Calendar.getInstance()
    val m = (c.get(Calendar.MONTH) + 1).toString().padStart(2, '0')
    val d = c.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
    return "${c.get(Calendar.YEAR)}-$m-$d"
}
