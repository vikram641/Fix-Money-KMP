package com.fixmoney

import androidx.compose.ui.window.ComposeUIViewController
import com.fixmoney.ui.App

/**
 * iOS entry point. iosApp's SwiftUI shell calls this to get a UIViewController
 * hosting the same shared [App] composable Android runs.
 *
 * Koin starts in [ensureKoinStarted], which is guarded - this function can be
 * called more than once per process (e.g. SwiftUI recreating the representable),
 * and a Siri intent can beat it to the first call entirely.
 */
fun MainViewController() = ComposeUIViewController {
    ensureKoinStarted()
    App()
}
