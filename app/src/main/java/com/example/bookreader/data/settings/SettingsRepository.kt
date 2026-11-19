package com.example.bookreader.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "settings")

/** Глобальные настройки чтения (по умолчанию для всех книг). */
class SettingsRepository(private val ctx: Context) {
    private val key = stringPreferencesKey("reader_settings")

    val settings: Flow<ReaderSettings> = ctx.settingsStore.data.map { p ->
        p[key]?.let { ReaderSettings.fromJson(it) } ?: ReaderSettings()
    }

    suspend fun save(s: ReaderSettings) {
        ctx.settingsStore.edit { it[key] = s.toJson() }
    }
}
