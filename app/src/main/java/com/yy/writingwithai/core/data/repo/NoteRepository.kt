package com.yy.writingwithai.core.data.repo

import android.content.Context
import androidx.room.withTransaction
import com.yy.writingwithai.BuildConfig
import com.yy.writingwithai.core.data.db.AppDatabase
import com.yy.writingwithai.core.data.db.NoteDao
import com.yy.writingwithai.core.data.db.NoteTagDao
import com.yy.writingwithai.core.data.db.dao.NoteAttachmentDao
import com.yy.writingwithai.core.data.db.entity.NoteTagCrossRef
import com.yy.writingwithai.core.data.mapper.toEntity
import com.yy.writingwithai.core.data.mapper.toModel
import com.yy.writingwithai.core.data.model.Note
import com.yy.writingwithai.core.data.model.NoteWithTags
import com.yy.writingwithai.core.media.AttachmentStore
import com.yy.writingwithai.core.note.NoteLinker
import com.yy.writingwithai.core.widget.QuickNoteWidgetUpdater
import com.yy.writingwithai.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Note 业务仓库。
 *
 * - 包装 [NoteDao] + [NoteTagDao],只暴露 [Note](领域模型),不暴露 Entity
 * - `observeNotesWithTags` 合并"笔记列表" + "全表交叉引用"成 `List<NoteWithTags>`,
 *   供列表屏直接渲染(spec §"List ordering" + "Tag many-to-many")
 * - 删除 / upsert 走事务:`notes` 行 + `note_tags` 行要么都改要么都不改
 *
 * 见 [openspec.changes.quick-note-feature.specs.quick-note.spec] §"Note CRUD via Repository"。
 */
