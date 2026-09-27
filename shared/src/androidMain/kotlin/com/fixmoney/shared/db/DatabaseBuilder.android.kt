package com.fixmoney.shared.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

actual class DatabaseBuilderFactory(private val context: Context) {
    actual fun create(): RoomDatabase.Builder<FixMoneyDatabase> {
        val dbFile = context.getDatabasePath(DB_FILE_NAME)
        return Room.databaseBuilder<FixMoneyDatabase>(
            context = context.applicationContext,
            name = dbFile.absolutePath
        )
    }
}
