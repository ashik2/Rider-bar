package com.example.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RiderBarRepository(private val database: RiderBarDatabase) {
    private val shortcutDao = database.shortcutDao()
    private val settingsDao = database.settingsDao()

    val allShortcuts: Flow<List<AppShortcut>> = shortcutDao.getAllShortcuts()

    fun getShortcutFlow(slotId: Int): Flow<AppShortcut?> = shortcutDao.getShortcutFlow(slotId)

    suspend fun getShortcutDirect(slotId: Int): AppShortcut? = shortcutDao.getShortcutDirect(slotId)

    suspend fun saveShortcut(slotId: Int, packageName: String?, appName: String?) {
        shortcutDao.insertShortcut(AppShortcut(slotId, packageName, appName))
    }

    fun getSettingFlow(key: String, defaultValue: String): Flow<String> {
        return settingsDao.getSettingFlow(key).map { it?.value ?: defaultValue }
    }

    suspend fun getSettingDirect(key: String, defaultValue: String): String {
        return settingsDao.getSettingDirect(key)?.value ?: defaultValue
    }

    suspend fun saveSetting(key: String, value: String) {
        settingsDao.insertSetting(AppSetting(key, value))
    }
}
