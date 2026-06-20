package com.yy.writingwithai.core.prefs

import com.yy.writingwithai.core.ai.api.WritingOp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * custom-prompt-template · PromptTemplateStore Fake(测试用,in-memory)。
 *
 * spec: openspec/changes/custom-prompt-template/specs/custom-prompt-template/spec.md
 * "PromptTemplateStore persists templates via DataStore" Scenario "observeAll 实时反映变更"
 */
class FakePromptTemplateStore : PromptTemplateStore {
    private val backing =
        MutableStateFlow(
            mapOf(
                WritingOp.EXPAND to null as String?,
                WritingOp.POLISH to null as String?,
                WritingOp.ORGANIZE to null as String?
            )
        )

    /** Test hook:直注入状态(避免在测试中走 suspend)。 */
    fun seed(op: WritingOp, prompt: String?) {
        backing.update { it.toMutableMap().apply { put(op, prompt) } }
    }

    override suspend fun getForOp(op: WritingOp): String? = backing.value[op]?.takeIf { it.isNotEmpty() }

    override suspend fun setForOp(op: WritingOp, prompt: String) {
        backing.update { it.toMutableMap().apply { put(op, prompt) } }
    }

    override suspend fun resetToDefault(op: WritingOp) {
        setForOp(op, "")
    }

    override fun observeAll(): Flow<PromptTemplates> = backing.map { current ->
        PromptTemplates(
            expand = current[WritingOp.EXPAND]?.takeIf { it.isNotEmpty() }?.let { PromptTemplate(it) },
            polish = current[WritingOp.POLISH]?.takeIf { it.isNotEmpty() }?.let { PromptTemplate(it) },
            organize = current[WritingOp.ORGANIZE]?.takeIf { it.isNotEmpty() }?.let { PromptTemplate(it) }
        )
    }
}
