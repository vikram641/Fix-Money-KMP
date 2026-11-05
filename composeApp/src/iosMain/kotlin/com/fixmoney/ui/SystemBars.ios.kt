package com.fixmoney.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * No-op on iOS. There is no equivalent mismatch to fix: Compose Multiplatform
 * drives the status bar style from the composition's own colours, and iOS has
 * no navigation bar to tint. Kept as an `actual` so the common call site stays
 * platform-free.
 */
@Composable
actual fun SystemBarsAppearance(darkTheme: Boolean, navigationBarColor: Color) {
}
