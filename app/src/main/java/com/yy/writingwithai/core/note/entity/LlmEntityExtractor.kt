package com.yy.writingwithai.core.note.entity

import com.yy.writingwithai.core.ai.api.AiGateway
import com.yy.writingwithai.core.ai.api.AiStreamEvent
import com.yy.writingwithai.core.ai.api.TokenLimitExceeded
import com.yy.writingwithai.core.ai.api.WritingOp
import com.yy.writingwithai.core.data.db.NoteDao
import com.yy.writingwithai.core.data.db.dao.entity.NoteEntityDao
import com.yy.writingwithai.core.data.db.entity.entity.NoteEntityRow
import com.yy.writingwithai.core.data.repo.CustomPromptRepository
import com.yy.writingwithai.core.prefs.SecureApiKeyStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * entity-extraction-association · LLM 实体抽取实现(tasks §2.5)。
 *
 * 通过 `AiGateway.streamWritingOp(op=EXPAND, systemPrompt=...)` 取 JSON 数组，
 * 容错解析 + key 规范化 + 写入 `note_entities`。
 *
 * 安全:用户 content 包含 `ignore previous instructions` / `忽略之前指令` 时跳过抽取，
 * 防止 prompt 注入(spec §8.2)。
 *
 * entity-management-and-ai-decompose §2.2:system prompt 改为从 [CustomPromptRepository]
 * 读取(用户自定义 → 默认 → 内置 fallback)。
 */
