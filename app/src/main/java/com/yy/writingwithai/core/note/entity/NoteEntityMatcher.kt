package com.yy.writingwithai.core.note.entity

import androidx.room.withTransaction
import com.yy.writingwithai.core.data.db.AppDatabase
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
    private val entityDao: NoteEntityDao,
    private val db: AppDatabase
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
        // fix M23 (full-review):把"content 中只匹配首次出现"改为"匹配所有非重叠出现"。
        // 之前 `content.indexOf(surface)` 只返回第一个,后续出现全部漏命中。
        // 改成 while-loop 从 start 之后继续 indexOf,产出多个 span。
        for ((entityKey, entityType, surface) in known) {
            if (surface.isBlank()) continue
            var cursor = 0
            while (true) {
                val start = content.indexOf(surface, startIndex = cursor)
                if (start < 0) break
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
                cursor = end
                if (cursor >= content.length) break
            }
        }
        if (newRows.isEmpty()) return 0
        // fix H11:upsert 前先 deleteByNoteId 删旧行，避免编辑后笔记保留旧 entity spans。
        // 包裹在事务中保证原子性。
        db.withTransaction {
            entityDao.deleteByNoteId(noteId)
            entityDao.upsertAll(newRows)
        }
        return newRows.size
    }
}
