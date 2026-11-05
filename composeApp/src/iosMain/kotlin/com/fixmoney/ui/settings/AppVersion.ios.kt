package com.fixmoney.ui.settings

import platform.Foundation.NSBundle

actual fun appVersionName(): String =
    (NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString") as? String).orEmpty()
