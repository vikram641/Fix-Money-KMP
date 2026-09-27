package com.fixmoney.shared.platform

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate

actual fun currentHour(): Int =
    NSCalendar.currentCalendar.component(NSCalendarUnitHour, fromDate = NSDate()).toInt()

actual fun currentMonthApi(): String {
    val calendar = NSCalendar.currentCalendar
    val now = NSDate()
    val year = calendar.component(NSCalendarUnitYear, fromDate = now).toInt()
    val month = calendar.component(NSCalendarUnitMonth, fromDate = now).toInt()
    return "$year-${month.toString().padStart(2, '0')}"
}
