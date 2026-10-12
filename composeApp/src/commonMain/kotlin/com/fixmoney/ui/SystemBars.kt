package com.fixmoney.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Makes the platform's own status / navigation bars follow the app's theme.
 *
 * This has to be driven by [darkTheme] - the in-app Settings switch - and not by
 * the system's night mode, because the two disagree: the switch can put the app
 * in dark mode while the phone itself is light. When that happened the status
 * bar drew dark icons over the dark navy background (the clock was invisible)
 * and the navigation bar got a white scrim under the dark bottom nav.
 *
 * [navigationBarColor] is the app's own bottom bar colour ([AppColors.bottomBar]).
 * Android paints the system navigation bar with it so the two do not meet in a
 * seam; iOS ignores it.
 */
@Composable
expect fun SystemBarsAppearance(darkTheme: Boolean, navigationBarColor: Color)
