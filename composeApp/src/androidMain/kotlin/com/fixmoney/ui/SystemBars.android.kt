package com.fixmoney.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext

/**
 * Re-runs `enableEdgeToEdge` whenever the theme flips, then paints the system
 * navigation bar the same colour as the app's own bottom bar.
 *
 * `SystemBarStyle.auto`'s third argument is the dark-mode *test*; the default
 * reads the system's `uiMode`, which is exactly what was wrong - it is the
 * app's own switch that decides here.
 *
 * **The two lines after `enableEdgeToEdge` are the fix for a seam that was
 * visible on the device in dark mode**: the bottom bar read `#0B1A2B` while the
 * strip below it read `#07101A`. That is `#0B1A2B` under Android's 40%-black
 * navigation-bar contrast scrim - `enableEdgeToEdge` turns
 * `isNavigationBarContrastEnforced` **on** when the style's night mode is dark,
 * which is precisely the case the app asks for. Clearing it and setting the
 * colour explicitly removes the scrim and guarantees the match.
 *
 * Order matters: `enableEdgeToEdge` sets both of these itself, so they have to
 * be overridden *after* it, never before. `window.navigationBarColor` is
 * ignored from API 35 on, where the bar is always transparent and the app's own
 * background - which already extends under it - shows through instead.
 */
@Composable
actual fun SystemBarsAppearance(darkTheme: Boolean, navigationBarColor: Color) {
    val context = LocalContext.current
    LaunchedEffect(darkTheme, navigationBarColor, context) {
        val activity = context.findComponentActivity() ?: return@LaunchedEffect
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ) { darkTheme },
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ) { darkTheme }
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activity.window.isNavigationBarContrastEnforced = false
        }
        @Suppress("DEPRECATION")
        activity.window.navigationBarColor = navigationBarColor.toArgb()
    }
}

private tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is Activity -> null
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}
