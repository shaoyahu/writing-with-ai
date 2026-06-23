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
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
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
    private val db: AppDatabase,
    private val noteDao: NoteDao,
    private val noteTagDao: NoteTagDao,
    private val noteAttachmentDao: NoteAttachmentDao,
    private val widgetUpdater: QuickNoteWidgetUpdater,
    private val noteLinker: NoteLinker,
    private val attachmentStore: AttachmentStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recomputeFlow = MutableSharedFlow<String>(extraBufferCapacity = 64)

    /** AI replace 触发通知:detail ViewModel 收集,收到 noteId 后强刷。 */
    val noteUpdateEvents = MutableSharedFlow<String>(extraBufferCapacity = 32, replay = 1)

    init {
        scope.launch {
            recomputeFlow
                .debounce(DEBOUNCE_MS)
                .collect { noteId ->
                    try {
                        noteLinker.recomputeForNote(noteId)
                    } catch (_: Exception) { /* fire-and-forget */ }
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
        // C6 修:BuildConfig.DEBUG gate,release 包不打 noteId + tags 到 logcat(隐私)。
        if (BuildConfig.DEBUG) {
            android.util.Log.d("NoteRepo", "upsert noteId=${note.id} tags=$tags")
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
                android.util.Log.d("NoteRepo", "cleaned tags to insert: $cleaned")
            }
            cleaned.forEach { tag -> noteTagDao.add(NoteTagCrossRef(noteId = note.id, tag = tag)) }
        }
        // H3 修:widget 刷新包 NonCancellable,避免 viewModelScope 取消导致
        // 数据库已落库但 widget 没刷新(用户 back 时 race)。
        withContext(NonCancellable) { widgetUpdater.updateAll(context) }
        recomputeFlow.tryEmit(note.id)
    }

    suspend fun delete(id: String) {
        db.withTransaction {
            noteTagDao.removeAllForNote(id)
            noteDao.deleteById(id)
        }
        // H3 修:同上,NonCancellable 包 widget 刷新。
        withContext(NonCancellable) { widgetUpdater.updateAll(context) }
    }

    suspend fun setPinned(id: String, pinned: Boolean) {
        noteDao.setPinned(id, pinned)
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
