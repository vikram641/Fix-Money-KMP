package com.fixmoney.shared.coins

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.security.MessageDigest
import java.util.UUID

actual val platformName: String = "android"

/**
 * SHA-256 of `ANDROID_ID`. Stable across uninstall/reinstall for the same
 * signing key (Android 8+), so a reinstall finds the same wallet and trial.
 *
 * A handful of old/broken builds report null or the famous emulator constant
 * `9774d56d682e549c`; those fall back to a random id kept in [fallbackPrefs],
 * which survives only as long as app data does - acceptable for a rare case.
 */
class AndroidDeviceKeySource(private val context: Context) : DeviceKeySource {

    @SuppressLint("HardwareIds") // fraud prevention (trial reuse) is the allowed use; never sent to ads
    override fun deviceKey(): String {
        val raw = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() && it != "9774d56d682e549c" }
            ?: fallbackId()
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("fixmoney:${context.packageName}:$raw".toByteArray())
        return "a1-" + digest.joinToString("") { "%02x".format(it) }
    }

    private fun fallbackId(): String {
        val prefs = context.getSharedPreferences("prefs_onboarding", Context.MODE_PRIVATE)
        prefs.getString(KEY, null)?.let { return it }
        return UUID.randomUUID().toString().also { prefs.edit().putString(KEY, it).apply() }
    }

    private companion object {
        const val KEY = "coin_device_fallback"
    }
}
