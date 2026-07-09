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
 * - `search` 走 `title LIKE :q OR content LIKE :q`(roadmap §5.2 拍板，不引入 FTS)
 * - 所有写操作 `suspend`，由 Repository 在 IO 调度器上调用
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

    // fix M39 (full-review):EntityDetailViewModel.load 之前在 hits.mapNotNull 内
    // 单条 getById(每个 hit 一次 IO 查询 → N+1)。补批量接口,Room 用 IN(...) 一次拿全。
    // hits 上限 200 时,单次查询比 200 次快 10-100x(取决于 IO)。
    @Query("SELECT * FROM notes WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeById(id: String): Flow<NoteEntity?>

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query(
        "SELECT * FROM notes ORDER BY isPinned DESC, updatedAt DESC"
    )
    fun observeAll(): Flow<List<NoteEntity>>

    /**
     * fix-2026-06-30-full-review-r1 MEDIUM M4:observeRecent(limit) 走 SQL LIMIT,
     * 避免 `observeAll() + take(limit)` 加载全表再截断。大库下造成不必要的内存分配
     * 和 GC 压力(每次 Room invalidation 重新发射全表)。
     */
    @Query(
        "SELECT * FROM notes ORDER BY isPinned DESC, updatedAt DESC LIMIT :limit"
    )
    fun observeRecent(limit: Int): Flow<List<NoteEntity>>

    /**
     * 按 tag 筛选:JOIN `note_tags`，保持固定优先 + 时间倒序。
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
     * 搜索:title 或 content 含子串。空字符串由 Repository 拦截，不走 DAO。
     *
     * H4 修:`LIKE :q ESCAPE '\'` 让 Repository 在传入 `q` 前对 `%` / `_` / `\` 转义，
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

    /** search-enhancement · FTS4 全文搜索，返回匹配的 note id 列表。 */
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

    /**
     * fix M26 (full-review):批量按 title 解析 — 之前 [WikilinkIndexer.resolveTitles]
     * 走 N 次 [resolveByTitle],每次一条 SQL,wikilink 索引时 N 个 link 就 N 次 IO。
     * Room IN 查询一次性捞所有候选,内存里 groupBy title(LOWER 比较)对外暴露 title→entity 映射。
     * 重复 title 在 groupBy 里被压扁,只有第一个(updatedAt DESC)生效,与单条 resolveByTitle 语义对齐。
     */
    @Query("SELECT * FROM notes WHERE LOWER(title) IN (:titlesLower) ORDER BY updatedAt DESC")
    suspend fun resolveByTitles(titlesLower: List<String>): List<NoteEntity>

    @Query("UPDATE notes SET lastAiOp = :op, lastAiAt = :at WHERE id = :noteId")
    suspend fun updateAiMetadata(noteId: String, op: String, at: Long)

    /**
     * Backfill 路径用:取全部 note id(单列，比 `observeAll().first()` 省内存 + 不解 entity)。
     * R3 fix M8:让 `CompositeNoteLinker.recomputeAll` 真正能跑全量回填。
     */
    @Query("SELECT id FROM notes")
    suspend fun getAllIds(): List<String>
}
