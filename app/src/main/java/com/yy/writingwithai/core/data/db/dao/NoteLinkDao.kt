package com.yy.writingwithai.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yy.writingwithai.core.data.db.entity.NoteLinkEntity

/**
 * 笔记链接边表 DAO。
 *
 * - CRUD:upsert / deleteBySrc / deletePair(单边)
 * - 读:`getRelated` / `getBacklinks` 在 SQL 层完成多信号聚合 + 排序 + 阈值过滤
 *
 * 见 [openspec.changes.note-association.specs.note-association.spec] §"Read query aggregation"。
 */
@Dao
interface NoteLinkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(link: NoteLinkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(links: List<NoteLinkEntity>)

    @Query("DELETE FROM note_links WHERE srcNoteId = :noteId")
    suspend fun deleteBySrc(noteId: String)

    @Query("DELETE FROM note_links WHERE srcNoteId = :src AND dstNoteId = :dst")
    suspend fun deletePair(src: String, dst: String)

    @Query("SELECT COUNT(*) FROM note_links WHERE srcNoteId = :noteId")
    suspend fun countForNote(noteId: String): Int

    @Query("SELECT COUNT(*) FROM note_links")
    suspend fun countAll(): Int

    @Query(
        """
        SELECT
            nl.dstNoteId AS noteId,
            n.title AS title,
            SUBSTR(n.content, 1, 80) AS preview,
            (CASE WHEN MAX(CASE WHEN nl.linkType = 'WIKILINK' THEN 1.0 ELSE 0 END) > 0
                  THEN 1.00 ELSE 0 END) +
            (CASE WHEN MAX(CASE WHEN nl.linkType = 'TAG_OVERLAP' THEN nl.weight ELSE 0 END) > 0
                  THEN 1.50 * MAX(CASE WHEN nl.linkType = 'TAG_OVERLAP' THEN nl.weight ELSE 0 END)
                  ELSE 0 END) +
            (1.00 * COALESCE(MAX(CASE WHEN nl.linkType = 'CONTENT_SIM' THEN nl.weight ELSE 0 END), 0)) +
            (0.80 * COALESCE(MAX(CASE WHEN nl.linkType = 'LLM_EXTRACT' THEN nl.weight ELSE 0 END), 0)) +
            (1.50 * COALESCE(MAX(CASE WHEN nl.linkType = 'ENTITY_HIT' THEN nl.weight ELSE 0 END), 0))
                AS score,
            GROUP_CONCAT(DISTINCT nl.linkType) AS signals,
            GROUP_CONCAT(nl.evidence) AS evidence
        FROM note_links nl
        INNER JOIN notes n ON n.id = nl.dstNoteId
        WHERE nl.srcNoteId = :noteId
        -- R3 fix M3:GROUP BY 只需聚合键(JOIN 唯一键 n.id = nl.dstNoteId)。
        -- 之前 GROUP BY nl.dstNoteId, n.title, n.content 把可能很长的 content 也塞进 GROUP BY,
        -- SQLite 临时 B-tree 体积暴涨，大笔记库性能塌方。noteId 唯一 → 行天然唯一，无需附加列。
        GROUP BY nl.dstNoteId
        -- entity-extraction-polish §2.1:阈值由 caller 从 NoteAssociationSettingsStore 传入，SQL 不再硬编码。
        HAVING score > :threshold
        ORDER BY score DESC
        LIMIT :limit
        """
    )
    suspend fun getRelated(noteId: String, limit: Int, threshold: Double): List<RelatedRow>

    @Query(
        """
        SELECT
            nl.srcNoteId AS noteId,
            n.title AS title,
            SUBSTR(n.content, 1, 80) AS preview,
            (CASE WHEN MAX(CASE WHEN nl.linkType = 'WIKILINK' THEN 1.0 ELSE 0 END) > 0
                  THEN 1.00 ELSE 0 END) +
            (CASE WHEN MAX(CASE WHEN nl.linkType = 'TAG_OVERLAP' THEN nl.weight ELSE 0 END) > 0
                  THEN 1.50 * MAX(CASE WHEN nl.linkType = 'TAG_OVERLAP' THEN nl.weight ELSE 0 END)
                  ELSE 0 END) +
            (1.00 * COALESCE(MAX(CASE WHEN nl.linkType = 'CONTENT_SIM' THEN nl.weight ELSE 0 END), 0)) +
            (0.80 * COALESCE(MAX(CASE WHEN nl.linkType = 'LLM_EXTRACT' THEN nl.weight ELSE 0 END), 0)) +
            (1.50 * COALESCE(MAX(CASE WHEN nl.linkType = 'ENTITY_HIT' THEN nl.weight ELSE 0 END), 0))
                AS score,
            GROUP_CONCAT(DISTINCT nl.linkType) AS signals,
            GROUP_CONCAT(nl.evidence) AS evidence
        FROM note_links nl
        INNER JOIN notes n ON n.id = nl.srcNoteId
        WHERE nl.dstNoteId = :noteId
        GROUP BY nl.srcNoteId
        -- entity-extraction-polish §2.1:阈值由 caller 传入。
        HAVING score > :threshold
        ORDER BY score DESC
        LIMIT :limit
        """
    )
    suspend fun getBacklinks(noteId: String, limit: Int, threshold: Double): List<RelatedRow>
}

/**
 * `getRelated` / `getBacklinks` 的行结果。
 *
 * - `signals` 是逗号分隔的 LinkType 名称(例:`WIKILINK,TAG_OVERLAP`)，由调用方解析为 Set<LinkType>
 *   (避免 Room enum 类型转换的 TypeConverter 麻烦)
 */
data class RelatedRow(
    val noteId: String,
    val title: String,
    val preview: String,
    val score: Float,
    val signals: String,
    val evidence: String?
)
