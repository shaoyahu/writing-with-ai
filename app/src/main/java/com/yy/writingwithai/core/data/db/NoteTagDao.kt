package com.yy.writingwithai.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yy.writingwithai.core.data.db.entity.NoteTagCrossRef
import kotlinx.coroutines.flow.Flow

/**
 * 笔记 ↔ 标签 DAO。
 *
 * 见 [openspec.changes.quick-note-feature.specs.quick-note.spec] §"Tag many-to-many"。
 */
@Dao
interface NoteTagDao {
    /**
     * 加 tag。`OnConflictStrategy.IGNORE` 让"挂同一个 tag 第二次"成为 no-op,
     * Repository 不用先查再插。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(crossRef: NoteTagCrossRef)

    @Query("DELETE FROM note_tags WHERE noteId = :noteId AND tag = :tag")
    suspend fun remove(noteId: String, tag: String)

    @Query("DELETE FROM note_tags WHERE noteId = :noteId")
    suspend fun removeAllForNote(noteId: String)

    @Query("SELECT tag FROM note_tags WHERE noteId = :noteId ORDER BY tag")
    fun observeTagsFor(noteId: String): Flow<List<String>>

    @Query("SELECT DISTINCT tag FROM note_tags ORDER BY tag")
    fun observeAllTags(): Flow<List<String>>

    /**
     * 全表交叉引用(笔记量 < 1k，直接 SELECT * 没问题)。
     * Repository 在内存里 groupBy(noteId) → Map<noteId, List<tag>>,
     * 给列表屏批量渲染 tag chip 用。
     */
    @Query("SELECT * FROM note_tags")
    fun observeAllCrossRefs(): Flow<List<NoteTagCrossRef>>
}
