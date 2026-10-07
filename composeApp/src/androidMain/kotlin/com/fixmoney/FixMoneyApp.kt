package com.fixmoney

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.provider.Settings
import com.fixmoney.shared.di.initKoin
import com.fixmoney.shared.onboarding.AppPrefs
import com.fixmoney.voice.FloatingBubbleService
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext

/** Koin's start point on Android - the old app did this with Hilt's @HiltAndroidApp. */
class FixMoneyApp : Application() {

    private val appPrefs: AppPrefs by inject()

    override fun onCreate() {
        super.onCreate()
        DebugWorker.apply(this) // no-op outside debug builds
        initKoin { androidContext(this@FixMoneyApp) }
        restartVoiceBubbleIfEnabled()
    }

    /**
     * The bubble only ever starts from the Settings toggle switching ON, and a
     * plain relaunch (after a force-stop, a crash, or the OS killing the service)
     * doesn't rerun that. Without this the preference would still say enabled
     * while no bubble exists anywhere - the old app hit exactly this and fixed it
     * the same way. Safe to call when it's already running: starting an already
     * started service is a no-op restart.
     */
    private fun restartVoiceBubbleIfEnabled() {
        if (!appPrefs.voiceBubble()) return

        val hasOverlay = Settings.canDrawOverlays(this)
        val hasMic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (hasOverlay && hasMic) FloatingBubbleService.start(this)
    }
}
