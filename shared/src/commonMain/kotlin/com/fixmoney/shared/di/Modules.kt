package com.fixmoney.shared.di

import com.fixmoney.shared.data.CategoryRepository
import com.fixmoney.shared.home.HomeStateFactory
import com.fixmoney.shared.onboarding.OnboardingPrefs
import com.fixmoney.shared.platform.httpClient
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
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
    single { CategoryRepository(get()) }
    single { HomeStateFactory(get()) }
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
