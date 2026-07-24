package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shortcuts")
data class AppShortcut(
    @PrimaryKey val slotId: Int, // 1 = Left 1, 2 = Left 2, 3 = Right 1, 4 = Right 2
    val packageName: String?,
    val appName: String?
)

@Entity(tableName = "settings")
data class AppSetting(
    @PrimaryKey val key: String,
    val value: String
)
