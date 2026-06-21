package com.yy.writingwithai.core.note.impl

import android.content.Context
import com.yy.writingwithai.core.ai.api.AiGateway
import com.yy.writingwithai.core.ai.api.AiStreamEvent
import com.yy.writingwithai.core.ai.api.WritingOp
import com.yy.writingwithai.core.ai.prompt.NoteAssociationPrompt
import com.yy.writingwithai.core.data.db.AiHistoryDao
import com.yy.writingwithai.core.data.db.NoteDao
import com.yy.writingwithai.core.data.db.dao.NoteLinkDao
import com.yy.writingwithai.core.data.db.entity.AiHistoryEntity
import com.yy.writingwithai.core.data.db.entity.LinkType
import com.yy.writingwithai.core.data.db.entity.NoteLinkEntity
import com.yy.writingwithai.core.prefs.SecureApiKeyStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
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
    private val aiHistoryDao: AiHistoryDao,
    @ApplicationContext private val context: Context
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    private val ratePrefs = context.getSharedPreferences(PREFS_RATE, Context.MODE_PRIVATE)

    suspend fun extractAndPersist(noteId: String, bypassRateLimit: Boolean = false) {
        if (!bypassRateLimit && isRateLimited(noteId)) return
        val src = noteDao.getById(noteId) ?: return
        if (src.content.isBlank()) return

        val providers = apikeyStore.observeConfiguredProviders().first()
        val providerId = providers.firstOrNull() ?: return
        val apikey = apikeyStore.get(providerId) ?: return
        val model = "default"

        val query = sanitize(src.content).take(50)
        val q = "%$query%"
        val candidates = noteDao.search(q).first().filter { it.id != noteId }
        if (candidates.isEmpty()) return

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
        try {
            gateway.streamWritingOp(
                op = WritingOp.EXPAND,
                sourceText = prompt,
                providerId = providerId,
                apikey = apikey,
                modelName = model
            ).toList().forEach { event ->
                when (event) {
                    is AiStreamEvent.Delta -> responseText += event.text
                    is AiStreamEvent.Failed -> {
                        recordHistory(
                            noteId, providerId, model, inTokens, 0, startMs,
                            prompt.take(500), "", error = event.error.summary()
                        )
                        return
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
                    evidence = "{\"reason\":\"${l.reason.replace("\"", "\\\"")}\",\"lastLlmExtractAt\":$now}"
                )
            }
            if (links.isNotEmpty()) noteLinkDao.upsertAll(links)
            ratePrefs.edit().putLong("last_$noteId", now).apply()
            recordHistory(
                noteId, providerId, model, inTokens, outTokens, startMs,
                prompt.take(500), responseText.take(500), error = null
            )
        } catch (e: Exception) {
            recordHistory(
                noteId, providerId, model, inTokens, 0, startMs,
                prompt.take(500), "", error = e.message
            )
        }
    }

    fun isRateLimited(noteId: String): Boolean {
        val last = ratePrefs.getLong("last_$noteId", 0L)
        if (last == 0L) return false
        return System.currentTimeMillis() - last < RATE_LIMIT_MS
    }

    private suspend fun recordHistory(
        noteId: String,
        providerId: String,
        model: String,
        inTokens: Int,
        outTokens: Int,
        startMs: Long,
        inputSnapshot: String,
        outputSnapshot: String,
        error: String?
    ) {
        val total = inTokens + outTokens
        val duration = System.currentTimeMillis() - startMs
        aiHistoryDao.insert(
            AiHistoryEntity(
                id = UUID.randomUUID().toString(),
                noteId = noteId, providerId = providerId, model = model,
                op = "note-association-extract",
                inputTokens = inTokens, outputTokens = outTokens,
                totalTokens = total, durationMs = duration,
                createdAt = System.currentTimeMillis(),
                inputSnapshot = inputSnapshot, outputSnapshot = outputSnapshot,
                truncated = inputSnapshot.length < inTokens * 3,
                error = error
            )
        )
    }

    private fun parseResponse(text: String): NoteAssociationPrompt.LlmResponse {
        val clean = text.trim().removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()

        @Serializable data class LDto(val id: String, val confidence: Float, val reason: String)

        @Serializable data class RDto(val links: List<LDto> = emptyList())
        val dto = json.decodeFromString<RDto>(clean)
        return NoteAssociationPrompt.LlmResponse(
            links = dto.links.map {
                NoteAssociationPrompt.LlmLinkResult(it.id, it.confidence.coerceIn(0f, 1f), it.reason)
            }
        )
    }

    private fun sanitize(c: String) = c.replace(Regex("[\"'`*\\[\\]]"), " ")
        .replace(Regex("\\s+"), " ").trim().take(200)

    private fun estimateTokens(text: String): Int = (text.length / 3.5).toInt().coerceAtLeast(1)

    companion object {
        private const val PREFS_RATE = "note_assoc_llm_rate"
        const val RATE_LIMIT_MS = 24 * 60 * 60 * 1000L
    }
}
