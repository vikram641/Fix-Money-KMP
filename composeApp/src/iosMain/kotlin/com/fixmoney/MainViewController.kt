package com.fixmoney

import androidx.compose.ui.window.ComposeUIViewController
import com.fixmoney.shared.di.initKoin
import com.fixmoney.ui.App

/**
 * iOS entry point. iosApp's SwiftUI shell calls this to get a UIViewController
 * hosting the same shared [App] composable Android runs.
 *
 * iOS has no Application class, so Koin starts here instead - guarded, because
 * this function can be called more than once per process (e.g. SwiftUI
 * recreating the representable).
 */
private var koinStarted = false

fun MainViewController() = ComposeUIViewController {
    if (!koinStarted) {
        initKoin()
        koinStarted = true
    }
    App()
}
