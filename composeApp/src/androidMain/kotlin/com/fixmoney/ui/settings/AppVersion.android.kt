package com.fixmoney.ui.settings

import android.content.Context
import org.koin.mp.KoinPlatform

actual fun appVersionName(): String = runCatching {
    val context: Context = KoinPlatform.getKoin().get()
    context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
}.getOrDefault("")