@Singleton
class LlmEntityExtractor
@Inject
constructor(
    private val noteDao: NoteDao,
    private val entityDao: NoteEntityDao,
    private val aiGateway: AiGateway,
    private val secureApiKeyStore: SecureApiKeyStore,
    private val customPromptRepository: CustomPromptRepository
) : EntityExtractor {

    override suspend fun extractAndPersist(noteId: String, bypassRateLimit: Boolean): Int =
        withContext(Dispatchers.IO) {
            val note = noteDao.getById(noteId) ?: return@withContext 0
            val content = note.title + "\n" + note.content
            if (containsInjection(content)) return@withContext 0

            // remove-debug-fake-fallback §1.1-1.3:无真实 provider 直接返 0;FakeAiProvider 仅 JVM 单测用。
            // debug 包与 release 行为一致,无 apikey 走 ApiKeyMissing 错误对话框(由调用方 QuickNoteDetailViewModel.decompose 检测)。
            val providers = secureApiKeyStore.observeConfiguredProviders().first()
            val providerId = providers.firstOrNull() ?: return@withContext 0
            val apikey = secureApiKeyStore.get(providerId) ?: return@withContext 0

            // entity-management-and-ai-decompose §2.2:用 custom_prompts 表里的提示词
            val systemPrompt = customPromptRepository.getEffectiveContent()

            // fix-2026-06-24-review-r1-critical:fence 用户内容防 prompt 注入
            val fencedContent = com.yy.writingwithai.core.ai.prompt.SafePromptTemplate
                .fenceUserContent(content)
            val prompt = "Title: ${note.title}\nContent: $fencedContent"
            val raw = try {
                aiGateway.streamWritingOp(
                    op = WritingOp.EXPAND,
                    sourceText = prompt,
                    providerId = providerId,
                    apikey = apikey,
                    modelName = null,
                    systemPrompt = systemPrompt
                ).collectText(MAX_CHARS)
            } catch (e: TokenLimitExceeded) {
                android.util.Log.w("LlmEntityExtractor", "LLM output exceeded $MAX_CHARS chars; cap triggered")
                return@withContext 0
            }

            val entities = parseJsonEntities(raw)
            if (entities.isEmpty()) return@withContext 0

            val now = System.currentTimeMillis()
            val rows = entities.mapNotNull { (type, key, surface) ->
                // fix-full-review:indexOf 找不到 surface 时返回 -1，被 coerceAtLeast(0)
                // 变成 spanStart=0，导致所有找不到的实体 span 都从 0 开始(错误高亮)。
                // 改为找不到时跳过该实体(返回 null)，避免无效 span 污染 UI。
                val spanStart = content.indexOf(surface)
                if (spanStart < 0) return@mapNotNull null
                val spanEnd = (spanStart + surface.length).coerceAtMost(content.length)
                NoteEntityRow(
                    noteId = noteId,
                    entityType = type,
                    entityKey = EntityType.normalizeKey(type, key),
                    surfaceForm = surface.take(80),
                    spanStart = spanStart,
                    spanEnd = spanEnd,
                    lastExtractedAt = now,
                    source = "AI_EXTRACTED"
                )
            }
            entityDao.deleteByNoteId(noteId)
            entityDao.upsertAll(rows)
            rows.size
        }

    private fun containsInjection(content: String): Boolean {
        val lower = content.lowercase()
        return lower.contains("ignore previous instructions") ||
            lower.contains("忽略之前指令") ||
            lower.contains("ignore all previous")
    }

    private fun parseJsonEntities(raw: String): List<Triple<EntityType, String, String>> {
        // fix-2026-06-24-review-r1-high H20:严格 JSON.parseToJsonElement 解析(替代 substring `[...]`)
        val element = runCatching { Json.parseToJsonElement(raw) }.getOrNull() ?: run {
            android.util.Log.w(TAG, "LLM raw not valid JSON, prefix=${raw.take(80)}")
            return emptyList()
        }
        val arr = element as? JsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val obj = runCatching { el.jsonObject }.getOrNull() ?: return@mapNotNull null
            val typeStr = obj["type"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val key = obj["key"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val surface = obj["surface"]?.jsonPrimitive?.contentOrNull ?: key
            val type = EntityType.fromTypeName(typeStr) ?: return@mapNotNull null
            // entity-management-and-ai-decompose fix:LLM 经常给 surface 包单/双引号
            // ("先天缺陷 / "先天缺陷" / "content"),sheet 标题/sheet 内容要干净。
            // 解析时剥离两端连续引号。
            Triple(type, key, stripWrappingQuotes(surface))
        }
    }

    /**
     * 剥离两端的连续引号字符。LLM 输出常见形态:
     * - "先天缺陷(单前导) — 剥 1
     * - "先天缺陷"(双前导双尾随) — 剥 2
     * - "content"(标准双引号) — 剥 2
     * 异常:整串都是引号 → 原样返回(交给上层 coerce 兜底)。
     */
    private fun stripWrappingQuotes(s: String): String {
        if (s.isEmpty()) return s
        val quoteChars = setOf('“', '”', '‘', '’', '"', '\'')
        var leading = 0
        while (leading < s.length && s[leading] in quoteChars) leading++
        var trailing = 0
        while (trailing < s.length - leading && s[s.length - 1 - trailing] in quoteChars) trailing++
        if (leading + trailing >= s.length) return s
        return s.substring(leading, s.length - trailing)
    }

    companion object {
        // entity-management-and-ai-decompose §1.4:默认提示词作为内置 fallback
        // 实际生效值由 [CustomPromptRepository.getEffectiveContent] 提供。
        const val ENTITY_EXTRACT_SYSTEM_ZH: String =
            "你是笔记实体抽取助手。从文本中抽取 PERSON / WORK / LOCATION 三类实体。" +
                "每条输出 JSON 对象 {type,key,surface}，仅返回 JSON 数组，key 用小写英文。"

        // fix-2026-06-24-review-r1-critical:LLM 输出字符上限 ≈ 4K tokens
        // fix-2026-06-26-review-r3 LOW:去重到 AiConstants.LLM_MAX_OUTPUT_CHARS
        private const val MAX_CHARS = com.yy.writingwithai.core.ai.api.LLM_MAX_OUTPUT_CHARS
        private const val TAG = "LlmEntityExtractor"
    }
}

private suspend fun kotlinx.coroutines.flow.Flow<com.yy.writingwithai.core.ai.api.AiStreamEvent>.collectText(
    maxChars: Int
): String {
    val sb = StringBuilder()
    collect { ev ->
        if (ev is com.yy.writingwithai.core.ai.api.AiStreamEvent.Delta) {
            val t = ev.text ?: ""
            if (sb.length + t.length > maxChars) {
                throw com.yy.writingwithai.core.ai.api.TokenLimitExceeded(maxChars)
            }
            sb.append(t)
        }
    }
    return sb.toString()
}
