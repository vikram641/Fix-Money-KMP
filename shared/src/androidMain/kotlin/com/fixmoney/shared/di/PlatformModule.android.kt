package com.fixmoney.shared.di

import android.content.Context
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import com.fixmoney.shared.db.DatabaseBuilderFactory
import com.fixmoney.shared.export.ExportFileWriter
import org.koin.dsl.module

actual val platformModule = module {
    single { DatabaseBuilderFactory(get()) }
    single { ExportFileWriter(get()) }
    single<Settings> {
        val context: Context = get()
        // Same SharedPreferences file name the old app used, so an existing
        // install's onboarding profile is picked up rather than reset.
        SharedPreferencesSettings(
            context.getSharedPreferences("prefs_onboarding", Context.MODE_PRIVATE)
        )
    }
}
