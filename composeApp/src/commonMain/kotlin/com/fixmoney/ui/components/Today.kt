package com.fixmoney.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.fixmoney.shared.platform.currentMonthApi
import com.fixmoney.shared.platform.todayIso

/**
 * Today's date, re-read whenever the screen resumes.
 *
 * Screens used to capture the date once. An app left open from Oct 31 to Nov 1
 * kept showing October as "Month closed", a new Nov 1 expense never appeared on
 * Home, and week/days-left/pace stayed frozen until the database happened to
 * change. Key a flow on this to rebuild it when the day moves on.
 */
@Composable
fun rememberToday(): String {
    var today by remember { mutableStateOf(todayIso()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { today = todayIso() }
    return today
}

/**
 * The screen's selected month, which follows the calendar while the user is on
 * the current month: if it was showing the month in progress when the day rolled
 * over into a new one, it moves forward too. A month the user picked is left alone.
 */
@Composable
fun rememberFollowingMonth(today: String): MutableState<String> {
    val month = remember { mutableStateOf(currentMonthApi()) }
    var lastCurrent by remember { mutableStateOf(month.value) }
    val nowMonth = today.substringBeforeLast("-")
    if (nowMonth != lastCurrent) {
        if (month.value == lastCurrent) month.value = nowMonth
        lastCurrent = nowMonth
    }
    return month
}
