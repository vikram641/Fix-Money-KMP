package com.fixmoney

import androidx.compose.ui.window.ComposeUIViewController
import com.fixmoney.ui.App

/**
 * iOS entry point. iosApp's SwiftUI shell calls this to get a UIViewController
 * hosting the same shared [App] composable Android runs.
 */
fun MainViewController() = ComposeUIViewController { App() }
