package com.fixmoney.shared.platform

import java.util.Calendar

actual fun currentHour(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

actual fun currentMonthApi(): String {
    val c = Calendar.getInstance()
    val month = c.get(Calendar.MONTH) + 1
    return "${c.get(Calendar.YEAR)}-${month.toString().padStart(2, '0')}"
}
