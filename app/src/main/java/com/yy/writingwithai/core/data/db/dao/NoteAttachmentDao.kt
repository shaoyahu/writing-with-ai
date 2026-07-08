package com.yy.writingwithai.core.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yy.writingwithai.core.data.db.entity.NoteAttachmentEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * note-list-thumbnail · 列表缩略图查询投影(只取首图需要的 noteId + localPath)。
 *
 * Room 不支持直接用 `GROUP BY` 拿「每个 noteId 的 createdAt 最小行」还兼容 Flow,
 * 用 `LIMIT 1` 子查询 + IN 列表是更直接的写法(IN 子句 Room 可编译为 prepared stmt,
 * 一次性走索引,O(n) 而不是 O(n²) 嵌套查询)。
 */
data class FirstImageRow(
    val noteId: String,
    val localPath: String
)

@Dao
interface NoteAttachmentDao {
    @Query("SELECT * FROM note_attachments WHERE noteId = :noteId ORDER BY createdAt")
    fun observeForNote(noteId: String): Flow<List<NoteAttachmentEntity>>

    @Query("SELECT * FROM note_attachments WHERE noteId = :noteId")
    suspend fun getForNote(noteId: String): List<NoteAttachmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: NoteAttachmentEntity)

    @Delete
    suspend fun delete(entity: NoteAttachmentEntity)

    @Query("DELETE FROM note_attachments WHERE noteId = :noteId")
    suspend fun deleteForNote(noteId: String)

    /**
     * note-list-thumbnail · 批量拿一组笔记的「最早一张图片」localPath。
     *
     * 实现思路:子查询 `WHERE noteId IN (:noteIds) AND mimeType LIKE 'image/%'
     * GROUP BY noteId` 拿每个 noteId 的 `MIN(createdAt)` 时间戳,
     * 再回表 JOIN 拿 `localPath`。一次查询覆盖 N 条笔记,避免 N 次单 note 查询
     * 引发 O(n) 次磁盘读 + Compose 重组(列表屏一次渲染 N 条卡片)。
     *
     * 性能:noteId 已有 `Index("noteId")`(见 NoteAttachmentEntity 注释),走索引。
     * returned rows 数量 ≤ :noteIds.size(无图笔记不返回行)。
     *
     * **注意**:[noteIds] 不能为空列表(Room 会生成无效 SQL `IN ()`);
     * 大列表需由调用方 cap(Repository 层已做 `take(500)`)。
     */
    @Query(
        """
        SELECT t1.noteId AS noteId, t1.localPath AS localPath
        FROM note_attachments t1
        INNER JOIN (
            SELECT noteId, MIN(createdAt) AS firstAt
            FROM note_attachments
            WHERE noteId IN (:noteIds) AND mimeType LIKE 'image/%'
            GROUP BY noteId
        ) t2 ON t1.noteId = t2.noteId AND t1.createdAt = t2.firstAt
        WHERE t1.mimeType LIKE 'image/%'
        """
    )
    fun observeFirstImageForNotes(noteIds: List<String>): Flow<List<FirstImageRow>>

    // fix-full-review:空列表会生成无效 SQL `IN ()`，Room 运行时直接崩溃。
    // 提供安全包装：空输入直接返回空 Flow，避免走到 Room 生成的 SQL。
    fun observeFirstImageForNotesSafe(noteIds: List<String>): Flow<List<FirstImageRow>> {
        if (noteIds.isEmpty()) return flow { emit(emptyList()) }
        return observeFirstImageForNotes(noteIds)
    }
}
