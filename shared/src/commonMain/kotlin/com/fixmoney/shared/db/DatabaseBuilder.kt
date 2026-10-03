package com.fixmoney.shared.db

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

/**
 * The database file lives in a platform-specific place (app database dir on
 * Android, Documents on iOS), so only the builder is `expect`/`actual`; the
 * driver and dispatcher config below is shared.
 */
expect class DatabaseBuilderFactory {
    fun create(): RoomDatabase.Builder<FixMoneyDatabase>
}

fun buildDatabase(factory: DatabaseBuilderFactory): FixMoneyDatabase =
    factory.create()
        // Real migrations, not a destructive fallback. This app keeps the only
        // copy of a user's expenses - there is no backend to restore from - so
        // a missing migration must fail loudly in development rather than drop
        // the table in someone's hands. See Migrations.kt.
        .addMigrations(*ALL_MIGRATIONS)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Default)
        .build()
