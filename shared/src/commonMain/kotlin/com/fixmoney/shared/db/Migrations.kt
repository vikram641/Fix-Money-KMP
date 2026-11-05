package com.fixmoney.shared.db

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Schema migrations.
 *
 * **Every schema change from here on needs one of these.** The builder used to
 * fall back to `fallbackToDestructiveMigration(dropAllTables = true)`, which was
 * correct while nothing had shipped - and would silently delete every expense a
 * user had ever entered the first time the schema moved after launch. The
 * comment on it said "revisit before the first Play Store upload"; this is that
 * revisit.
 *
 * Without a matching migration Room now throws instead. That is the behaviour
 * worth having: a crash in development is a bug report, a wiped ledger is a
 * one-star review and nothing to restore from - this app has no backend copy.
 */

/**
 * v2 -> v3: indices on `expenses`.
 *
 * Index names must match what Room generates from `@Index` (`index_<table>_<col>`)
 * or validation fails on open with a schema mismatch.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_date` ON `expenses` (`date`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_updatedAt` ON `expenses` (`updatedAt`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_categoryId` ON `expenses` (`categoryId`)")
    }
}

/** Passed to the builder in order; add each new one here. */
val ALL_MIGRATIONS = arrayOf(MIGRATION_2_3)