@Singleton
class NoteRepository
@Inject
constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val db: AppDatabase,
    private val noteDao: NoteDao,
    private val noteTagDao: NoteTagDao,
    private val noteAttachmentDao: NoteAttachmentDao,
    private val widgetUpdater: QuickNoteWidgetUpdater,
    private val noteLinker: NoteLinker,
    private val attachmentStore: AttachmentStore
) {
    // hardening H-5:scope 改为 Hilt 注入的 @ApplicationScope,不再自管 SupervisorJob。
    // 进程退出时由进程死亡隐式 cancel,不再 leak。
    private val recomputeFlow = MutableSharedFlow<String>(extraBufferCapacity = 64)

    /** AI replace 触发通知:detail ViewModel 收集,收到 noteId 后强刷。 */
    val noteUpdateEvents = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 32)

    init {
        scope.launch {
            recomputeFlow
                .debounce(DEBOUNCE_MS)
                .collect { noteId ->
                    // R3 fix M4 + M10:之前 `catch (_: Exception)` 既吞 CancellationException
                    // 又静默 drop 业务异常。分层捕获:
                    // 1) CancellationException 必须 rethrow —— fire-and-forget scope 取消时
                    //    不应被当"重算失败"。
                    // 2) 其他异常:log warning 让失败可观测(原来完全无声)。
                    try {
                        noteLinker.recomputeForNote(noteId)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        android.util.Log.w("NoteRepo", "recomputeForNote failed for noteId=$noteId", e)
                    }
                }
        }
    }

    /**
     * 列表屏用:根据搜索词 / tag 筛选返回 `Flow<List<NoteWithTags>>`。
     *
     * - `query` 为空或 null → 无搜索条件
     * - `tag` 为空或 null → 无 tag 筛选
     */
    fun observeNotesWithTags(query: String?, tag: String?): Flow<List<NoteWithTags>> {
        val notesFlow: Flow<List<Note>> =
            when {
                !tag.isNullOrBlank() -> noteDao.observeByTag(tag).map { list -> list.map { it.toModel() } }
                !query.isNullOrBlank() -> {
                    // H4 修:转义 `%` `_` `\` 避免用户输入被当通配符;配合 DAO 的 ESCAPE '\\'。
                    val escaped =
                        query.trim()
                            .replace("\\", "\\\\")
                            .replace("%", "\\%")
                            .replace("_", "\\_")
                    val q = "%$escaped%"
                    noteDao.search(q).map { list -> list.map { it.toModel() } }
                }
                else -> noteDao.observeAll().map { list -> list.map { it.toModel() } }
            }
        return combine(notesFlow, noteTagDao.observeAllCrossRefs()) { notes, crossRefs ->
            val byNote = crossRefs.groupBy({ it.noteId }, { it.tag })
            notes.map { NoteWithTags(it, byNote[it.id].orEmpty()) }
        }.distinctUntilChanged()
    }

    /** 详情 / 编辑屏用:单条 note + 它的 tag 列表。 */
    fun observeNoteWithTags(noteId: String): Flow<NoteWithTags?> = combine(
        noteDao.observeById(noteId).map { it?.toModel() },
        noteTagDao.observeTagsFor(noteId)
    ) { note, tags ->
        note?.let { NoteWithTags(it, tags) }
    }.distinctUntilChanged()

    suspend fun getNote(id: String): Note? = noteDao.getById(id)?.toModel()

    /**
     * upsert + 同步 tags(整组替换):
     * - 删掉该笔记的旧 tag 行
     * - 把传入的 tag 集合逐个写入(去重 + 去空)
     */
    suspend fun upsert(note: Note, tags: List<String>) {
        // M6 修:仅记 tags.size,不打印 noteId / tags 内容(隐私)。
        // C6 修:BuildConfig.DEBUG gate,release 包不打 noteId + tags 到 logcat(隐私)。
        if (BuildConfig.DEBUG) {
            android.util.Log.d("NoteRepo", "upsert noteId=${note.id} tags.size=${tags.size}")
        }
        db.withTransaction {
            noteDao.upsert(note.toEntity())
            noteTagDao.removeAllForNote(note.id)
            val cleaned = tags
                .asSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .toList()
            if (BuildConfig.DEBUG) {
                android.util.Log.d("NoteRepo", "cleaned tags to insert: size=${cleaned.size}")
            }
            cleaned.forEach { tag -> noteTagDao.add(NoteTagCrossRef(noteId = note.id, tag = tag)) }
        }
        // H3 修:widget 刷新包 NonCancellable,避免 viewModelScope 取消导致
        // 数据库已落库但 widget 没刷新(用户 back 时 race)。
        withContext(NonCancellable) { widgetUpdater.updateAll(context) }
        recomputeFlow.tryEmit(note.id)
    }

    suspend fun delete(id: String) {
        // review r2 修:删除笔记时清理附件文件 + DB 行,避免磁盘泄漏。
        // review r3 修 H5:文件清理必须**在 DB 事务之后**,否则 DB 行指向不存在的 localPath。
        // 顺序:DB 事务先删行(attachment row + tag row + note row),再删文件。
        // 若文件删除失败,DB 已删,下次 attach-less delete 是干净的;文件会成 orphan,
        // 但 orphan attachment dir 没 DB 行引用,后续 cleanup 仍能回收。
        db.withTransaction {
            noteAttachmentDao.deleteForNote(id)
            noteTagDao.removeAllForNote(id)
            noteDao.deleteById(id)
        }
        try {
            attachmentStore.deleteAllForNote(id)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // 文件删除失败不阻塞业务(DB 已删),但要 log 出来便于 orphan 排查
            android.util.Log.w("NoteRepo", "delete: attachment cleanup failed for noteId=$id", e)
        }
        // H3 修:同上,NonCancellable 包 widget 刷新。
        withContext(NonCancellable) { widgetUpdater.updateAll(context) }
    }

    suspend fun setPinned(id: String, pinned: Boolean) {
        noteDao.setPinned(id, pinned)
    }

    /**
     * note-list-card-actions · 给单条笔记挂已有 tag(幂等)。
     *
     * - NoteTagDao.add 用 IGNORE 策略 → 重复挂同一 tag 自动 no-op,不需要先查再插
     * - 走事务外(单条 INSERT)+ NonCancellable 包 widget 刷新(同 upsert/delete 模式)
     * - tag 自动 trim + 空过滤,与 upsert 的 cleaned 行为一致
     */
    suspend fun addTagToNote(noteId: String, tag: String) {
        val cleaned = tag.trim()
        if (cleaned.isEmpty()) return
        if (BuildConfig.DEBUG) {
            android.util.Log.d("NoteRepo", "addTagToNote noteId=$noteId tag.size=${cleaned.length}")
        }
        noteTagDao.add(NoteTagCrossRef(noteId = noteId, tag = cleaned))
        withContext(NonCancellable) { widgetUpdater.updateAll(context) }
        recomputeFlow.tryEmit(noteId)
    }

    suspend fun updateAiMetadata(noteId: String, op: String, at: Long) {
        noteDao.updateAiMetadata(noteId, op, at)
    }

    fun observeAllTags(): Flow<List<String>> = noteTagDao.observeAllTags().distinctUntilChanged()

    /** M4-1:Widget 取最近 N 条笔记(内存截断,Room SQL 不变);Room 已按 updatedAt desc 排序(M1 既有 `observeAll()`)。 */
    fun observeRecent(limit: Int): Flow<List<Note>> =
        noteDao.observeAll().map { list -> list.take(limit).map { it.toModel() } }

    companion object {
        private const val DEBOUNCE_MS = 500L
    }
}
