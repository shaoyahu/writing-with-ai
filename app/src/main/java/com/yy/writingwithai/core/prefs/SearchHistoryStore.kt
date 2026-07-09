package com.yy.writingwithai.core.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * search-enhancement · 搜索历史 DataStore(最近 20 条)。
 *
 * fix M14 (full-review):改成 Hilt @Singleton + 注入 @ApplicationContext。
 * 之前是 object + Context 参数逐方法传入,违反 CLAUDE.md "feature 不直接 new 基础设施"
 * 原则,且 Singleton 数据没法在多个 caller 之间共享内存缓存。
 * 保留 [SearchHistoryStoreLegacy] 静态 façade 给仍未迁移的 caller 用(已 deprecate)。
 */
@Singleton
class SearchHistoryStoreImpl
@Inject
constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun getAll(): List<String> {
        val prefs = context.applicationContext.searchHistoryPrefs.data.first()
        return (prefs[KEY_QUERIES] ?: emptySet()).toList()
    }

    suspend fun add(query: String) {
        context.applicationContext.searchHistoryPrefs.edit { prefs ->
            val current = (prefs[KEY_QUERIES] ?: emptySet()).toMutableList()
            current.remove(query) // dedup
            current.add(0, query) // add to front
            if (current.size > 20) current.removeAt(current.lastIndex)
            prefs[KEY_QUERIES] = current.toSet()
        }
    }

    suspend fun remove(query: String) {
        context.applicationContext.searchHistoryPrefs.edit { prefs ->
            val current = (prefs[KEY_QUERIES] ?: emptySet()).toMutableList()
            current.remove(query)
            prefs[KEY_QUERIES] = current.toSet()
        }
    }

    suspend fun clear() {
        context.applicationContext.searchHistoryPrefs.edit { prefs ->
            prefs.remove(KEY_QUERIES)
        }
    }

    companion object {
        private val Context.searchHistoryPrefs: DataStore<Preferences> by preferencesDataStore(
            name = "search_history"
        )
        private val KEY_QUERIES = stringSetPreferencesKey("recent_queries")
    }
}

/**
 * fix M14 (full-review):legacy object facade 仅保留 M14 迁移期间的兼容入口。
 * 新 caller 必须用 [SearchHistoryStoreImpl](Hilt 注入)。
 */
@Deprecated("Use Hilt-injected SearchHistoryStoreImpl instead", ReplaceWith("SearchHistoryStoreImpl"))
object SearchHistoryStoreLegacy {
    suspend fun getAll(context: Context): List<String> = SearchHistoryStoreImpl(context.applicationContext).getAll()

    suspend fun add(context: Context, query: String) = SearchHistoryStoreImpl(context.applicationContext).add(query)

    suspend fun remove(context: Context, query: String) =
        SearchHistoryStoreImpl(context.applicationContext).remove(query)

    suspend fun clear(context: Context) = SearchHistoryStoreImpl(context.applicationContext).clear()
}
