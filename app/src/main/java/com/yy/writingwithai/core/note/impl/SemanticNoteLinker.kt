package com.yy.writingwithai.core.note.impl

import android.content.Context
import com.yy.writingwithai.core.ai.api.AiGateway
import com.yy.writingwithai.core.ai.api.AiStreamEvent
import com.yy.writingwithai.core.ai.api.TokenLimitExceeded
import com.yy.writingwithai.core.ai.api.WritingOp
import com.yy.writingwithai.core.ai.prompt.NoteAssociationPrompt
import com.yy.writingwithai.core.data.db.NoteDao
import com.yy.writingwithai.core.data.db.dao.NoteLinkDao
import com.yy.writingwithai.core.data.db.entity.LinkType
import com.yy.writingwithai.core.data.db.entity.NoteLinkEntity
import com.yy.writingwithai.core.prefs.SecureApiKeyStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Singleton
class LlmNoteLinkExtractor @Inject constructor(
    private val gateway: AiGateway,
    private val noteLinkDao: NoteLinkDao,
    private val noteDao: NoteDao,
    private val apikeyStore: SecureApiKeyStore,
    @ApplicationContext private val context: Context
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    // TODO polish-review-r2 M6:SharedPreferences → DataStore 与项目其他 store 一致;
    // 当前保留 SharedPreferences(binary 兼容老用户,功能 OK)。
    private val ratePrefs = context.getSharedPreferences(PREFS_RATE, Context.MODE_PRIVATE)

    /** @return number of links persisted, or 0 if skipped / no candidates / error. */
    suspend fun extractAndPersist(noteId: String, bypassRateLimit: Boolean = false): Int {
        if (!bypassRateLimit && isRateLimited(noteId)) return 0
        val src = noteDao.getById(noteId) ?: return 0
        if (src.content.isBlank()) return 0

        val providers = apikeyStore.observeConfiguredProviders().first()
        val providerId = providers.firstOrNull() ?: return 0
        val apikey = apikeyStore.get(providerId) ?: return 0
        val model = "default"

        val query = sanitize(src.content).take(50)
            // H2 修:转义 `%` `_` `\` 与 Repository.observeNotesWithTags 行为对齐(DAO 用 `LIKE :q ESCAPE '\'`),
            // 否则 `100\off` 这种含 `\` 的内容会让 SQLite `\o` 当转义,误匹配 `100*off`。
            .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        val q = "%$query%"
        val candidates = noteDao.search(q).first().filter { it.id != noteId }
        if (candidates.isEmpty()) return 0

        val prompt = NoteAssociationPrompt.build(
            sourceTitle = src.title,
            sourceContent = src.content,
            candidates = candidates.take(20).map {
                NoteAssociationPrompt.CandidateLine(it.id, it.title, it.content.take(80))
            }
        )

        val startMs = System.currentTimeMillis()
        val inTokens = estimateTokens(prompt)
        var responseText = ""
        var linkCount = 0
        try {
            gateway.streamWritingOp(
                op = WritingOp.EXPAND,
                sourceText = prompt,
                providerId = providerId,
                apikey = apikey,
                modelName = model
            ).toList().forEach { event ->
                when (event) {
                    is AiStreamEvent.Delta -> {
                        val t = event.text ?: ""
                        // fix-2026-06-24-review-r1-critical:防失控 LLM 输出 OOM / 计费炸
                        if (responseText.length + t.length > MAX_CHARS) {
                            throw TokenLimitExceeded(MAX_CHARS)
                        }
                        responseText += t
                    }
                    is AiStreamEvent.Failed -> {
                        // M7 修:失败也由 gateway.onCompletion 统一 record,extractor 不再独立 record。
                        return 0
                    }
                    else -> {}
                }
            }
            val response = parseResponse(responseText)
            val outTokens = estimateTokens(responseText)
            val now = System.currentTimeMillis()
            val links = response.links.map { l ->
                NoteLinkEntity(
                    srcNoteId = noteId,
                    dstNoteId = l.id,
                    linkType = LinkType.LLM_EXTRACT,
                    weight = l.confidence.coerceIn(0f, 1f),
                    createdAt = now,
                    updatedAt = now,
                    // M5 修:用 @Serializable 替代手动 JSON 拼接,
                    // 避免 reason 含 `\n` / `\` / 控制字符破坏 JSON。
                    evidence = Json.encodeToString(
                        EvidenceDto.serializer(),
                        EvidenceDto(reason = l.reason, lastLlmExtractAt = now)
                    )
                )
            }
            linkCount = links.size
            if (links.isNotEmpty()) noteLinkDao.upsertAll(links)
            ratePrefs.edit().putLong("last_$noteId", now).apply()
            // M7 修:删 recordHistory(...),由 CoreAiGateway.onCompletion 统一 record,避免双重 entry。
        } catch (e: TokenLimitExceeded) {
            // fix r1:超 cap 不 record 计费(throw 在 collect 内,history 路径不到)
            android.util.Log.w(TAG, "LLM output exceeded $MAX_CHARS chars; cap triggered")
            return 0
        } catch (e: Exception) {
            // M7 修:同样由 gateway 统一 record(失败也走 onCompletion 的 lastError 路径)。
            if (e is kotlinx.coroutines.CancellationException) throw e
            return 0
        }
        return linkCount
    }

    fun isRateLimited(noteId: String): Boolean {
        val last = ratePrefs.getLong("last_$noteId", 0L)
        if (last == 0L) return false
        return System.currentTimeMillis() - last < RATE_LIMIT_MS
    }

    // M7 修:删 `private suspend fun recordHistory(...)` 整段 — gateway.onCompletion 统一 record。

    private fun parseResponse(text: String): NoteAssociationPrompt.LlmResponse {
        val clean = text.trim().removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()

        @Serializable data class LDto(val id: String, val confidence: Float, val reason: String)

        @Serializable data class RDto(val links: List<LDto> = emptyList())
        return try {
            val dto = json.decodeFromString<RDto>(clean)
            NoteAssociationPrompt.LlmResponse(
                links = dto.links.map {
                    NoteAssociationPrompt.LlmLinkResult(it.id, it.confidence.coerceIn(0f, 1f), it.reason)
                }
            )
        } catch (_: Exception) {
            // 模型返回非 JSON / 截断,当作空链接处理(返回 0 link,不算错)。
            NoteAssociationPrompt.LlmResponse(links = emptyList())
        }
    }

    private fun sanitize(c: String) = c.replace(Regex("[\"'`*\\[\\]]"), " ")
        .replace(Regex("\\s+"), " ").trim().take(200)

    // fix-2026-06-25-review-r1 H4:英文约 1 tok / 4 char,CJK 约 1.5 tok / 1 char;
    // 之前 (length / 3.5) 把 1000 字中文估成 285,实际 1500,系统低估 5x 影响费用感知。
    private fun estimateTokens(text: String): Int {
        val cjk = text.count { it in '一'..'鿿' }
        val other = text.length - cjk
        return (other / 4) + (cjk * 3 / 2)
    }

    /** M5:LLM 关联抽取 evidence 序列化(替代手动 JSON 拼接)。 */
    @Serializable
    private data class EvidenceDto(val reason: String, val lastLlmExtractAt: Long)

    companion object {
        private const val PREFS_RATE = "note_assoc_llm_rate"
        const val RATE_LIMIT_MS = 24 * 60 * 60 * 1000L

        // fix-2026-06-24-review-r1-critical:LLM 输出字符上限 ≈ 4K tokens
        // fix-2026-06-26-review-r3 LOW:去重到 AiConstants.LLM_MAX_OUTPUT_CHARS
        private const val MAX_CHARS = com.yy.writingwithai.core.ai.api.LLM_MAX_OUTPUT_CHARS
        private const val TAG = "LlmNoteLinkExtractor"
    }
}
