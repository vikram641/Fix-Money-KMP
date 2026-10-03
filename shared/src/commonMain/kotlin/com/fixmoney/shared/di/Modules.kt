package com.fixmoney.shared.di

import com.fixmoney.shared.ai.ChatEngine
import com.fixmoney.shared.config.FeatureFlagRepository
import com.fixmoney.shared.analytics.AnalyticsStateFactory
import com.fixmoney.shared.data.CategoryRepository
import com.fixmoney.shared.db.DatabaseBuilderFactory
import com.fixmoney.shared.db.FixMoneyDatabase
import com.fixmoney.shared.db.buildDatabase
import com.fixmoney.shared.expense.BudgetRepository
import com.fixmoney.shared.expense.BudgetStateFactory
import com.fixmoney.shared.expense.ExpenseRepository
import com.fixmoney.shared.export.ExpenseExporter
import com.fixmoney.shared.home.HomeStateFactory
import com.fixmoney.shared.onboarding.AppPrefs
import com.fixmoney.shared.onboarding.OnboardingPrefs
import com.fixmoney.shared.platform.httpClient
import com.fixmoney.shared.receipt.ReceiptScanEngine
import com.fixmoney.shared.voice.VoiceQuickAddEngine
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import com.fixmoney.shared.premium.TrialState
import com.russhwolf.settings.Settings
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin replaces Hilt here - Hilt is Android-only, so it can't live in shared
 * code (migration plan section 5).
 */
val sharedModule = module {
    single<HttpClient> {
        httpClient().config {
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 15_000
            }
        }
    }
    single { OnboardingPrefs(get()) }
    single { AppPrefs(get()) }
    single { TrialState(get()) }
    single<FixMoneyDatabase> { buildDatabase(get()) }
    single { get<FixMoneyDatabase>().expenseDao() }
    single { get<FixMoneyDatabase>().budgetDao() }
    single { CategoryRepository(get(), get()) }
    single { FeatureFlagRepository(get(), get()) }
    single { ExpenseRepository(get()) }
    single { BudgetRepository(get(), get()) }
    single { BudgetStateFactory(get(), get(), get()) }
    single { ChatEngine(get(), get()) }
    single { HomeStateFactory(get(), get(), get()) }
    single { AnalyticsStateFactory(get(), get(), get()) }
    single { VoiceQuickAddEngine(get(), get(), get()) }
    single { ReceiptScanEngine(get()) }
    single { ExpenseExporter(get(), get()) }
}

/** Provides the platform's Settings implementation (SharedPreferences / NSUserDefaults). */
expect val platformModule: Module

/**
 * Called once per platform at startup: from `ExpenseApp.onCreate()` on Android,
 * and from `MainViewController()` on iOS (iOS has no Application class).
 */
fun initKoin(appDeclaration: KoinApplication.() -> Unit = {}) = startKoin {
    appDeclaration()
    modules(sharedModule, platformModule)
}
