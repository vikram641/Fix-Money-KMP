package com.fixmoney.shared.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

actual class DatabaseBuilderFactory(private val context: Context) {
    actual fun create(): RoomDatabase.Builder<FixMoneyDatabase> {
        // Must run before Room opens anything: once Room creates an empty
        // fixmoney.db there is nothing left to adopt, and the old app's history
        // is stranded on disk forever. See LegacyImport.android.kt.
        adoptLegacyDatabaseIfPresent(context)

        val dbFile = context.getDatabasePath(DB_FILE_NAME)
        return Room.databaseBuilder<FixMoneyDatabase>(
            context = context.applicationContext,
            name = dbFile.absolutePath
        )
    }
}
