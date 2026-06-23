package com.yy.writingwithai.core.note.entity

import com.yy.writingwithai.core.ai.api.AiGateway
import com.yy.writingwithai.core.ai.api.WritingOp
import com.yy.writingwithai.core.data.db.NoteDao
import com.yy.writingwithai.core.data.db.dao.entity.NoteEntityDao
import com.yy.writingwithai.core.data.db.entity.entity.NoteEntityRow
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * entity-extraction-association · LLM 实体抽取实现(tasks §2.5)。
 *
 * 通过 `AiGateway.streamWritingOp(op=EXPAND, systemPrompt=...)` 取 JSON 数组,
 * 容错解析 + key 规范化 + 写入 `note_entities`。
 *
 * 安全:用户 content 包含 `ignore previous instructions` / `忽略之前指令` 时跳过抽取,
 * 防止 prompt 注入(spec §8.2)。
 */
@Singleton
class LlmEntityExtractor
@Inject
constructor(
    private val noteDao: NoteDao,
    private val entityDao: NoteEntityDao,
    private val aiGateway: AiGateway
) : EntityExtractor {

    override suspend fun extractAndPersist(noteId: String, bypassRateLimit: Boolean): Int =
        withContext(Dispatchers.IO) {
            val note = noteDao.getById(noteId) ?: return@withContext 0
            val content = note.title + "\n" + note.content
            if (containsInjection(content)) return@withContext 0

            val prompt = "Title: ${note.title}\nContent: $content"
            val raw = aiGateway.streamWritingOp(
                op = WritingOp.EXPAND,
                sourceText = prompt,
                providerId = "fake",
                apikey = "",
                modelName = null,
                systemPrompt = ENTITY_EXTRACT_SYSTEM_ZH
            ).collectText()

            val entities = parseJsonEntities(raw)
            if (entities.isEmpty()) return@withContext 0

            val now = System.currentTimeMillis()
            val rows = entities.map { (type, key, surface) ->
                val spanStart = content.indexOf(surface).coerceAtLeast(0)
                val spanEnd = (spanStart + surface.length).coerceAtMost(content.length)
                NoteEntityRow(
                    noteId = noteId,
                    entityType = type,
                    entityKey = EntityType.normalizeKey(type, key),
                    surfaceForm = surface.take(80),
                    spanStart = spanStart,
                    spanEnd = spanEnd,
                    lastExtractedAt = now
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
        val bracketStart = raw.indexOf('[')
        val bracketEnd = raw.lastIndexOf(']')
        if (bracketStart < 0 || bracketEnd <= bracketStart) return emptyList()
        val cleaned = raw.substring(bracketStart, bracketEnd + 1)
        val arr = runCatching { Json.parseToJsonElement(cleaned) as JsonArray }.getOrNull() ?: return emptyList()
        return arr.mapNotNull { el ->
            val obj = runCatching { el.jsonObject }.getOrNull() ?: return@mapNotNull null
            val typeStr = obj["type"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val key = obj["key"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val surface = obj["surface"]?.jsonPrimitive?.contentOrNull ?: key
            val type = runCatching { EntityType.valueOf(typeStr.uppercase()) }.getOrNull() ?: return@mapNotNull null
            Triple(type, key, surface)
        }
    }

    companion object {
        const val ENTITY_EXTRACT_SYSTEM_ZH: String =
            "你是笔记实体抽取助手。从文本中抽取 PERSON / WORK / LOCATION 三类实体。" +
                "每条输出 JSON 对象 {type,key,surface},仅返回 JSON 数组,key 用小写英文。"
    }
}

private suspend fun kotlinx.coroutines.flow.Flow<com.yy.writingwithai.core.ai.api.AiStreamEvent>.collectText(): String {
    val sb = StringBuilder()
    collect { ev ->
        if (ev is com.yy.writingwithai.core.ai.api.AiStreamEvent.Delta) sb.append(ev.text)
    }
    return sb.toString()
}
