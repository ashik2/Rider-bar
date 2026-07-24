package com.example.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ShortcutDao {
    @Query("SELECT * FROM shortcuts ORDER BY slotId ASC")
    fun getAllShortcuts(): Flow<List<AppShortcut>>

    @Query("SELECT * FROM shortcuts WHERE slotId = :slotId")
    fun getShortcutFlow(slotId: Int): Flow<AppShortcut?>

    @Query("SELECT * FROM shortcuts WHERE slotId = :slotId")
    suspend fun getShortcutDirect(slotId: Int): AppShortcut?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShortcut(shortcut: AppShortcut)
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE `key` = :key")
    fun getSettingFlow(key: String): Flow<AppSetting?>

    @Query("SELECT * FROM settings WHERE `key` = :key")
    suspend fun getSettingDirect(key: String): AppSetting?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetting(setting: AppSetting)
}
