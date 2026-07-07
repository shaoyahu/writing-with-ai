package com.yy.writingwithai.core.data.db.dao.entity

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yy.writingwithai.core.data.db.entity.entity.NoteEntityRow
import com.yy.writingwithai.core.note.entity.EntityType

/**
 * entity-extraction-association · note_entities DAO。
 *
 * entity-management-and-ai-decompose 扩展:
 * - `queryDistinctSurfaces`:给 matcher 用,返回所有 surfaceForm 去重集
 * - `queryEntityList`:按类型过滤 + 关键字搜索 + 排序的列表
 * - `queryNotesByEntity`:详情页 → 关联笔记列表
 * - `deleteByEntityKey`:按 entityKey 删除(实体管理详情页删除)
 * - `deleteByEntityKeys`:批量删除
 */
@Dao
interface NoteEntityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<NoteEntityRow>)

    @Query("SELECT * FROM note_entities WHERE noteId = :noteId")
    suspend fun getByNoteId(noteId: String): List<NoteEntityRow>

    @Query("DELETE FROM note_entities WHERE noteId = :noteId")
    suspend fun deleteByNoteId(noteId: String)

    @Query(
        "SELECT other.* FROM note_entities self " +
            "JOIN note_entities other ON self.entityKey = other.entityKey " +
            "WHERE self.noteId = :srcNoteId AND other.noteId != :srcNoteId " +
            "LIMIT :limit"
    )
    suspend fun querySharedEntityHits(srcNoteId: String, limit: Int): List<NoteEntityRow>

    @Query("SELECT DISTINCT entityKey FROM note_entities ORDER BY entityKey LIMIT :limit OFFSET :offset")
    suspend fun queryAllEntityKeys(limit: Int, offset: Int): List<String>

    /** matcher 用:返回所有已知 surfaceForm + entityKey + entityType 去重集。 */
    @Query(
        "SELECT entityKey, entityType, MAX(surfaceForm) AS surfaceForm " +
            "FROM note_entities " +
            "GROUP BY entityKey ORDER BY MAX(lastExtractedAt) DESC"
    )
    suspend fun queryDistinctSurfaces(): List<DistinctSurfaceRow>

    /**
     * 实体管理列表聚合。
     * - [search] = surfaceForm LIKE %x%(可空)
     * - [typeFilter] = entityType = ?(可空)
     * - [sort] = name / noteCount / lastExtracted
     */
    @Query(
        "SELECT entityKey, entityType, MAX(surfaceForm) AS surfaceForm, " +
            "COUNT(DISTINCT noteId) AS noteCount, MAX(lastExtractedAt) AS lastExtractedAt " +
            "FROM note_entities " +
            "WHERE (:search IS NULL OR surfaceForm LIKE '%' || :search || '%') " +
            "AND (:typeFilter IS NULL OR entityType = :typeFilter) " +
            "GROUP BY entityKey " +
            "ORDER BY " +
            "  CASE WHEN :sort = 'name' THEN MAX(surfaceForm) END ASC, " +
            "  CASE WHEN :sort = 'noteCount' THEN COUNT(DISTINCT noteId) END DESC, " +
            "  CASE WHEN :sort = 'lastExtracted' THEN MAX(lastExtractedAt) END DESC"
    )
    suspend fun queryEntityList(search: String?, typeFilter: EntityType?, sort: String): List<EntityListRow>

    @Query("SELECT COUNT(DISTINCT entityKey) FROM note_entities")
    suspend fun countDistinctEntityKeys(): Int

    /** 实体详情:返回该 entityKey 的全部 noteId + 命中信息,按 noteId 去重。 */
    @Query(
        "SELECT noteId, MIN(spanStart) AS spanStart, MAX(spanEnd) AS spanEnd, " +
            "MAX(lastExtractedAt) AS lastExtractedAt, MAX(source) AS source " +
            "FROM note_entities WHERE entityKey = :entityKey GROUP BY noteId " +
            "ORDER BY lastExtractedAt DESC LIMIT :limit"
    )
    suspend fun queryNotesByEntity(entityKey: String, limit: Int): List<EntityNoteHitRow>

    /** 按 entityKey 删除(详情页单实体删除)。 */
    @Query("DELETE FROM note_entities WHERE entityKey = :entityKey")
    suspend fun deleteByEntityKey(entityKey: String)

    /** 批量删除(实体管理多选删除)。 */
    @Query("DELETE FROM note_entities WHERE entityKey IN (:entityKeys)")
    suspend fun deleteByEntityKeys(entityKeys: List<String>)
}

data class DistinctSurfaceRow(
    val entityKey: String,
    val entityType: EntityType,
    val surfaceForm: String
)

data class EntityListRow(
    val entityKey: String,
    val entityType: EntityType,
    val surfaceForm: String,
    val noteCount: Int,
    val lastExtractedAt: Long
)

data class EntityNoteHitRow(
    val noteId: String,
    val spanStart: Int,
    val spanEnd: Int,
    val lastExtractedAt: Long,
    val source: String
)
