package com.fixmoney

import com.fixmoney.shared.di.initKoin

private var koinStarted = false

/**
 * iOS has no Application class, so Koin is started on first use instead.
 *
 * Two callers, and the second is the reason this isn't just inlined into
 * [MainViewController]: a Siri App Intent runs in the app's process **without any
 * UI**, so it can be the first thing that ever touches Koin in that launch.
 *
 * Not synchronized - both callers run on the main queue (the composable on the UI
 * thread, the intent bridge because it dispatches to `Dispatchers.Main` before
 * calling this), so there is nothing to race with.
 */
fun ensureKoinStarted() {
    if (koinStarted) return
    initKoin()
    koinStarted = true
}
