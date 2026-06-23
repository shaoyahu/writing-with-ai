package com.yy.writingwithai.core.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/**
 * search-enhancement · 搜索历史 DataStore(最近 20 条)。
 */
object SearchHistoryStore {
    private val Context.searchHistoryPrefs: DataStore<Preferences> by preferencesDataStore(
        name = "search_history"
    )

    private val KEY_QUERIES = stringSetPreferencesKey("recent_queries")

    suspend fun getAll(context: Context): List<String> {
        val prefs = context.applicationContext.searchHistoryPrefs.data.first()
        return (prefs[KEY_QUERIES] ?: emptySet()).toList()
    }

    suspend fun add(context: Context, query: String) {
        context.applicationContext.searchHistoryPrefs.edit { prefs ->
            val current = (prefs[KEY_QUERIES] ?: emptySet()).toMutableList()
            current.remove(query) // dedup
            current.add(0, query) // add to front
            if (current.size > 20) current.removeAt(current.lastIndex)
            prefs[KEY_QUERIES] = current.toSet()
        }
    }

    suspend fun remove(context: Context, query: String) {
        context.applicationContext.searchHistoryPrefs.edit { prefs ->
            val current = (prefs[KEY_QUERIES] ?: emptySet()).toMutableList()
            current.remove(query)
            prefs[KEY_QUERIES] = current.toSet()
        }
    }

    suspend fun clear(context: Context) {
        context.applicationContext.searchHistoryPrefs.edit { prefs ->
            prefs.remove(KEY_QUERIES)
        }
    }
}
