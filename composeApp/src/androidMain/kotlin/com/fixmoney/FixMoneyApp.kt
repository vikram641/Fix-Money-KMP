package com.fixmoney

import android.app.Application
import com.fixmoney.shared.di.initKoin
import org.koin.android.ext.koin.androidContext

/** Koin's start point on Android - the old app did this with Hilt's @HiltAndroidApp. */
class FixMoneyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin { androidContext(this@FixMoneyApp) }
    }
}
