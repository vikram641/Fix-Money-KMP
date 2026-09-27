package com.fixmoney.shared.di

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import com.fixmoney.shared.db.DatabaseBuilderFactory
import org.koin.dsl.module
import platform.Foundation.NSUserDefaults

actual val platformModule = module {
    single { DatabaseBuilderFactory() }
    single<Settings> {
        NSUserDefaultsSettings(NSUserDefaults(suiteName = "prefs_onboarding"))
    }
}
