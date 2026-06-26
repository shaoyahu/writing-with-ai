package com.yy.writingwithai.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.yy.writingwithai.core.data.db.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

/**
 * 随手记主表 DAO。
 *
 * - `observe*` 返回 `Flow`,Room 在底层表变更时自动推送新结果
 * - `search` 走 `title LIKE :q OR content LIKE :q`(roadmap §5.2 拍板,不引入 FTS)
 * - 所有写操作 `suspend`,由 Repository 在 IO 调度器上调用
 * - 排序:固定优先 + 更新时间倒序(spec §"List ordering")
 *
 * 见 [openspec.changes.quick-note-feature.specs.quick-note.spec] §"Note CRUD via Repository"。
 */
@Dao
interface NoteDao {
    @Upsert
    suspend fun upsert(note: NoteEntity)

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeById(id: String): Flow<NoteEntity?>

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query(
        "SELECT * FROM notes ORDER BY isPinned DESC, updatedAt DESC"
    )
    fun observeAll(): Flow<List<NoteEntity>>

    /**
     * 按 tag 筛选:JOIN `note_tags`,保持固定优先 + 时间倒序。
     */
    @Query(
        """
        SELECT n.* FROM notes n
        INNER JOIN note_tags t ON n.id = t.noteId
        WHERE t.tag = :tag
        ORDER BY n.isPinned DESC, n.updatedAt DESC
        """
    )
    fun observeByTag(tag: String): Flow<List<NoteEntity>>

    /**
     * 搜索:title 或 content 含子串。空字符串由 Repository 拦截,不走 DAO。
     *
     * H4 修:`LIKE :q ESCAPE '\'` 让 Repository 在传入 `q` 前对 `%` / `_` / `\` 转义,
     * 避免用户输入 `100%` 时被当通配符。
     */
    @Query(
        """
        SELECT * FROM notes
        WHERE title LIKE :q ESCAPE '\' OR content LIKE :q ESCAPE '\'
        ORDER BY isPinned DESC, updatedAt DESC
        """
    )
    fun search(q: String): Flow<List<NoteEntity>>

    /** search-enhancement · FTS4 全文搜索,返回匹配的 note id 列表。 */
    @Query(
        """
        SELECT n.id FROM notes_fts fts
        JOIN notes n ON n.rowid = fts.rowid
        WHERE notes_fts MATCH :query
        ORDER BY n.updatedAt DESC
        """
    )
    suspend fun searchFtsIds(query: String): List<String>

    @Query("UPDATE notes SET isPinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    @Query("SELECT * FROM notes WHERE LOWER(title) LIKE LOWER(:q) ESCAPE '\\' ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun searchByTitlePrefix(q: String, limit: Int): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE LOWER(title) = LOWER(:title) ORDER BY updatedAt DESC LIMIT 1")
    suspend fun resolveByTitle(title: String): NoteEntity?

    @Query("UPDATE notes SET lastAiOp = :op, lastAiAt = :at WHERE id = :noteId")
    suspend fun updateAiMetadata(noteId: String, op: String, at: Long)

    /**
     * Backfill 路径用:取全部 note id(单列,比 `observeAll().first()` 省内存 + 不解 entity)。
     * R3 fix M8:让 `CompositeNoteLinker.recomputeAll` 真正能跑全量回填。
     */
    @Query("SELECT id FROM notes")
    suspend fun getAllIds(): List<String>
}
