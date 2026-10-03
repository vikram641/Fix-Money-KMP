package com.fixmoney.shared.platform

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.NSTimeInterval
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.NSDateFormatter
import platform.Foundation.timeIntervalSince1970

actual fun currentHour(): Int =
    NSCalendar.currentCalendar.component(NSCalendarUnitHour, fromDate = NSDate()).toInt()

actual fun currentMonthApi(): String {
    val calendar = NSCalendar.currentCalendar
    val now = NSDate()
    val year = calendar.component(NSCalendarUnitYear, fromDate = now).toInt()
    val month = calendar.component(NSCalendarUnitMonth, fromDate = now).toInt()
    return "$year-${month.toString().padStart(2, '0')}"
}

actual fun currentTimeMillis(): Long =
    (NSDate().timeIntervalSince1970 * 1000).toLong()

actual fun todayIso(): String {
    val calendar = NSCalendar.currentCalendar
    val now = NSDate()
    val y = calendar.component(NSCalendarUnitYear, fromDate = now).toInt()
    val m = calendar.component(NSCalendarUnitMonth, fromDate = now).toInt()
    val d = calendar.component(NSCalendarUnitDay, fromDate = now).toInt()
    return "$y-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
}

actual fun currentTimeLabel(): String {
    val formatter = NSDateFormatter()
    // Not a hardcoded pattern: `setLocalizedDateFormatFromTemplate` resolves
    // the user's 12/24-hour preference instead of forcing one.
    formatter.setLocalizedDateFormatFromTemplate("jmm")
    return formatter.stringFromDate(NSDate())
}

actual fun timeLabelOf(epochMillis: Long): String {
    val formatter = NSDateFormatter()
    formatter.setLocalizedDateFormatFromTemplate("jmm")
    val date = NSDate.dateWithTimeIntervalSince1970(epochMillis / 1000.0 as NSTimeInterval)
    return formatter.stringFromDate(date)
}
