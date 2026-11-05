package com.fixmoney

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import com.fixmoney.shared.ai.AiEndpoint

/**
 * Debug-only switch for testing AI coins against `wrangler dev` on the Mac:
 *
 * ```
 * adb reverse tcp:8787 tcp:8787
 * adb shell am start -n com.vikram641.expensetracker.kmp/com.fixmoney.MainActivity --es worker_url http://127.0.0.1:8787
 * ```
 *
 * The choice is remembered across launches (and by the voice bubble's process
 * start), so the extra only has to be passed once. To go back to the real
 * Worker pass an empty value - quoted *inside* the device shell, or adb drops
 * it and `am` fails with "Argument expected after worker_url":
 * `adb shell "am start -n com.vikram641.expensetracker.kmp/com.fixmoney.MainActivity --es worker_url ''"` **A release build ignores all of this**: the
 * debuggable check below is the only gate, and the cleartext permission it
 * needs exists only in `src/androidDebug`.
 */
object DebugWorker {
    private const val PREFS = "debug_tools"
    private const val KEY = "worker_url"
    const val EXTRA = "worker_url"

    fun apply(context: Context) {
        if (!context.isDebuggable()) return
        AiEndpoint.useDebugWorker(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))
    }

    fun fromIntent(context: Context, intent: Intent?) {
        if (!context.isDebuggable() || intent?.hasExtra(EXTRA) != true) return
        val url = intent.getStringExtra(EXTRA).orEmpty()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, url).apply()
        AiEndpoint.useDebugWorker(url)
        android.util.Log.i("DebugWorker", "AI Worker -> ${AiEndpoint.current}")
    }

    private fun Context.isDebuggable() = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}
