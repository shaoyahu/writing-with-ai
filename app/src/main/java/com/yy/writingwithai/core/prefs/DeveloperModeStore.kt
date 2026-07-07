package com.yy.writingwithai.core.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * entity-management-and-ai-decompose §5.1 · 开发者模式开关(DataStore Preferences)。
 *
 * 状态:`isDeveloperModeEnabled` boolean,默认 false。
 * 应用卸载即重置(符合预期,DataStore 在 app data 目录)。
 *
 * 不需要加密:非敏感信息,只需 persistent state。
 */
@Singleton
class DeveloperModeStore
@Inject
constructor(
    @ApplicationContext private val context: Context
) {
    val isEnabled: Flow<Boolean> = context.devModeDataStore.data.map { prefs ->
        prefs[KEY_ENABLED] ?: false
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.devModeDataStore.edit { it[KEY_ENABLED] = enabled }
    }

    companion object {
        private val KEY_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("dev_mode_enabled")
    }
}

private val Context.devModeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "developer_mode_store"
)
