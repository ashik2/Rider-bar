package com.example.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [AppShortcut::class, AppSetting::class], version = 1, exportSchema = false)
abstract class RiderBarDatabase : RoomDatabase() {
    abstract fun shortcutDao(): ShortcutDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile
        private var INSTANCE: RiderBarDatabase? = null

        fun getDatabase(context: Context): RiderBarDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RiderBarDatabase::class.java,
                    "riderbar_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
