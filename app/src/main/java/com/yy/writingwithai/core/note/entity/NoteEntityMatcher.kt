package com.yy.writingwithai.core.note.entity

import com.yy.writingwithai.core.data.db.dao.entity.NoteEntityDao
import com.yy.writingwithai.core.data.db.entity.entity.NoteEntityRow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * entity-management-and-ai-decompose §2.3:把笔记内容中已存在的实体(surfaceForm)找出来,
 * 自动创建 note_entities 关联(不调 AI)。
 *
 * 匹配规则:
 * - 完全相等(Chinese surface 比 English surface 不同,不互相匹配)
 * - 大小写敏感(spec §Scenario "Case-insensitive matching" 明确:Chinese vs English 不匹配)
 *   实现上直接 case-sensitive compare,避免 locale 折叠带来的边界。
 *
 * 行为:
 * - 输入笔记 ID + 笔记内容(已过滤 title)
 * - 列出所有已有实体的 (entityKey, entityType, surfaceForm) 去重集
 * - 对每个 surface 在 content 中查找位置,创建 NoteEntityRow(source=AI_EXTRACTED 默认;
 *   自动匹配视为 AI 已索引,沿用 source 默认)
 * - 已存在的 (noteId, entityKey) 不会重复(主键冲突由 upsert REPLACE 覆盖)
 */
@Singleton
class NoteEntityMatcher
@Inject
constructor(
    private val entityDao: NoteEntityDao
) {

    /**
     * 在 [content] 中匹配所有已知实体的 surfaceForm,创建 note_entities 关联。
     * @return 新增/覆盖的 NoteEntityRow 数
     */
    suspend fun matchAndPersist(noteId: String, content: String): Int {
        if (content.isBlank()) return 0
        val known = entityDao.queryDistinctSurfaces()
        if (known.isEmpty()) return 0
        val now = System.currentTimeMillis()
        val newRows = mutableListOf<NoteEntityRow>()
        for ((entityKey, entityType, surface) in known) {
            if (surface.isBlank()) continue
            val start = content.indexOf(surface)
            if (start < 0) continue
            val end = (start + surface.length).coerceAtMost(content.length)
            newRows += NoteEntityRow(
                noteId = noteId,
                entityType = entityType,
                entityKey = entityKey,
                surfaceForm = surface,
                spanStart = start,
                spanEnd = end,
                lastExtractedAt = now,
                source = "AI_EXTRACTED"
            )
        }
        if (newRows.isEmpty()) return 0
        entityDao.upsertAll(newRows)
        return newRows.size
    }
}
