package com.yy.writingwithai.core.data.repo

import androidx.room.withTransaction
import com.yy.writingwithai.core.data.db.AppDatabase
import com.yy.writingwithai.core.data.db.NoteDao
import com.yy.writingwithai.core.data.db.NoteTagDao
import com.yy.writingwithai.core.data.db.entity.NoteTagCrossRef
import com.yy.writingwithai.core.data.mapper.toEntity
import com.yy.writingwithai.core.data.mapper.toModel
import com.yy.writingwithai.core.data.model.Note
import com.yy.writingwithai.core.data.model.NoteWithTags
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

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
        private val db: AppDatabase,
        private val noteDao: NoteDao,
        private val noteTagDao: NoteTagDao,
    ) {
        /**
         * 列表屏用:根据搜索词 / tag 筛选返回 `Flow<List<NoteWithTags>>`。
         *
         * - `query` 为空或 null → 无搜索条件
         * - `tag` 为空或 null → 无 tag 筛选
         */
        fun observeNotesWithTags(
            query: String?,
            tag: String?,
        ): Flow<List<NoteWithTags>> {
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
        fun observeNoteWithTags(noteId: String): Flow<NoteWithTags?> =
            combine(
                noteDao.observeById(noteId).map { it?.toModel() },
                noteTagDao.observeTagsFor(noteId),
            ) { note, tags ->
                note?.let { NoteWithTags(it, tags) }
            }.distinctUntilChanged()

        suspend fun getNote(id: String): Note? = noteDao.getById(id)?.toModel()

        /**
         * upsert + 同步 tags(整组替换):
         * - 删掉该笔记的旧 tag 行
         * - 把传入的 tag 集合逐个写入(去重 + 去空)
         */
        suspend fun upsert(
            note: Note,
            tags: List<String>,
        ) {
            db.withTransaction {
                noteDao.upsert(note.toEntity())
                noteTagDao.removeAllForNote(note.id)
                tags
                    .asSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .forEach { tag -> noteTagDao.add(NoteTagCrossRef(noteId = note.id, tag = tag)) }
            }
        }

        suspend fun delete(id: String) {
            db.withTransaction {
                noteTagDao.removeAllForNote(id)
                noteDao.deleteById(id)
            }
        }

        suspend fun setPinned(
            id: String,
            pinned: Boolean,
        ) {
            noteDao.setPinned(id, pinned)
        }

        suspend fun updateAiMetadata(
            noteId: String,
            op: String,
            at: Long,
        ) {
            noteDao.updateAiMetadata(noteId, op, at)
        }

        fun observeAllTags(): Flow<List<String>> = noteTagDao.observeAllTags().distinctUntilChanged()
    }
