package com.fixmoney.shared.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.fixmoney.shared.platform.logWarn

private const val TAG = "LegacyImport"

/** What the pre-KMP app called its database. */
private const val LEGACY_DB_FILE = "expense.db"

/**
 * Adopts the pre-KMP app's database on first launch after the rewrite ships.
 *
 * **Without this, every existing user silently loses their entire history.** The
 * old app stored expenses in `expense.db`; this one reads `fixmoney.db`. An
 * update installs over the old app, so the old file is still sitting there -
 * untouched, unread - while Room creates a fresh empty database beside it. And
 * because the SharedPreferences file name is shared, onboarding is already
 * marked complete, so the user is not even sent through setup: they open the
 * app, land on Home, and every expense they ever entered is gone with no
 * explanation. There is no backend copy to restore from.
 *
 * **A plain file rename would crash instead.** Both schemas are at
 * `user_version = 3`, so Room would run no migration and then fail validation,
 * because the two `budgets` tables disagree: the old one is keyed on
 * `(categoryId, month)`, this one on `id`. The `expenses` tables are identical,
 * column for column.
 *
 * So the copy rebuilds `budgets` into the new shape on the way through. The old
 * table already carries an `id` column, so no value has to be invented.
 *
 * Runs once: it does nothing if the new file already exists, and the legacy file
 * is left on disk rather than deleted, so a failure here is recoverable and a
 * downgrade still finds its data.
 */
internal fun adoptLegacyDatabaseIfPresent(context: Context) {
    val target = context.getDatabasePath(DB_FILE_NAME)
    if (target.exists()) {
        repairLegacyIdentity(target.absolutePath)
        return
    }

    val legacy = context.getDatabasePath(LEGACY_DB_FILE)
    if (!legacy.exists()) return

    try {
        target.parentFile?.mkdirs()

        // Copy first and convert the copy. Converting in place would leave the
        // old app's data mangled if this failed halfway, and that file is the
        // only copy there is.
        legacy.copyTo(target, overwrite = true)
        // WAL/SHM belong to the legacy file's journal, not this one; carrying
        // them over would point at the wrong database.
        listOf("$LEGACY_DB_FILE-wal", "$LEGACY_DB_FILE-shm").forEach {
            context.getDatabasePath(it).takeIf { f -> f.exists() }?.delete()
        }

        SQLiteDatabase.openDatabase(
            target.absolutePath, null, SQLiteDatabase.OPEN_READWRITE
        ).use { db ->
            db.beginTransaction()
            try {
                rebuildBudgets(db)
                importPendingExpenses(db)
                // The copy still carries the old app's identity hash. Room checks
                // it on first open and throws "cannot verify the data integrity"
                // - every launch, since fixmoney.db now exists and this never
                // runs again. With the table gone Room validates the schema
                // instead and writes its own hash.
                db.execSQL("DROP TABLE IF EXISTS room_master_table")
                // Indices this schema expects; Room validates their presence.
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_date` ON `expenses` (`date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_updatedAt` ON `expenses` (`updatedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_categoryId` ON `expenses` (`categoryId`)")
                db.version = 3
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
        logWarn(TAG, "adopted $LEGACY_DB_FILE into $DB_FILE_NAME")
    } catch (e: Exception) {
        // Start clean rather than hand Room a half-converted file: an empty app
        // is recoverable, a crash loop on launch is not. The legacy file is
        // still there, so nothing is destroyed by giving up here.
        logWarn(TAG, "legacy import failed, starting fresh: ${e.message}")
        runCatching { target.delete() }
    }
}

/** The old app's Room identity hash (`ExpenseDatabase_Impl`, schema v3). */
private const val LEGACY_IDENTITY_HASH = "ac07dbd5ae44997d2da265c79bdc6482"

/**
 * Rescues installs that 2.1 already imported. 2.1 left the legacy identity hash
 * in `room_master_table`, so Room crashes on every launch. Dropping the table
 * lets Room re-validate the (already converted) schema and stamp its own hash.
 * Only ever touches a file that still carries the legacy hash.
 */
private fun repairLegacyIdentity(path: String) {
    try {
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            val hasLegacyHash = runCatching {
                db.rawQuery("SELECT identity_hash FROM room_master_table WHERE id = 42", null).use { c ->
                    c.moveToFirst() && c.getString(0) == LEGACY_IDENTITY_HASH
                }
            }.getOrDefault(false)
            if (hasLegacyHash) {
                db.execSQL("DROP TABLE IF EXISTS room_master_table")
                logWarn(TAG, "repaired legacy identity hash in $DB_FILE_NAME")
            }
        }
    } catch (e: Exception) {
        logWarn(TAG, "identity repair skipped: ${e.message}")
    }
}

/**
 * The old app queued expenses made offline in `pending_expenses` until they
 * synced. Anything still queued at update time exists nowhere else, so it is
 * carried into `expenses` rather than left behind.
 */
private fun importPendingExpenses(db: SQLiteDatabase) {
    val hasTable = db.rawQuery(
        "SELECT 1 FROM sqlite_master WHERE type='table' AND name='pending_expenses'", null
    ).use { it.moveToFirst() }
    if (!hasTable) return
    db.execSQL(
        """
        INSERT OR IGNORE INTO `expenses`
            (id, amount, currency, date, note, receiptUrl, createdAt, updatedAt,
             isSynced, localCreatedAt, userId, categoryId, categoryName,
             categoryColor, categoryIcon)
        SELECT 'pending-' || localId, amount, currency, date, note, receiptUrl,
               date, date, 0, createdAtLocal, '', categoryId, categoryName,
               categoryColor, categoryIcon
        FROM `pending_expenses`
        """.trimIndent()
    )
}

/**
 * Recreates `budgets` with `id` as the primary key.
 *
 * SQLite cannot alter a primary key, so the table is rebuilt and copied. Rows
 * that collide on `id` are dropped by `INSERT OR IGNORE` - the old composite key
 * allowed what the new one does not.
 */
private fun rebuildBudgets(db: SQLiteDatabase) {
    db.execSQL("ALTER TABLE `budgets` RENAME TO `budgets_legacy`")
    db.execSQL(
        """
        CREATE TABLE `budgets` (
            `id` TEXT NOT NULL,
            `categoryId` TEXT NOT NULL,
            `categoryName` TEXT NOT NULL,
            `categoryColor` TEXT NOT NULL,
            `categoryIcon` TEXT NOT NULL,
            `currency` TEXT NOT NULL,
            `limitAmount` INTEGER NOT NULL,
            `month` TEXT NOT NULL,
            `spentAmount` INTEGER NOT NULL,
            PRIMARY KEY(`id`)
        )
        """.trimIndent()
    )
    db.execSQL(
        """
        INSERT OR IGNORE INTO `budgets`
            (id, categoryId, categoryName, categoryColor, categoryIcon,
             currency, limitAmount, month, spentAmount)
        SELECT id, categoryId, categoryName, categoryColor, categoryIcon,
               currency, limitAmount, month, spentAmount
        FROM `budgets_legacy`
        """.trimIndent()
    )
    db.execSQL("DROP TABLE `budgets_legacy`")
}
