package com.yy.writingwithai.core.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.yy.writingwithai.core.ai.prompt.DefaultPrompts
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * entity-management-and-ai-decompose §1+§2+§5 · 实体抽取提示词仓库。
 *
 * 复用 DataStore Preferences(沿用项目现有 prefs 模式,避免 Room schema migration 摩擦):
 * - key `entity_extract_prompt_custom`:用户自定义提示词(非空即生效)
 * - 空 / 缺失 → fallback 到 `DefaultPrompts.ENTITY_EXTRACT_SYSTEM`(内置默认)
 *
 * 没有"seedIfMissing"步骤:DataStore key 缺失即代表使用默认,无需提前写。
 */
private val Context.entityExtractPromptDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "entity_extract_prompt_store"
)

@Singleton
class CustomPromptRepository
@Inject
constructor(
    @ApplicationContext private val context: Context
) {

    private val customKey = stringPreferencesKey("entity_extract_prompt_custom")

    /** 返回用户自定义 → 默认 fallback。 */
    suspend fun getEffectiveContent(): String = withContext(Dispatchers.IO) {
        val custom = context.entityExtractPromptDataStore.data.first()[customKey]
        custom?.takeIf { it.isNotBlank() } ?: DefaultPrompts.ENTITY_EXTRACT_SYSTEM
    }

    suspend fun getDefaultContent(): String = DefaultPrompts.ENTITY_EXTRACT_SYSTEM

    suspend fun getCustomContent(): String? = withContext(Dispatchers.IO) {
        context.entityExtractPromptDataStore.data.first()[customKey]?.takeIf { it.isNotBlank() }
    }

    suspend fun saveCustom(content: String) = withContext(Dispatchers.IO) {
        require(content.isNotBlank()) { "prompt content must not be blank" }
        context.entityExtractPromptDataStore.edit { it[customKey] = content }
    }

    suspend fun resetToDefault() = withContext(Dispatchers.IO) {
        context.entityExtractPromptDataStore.edit { it.remove(customKey) }
    }
}
