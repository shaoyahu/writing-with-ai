package com.yy.writingwithai.core.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.yy.writingwithai.core.ai.api.WritingOp
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * custom-prompt-template · AI 操作 system prompt 用户模板仓库。
 *
 * spec: openspec/changes/custom-prompt-template/specs/custom-prompt-template/spec.md
 * "PromptTemplateStore persists templates via DataStore"
 *
 * 3 个 key 集合:`prompt_template_expand` / `polish` / `organize`，各 `String?`。
 * 空字符串 / null 视为"恢复默认"(调用方走 `DefaultPrompts.forOp(op)` fallback)。
 */
private val Context.promptTemplateDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "prompt_template_store"
)

interface PromptTemplateStore {
    /** key 缺失 / value null / value 空字符串 → return `null`(调用方 fallback 默认)。 */
    suspend fun getForOp(op: WritingOp): String?

    suspend fun setForOp(op: WritingOp, prompt: String)

    suspend fun resetToDefault(op: WritingOp)

    fun observeAll(): Flow<PromptTemplates>
}

data class PromptTemplate(
    val systemPrompt: String?
)

data class PromptTemplates(
    val expand: PromptTemplate?,
    val polish: PromptTemplate?,
    val organize: PromptTemplate?
) {
    companion object {
        val EMPTY =
            PromptTemplates(
                expand = null,
                polish = null,
                organize = null
            )
    }
}

@Singleton
class PromptTemplateStoreImpl
@Inject
constructor(
    @ApplicationContext private val context: Context
) : PromptTemplateStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val keys = mapOf(
        WritingOp.EXPAND to stringPreferencesKey("prompt_template_expand"),
        WritingOp.POLISH to stringPreferencesKey("prompt_template_polish"),
        WritingOp.ORGANIZE to stringPreferencesKey("prompt_template_organize"),
        WritingOp.SUMMARIZE to stringPreferencesKey("prompt_template_summarize"),
        WritingOp.TRANSLATE to stringPreferencesKey("prompt_template_translate")
    )

    override suspend fun getForOp(op: WritingOp): String? {
        val prefs = context.promptTemplateDataStore.data.first()
        val raw = prefs[keys[op] ?: return null]
        // fallback 规则:空字符串 / null 视为恢复默认
        return raw?.takeIf { it.isNotEmpty() }
    }

    override suspend fun setForOp(op: WritingOp, prompt: String) {
        val key = keys[op] ?: return
        context.promptTemplateDataStore.edit { it[key] = prompt }
    }

    override suspend fun resetToDefault(op: WritingOp) {
        // 写空字符串，触发 fallback
        setForOp(op, "")
    }

    override fun observeAll(): Flow<PromptTemplates> {
        return context.promptTemplateDataStore.data
            .map { prefs ->
                PromptTemplates(
                    expand = prefs[
                        keys.getValue(
                            WritingOp.EXPAND
                        )
                    ]?.takeIf { it.isNotEmpty() }?.let { PromptTemplate(it) },
                    polish = prefs[
                        keys.getValue(
                            WritingOp.POLISH
                        )
                    ]?.takeIf { it.isNotEmpty() }?.let { PromptTemplate(it) },
                    organize = prefs[
                        keys.getValue(
                            WritingOp.ORGANIZE
                        )
                    ]?.takeIf { it.isNotEmpty() }?.let { PromptTemplate(it) }
                )
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = PromptTemplates.EMPTY
            )
    }

    companion object {
        internal const val PROMPT_TEMPLATE_STORE = "prompt_template_store"
    }
}
