package com.kimaita.monies.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kimaita.monies.data.database.models.Setting

@Dao
interface SettingsDao {
    @Upsert
    suspend fun saveSetting(setting: Setting)

    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun getSetting(key: String): String?
}