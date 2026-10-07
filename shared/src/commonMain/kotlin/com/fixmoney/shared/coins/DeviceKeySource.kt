package com.fixmoney.shared.coins

/**
 * A stable identifier for this phone that survives uninstalling the app.
 *
 * That survival is the whole point: the Worker finds a wallet - and, crucially,
 * its trial - by this key when the app has no token, so a reinstall lands on
 * the same coins and the same trial instead of a fresh 5 free days every time.
 *
 * - Android: SHA-256 of `ANDROID_ID`, which since Android 8 is fixed per signing
 *   key + user + device and does not change on reinstall (only on factory reset).
 *   Hashed before it leaves the device; the Worker salts it again. Play policy
 *   allows a device id for fraud prevention, which is this use - it must never
 *   be passed to AdMob or analytics.
 * - iOS: a random UUID kept in the Keychain, which outlives app deletion.
 *
 * Provided by `platformModule`, because the Android half needs a Context.
 */
fun interface DeviceKeySource {
    fun deviceKey(): String
}

/** "android" / "ios" - sent on registration so the Worker can tell the two apart. */
expect val platformName: String
