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
        // No released version has shipped yet, so there is nothing to migrate
        // from; revisit before the first Play Store upload.
        .fallbackToDestructiveMigration(dropAllTables = true)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Default)
        .build()
