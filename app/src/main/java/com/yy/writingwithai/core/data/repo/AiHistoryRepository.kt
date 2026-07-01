package com.yy.writingwithai.core.data.repo

import com.yy.writingwithai.core.data.db.AiHistoryDao
import com.yy.writingwithai.core.data.db.entity.AiHistoryEntity
import com.yy.writingwithai.core.data.mapper.toModel
import com.yy.writingwithai.core.data.model.AiHistory
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class AiHistoryRepository
@Inject
constructor(
    private val dao: AiHistoryDao
) {
    private companion object {
        const val MAX_SNAPSHOT_LEN = 10_000
        const val MAX_ERROR_LEN = 1_000
        const val PRUNE_DAYS = 90L

        // H3 修:apikey / Bearer / x-api-key 等敏感 pattern 集中脱敏，
        // 避免 gateway / extractor 各自实现漂移。
        val APIKEY_PATTERNS = listOf(
            Regex("""sk-[A-Za-z0-9_\-]{16,}"""),
            Regex("""(?i)Bearer\s+[A-Za-z0-9_\-\.=]{16,}"""),
            Regex("""(?i)x-api-key[:\s]+[A-Za-z0-9_\-\.=]{16,}""")
        )
    }

    private fun redact(s: String): String = APIKEY_PATTERNS.fold(s) { acc, p -> acc.replace(p, "***REDACTED***") }

    /** 记录一次 AI 调用(由 CoreAiGateway 在 Done / Failed 时调)。 */
    suspend fun record(
        noteId: String?,
        providerId: String,
        model: String,
        op: String,
        inputTokens: Int,
        outputTokens: Int,
        totalTokens: Int,
        durationMs: Long,
        createdAt: Long,
        inputSnapshot: String,
        outputSnapshot: String,
        error: String?
    ) {
        val redactedInput = redact(inputSnapshot).take(MAX_SNAPSHOT_LEN)
        val redactedOutput = redact(outputSnapshot).take(MAX_SNAPSHOT_LEN)
        val redactedError = error?.let { redact(it).take(MAX_ERROR_LEN) }
        dao.insert(
            AiHistoryEntity(
                id = UUID.randomUUID().toString(),
                noteId = noteId,
                providerId = providerId,
                model = model,
                op = op,
                inputTokens = inputTokens,
                outputTokens = outputTokens,
                totalTokens = totalTokens,
                durationMs = durationMs,
                createdAt = createdAt,
                inputSnapshot = redactedInput,
                outputSnapshot = redactedOutput,
                truncated =
                redactedInput.length < inputSnapshot.length ||
                    redactedOutput.length < outputSnapshot.length,
                error = redactedError
            )
        )
    }

    fun observeByNoteId(noteId: String): Flow<List<AiHistory>> = dao.observeByNoteId(
        noteId
    ).map { list ->
        list.map {
            it.toModel()
        }
    }

    fun observeAll(limit: Int = 100): Flow<List<AiHistory>> = dao.observeAll(limit).map { list ->
        list.map {
            it.toModel()
        }
    }

    suspend fun prune() {
        val cutoff = System.currentTimeMillis() - PRUNE_DAYS * 24 * 60 * 60 * 1000L
        dao.deleteOlderThan(cutoff)
    }
}
