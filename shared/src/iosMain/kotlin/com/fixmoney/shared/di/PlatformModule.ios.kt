package com.fixmoney.shared.di

import com.fixmoney.shared.coins.DeviceKeySource
import com.fixmoney.shared.coins.IosDeviceKeySource
import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import com.fixmoney.shared.db.DatabaseBuilderFactory
import com.fixmoney.shared.export.ExportFileWriter
import org.koin.dsl.module
import platform.Foundation.NSUserDefaults

actual val platformModule = module {
    single { DatabaseBuilderFactory() }
    single { ExportFileWriter() }
    single<DeviceKeySource> { IosDeviceKeySource() }
    single<Settings> {
        NSUserDefaultsSettings(NSUserDefaults(suiteName = "prefs_onboarding"))
    }
}
