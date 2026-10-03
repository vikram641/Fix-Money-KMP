package com.fixmoney.shared.di

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import com.fixmoney.shared.db.DatabaseBuilderFactory
import com.fixmoney.shared.export.ExportFileWriter
import org.koin.dsl.module
import platform.Foundation.NSUserDefaults

actual val platformModule = module {
    single { DatabaseBuilderFactory() }
    single { ExportFileWriter() }
    single<Settings> {
        NSUserDefaultsSettings(NSUserDefaults(suiteName = "prefs_onboarding"))
    }
}
