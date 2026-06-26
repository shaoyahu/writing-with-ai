package com.yy.writingwithai.core.note.impl

import com.yy.writingwithai.core.data.db.NoteDao
import com.yy.writingwithai.core.data.db.dao.NoteLinkDao
import com.yy.writingwithai.core.data.db.dao.RelatedRow
import com.yy.writingwithai.core.data.db.entity.NoteLinkEntity
import com.yy.writingwithai.core.note.NoteLinker
import com.yy.writingwithai.core.note.RelatedNote
import com.yy.writingwithai.core.note.config.LinkWeights
import com.yy.writingwithai.core.prefs.NoteAssociationSettingsStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

@Singleton
class CompositeNoteLinker
@Inject
constructor(
    private val noteLinkDao: NoteLinkDao,
    private val noteDao: NoteDao,
    private val localLinker: LocalNoteLinker,
    private val wikilinkIndexer: WikilinkIndexer,
    private val entityBacklinker: EntityBacklinker,
    private val llmExtractor: LlmNoteLinkExtractor,
    private val assocSettings: NoteAssociationSettingsStore
) : NoteLinker {

    override suspend fun recomputeForNote(noteId: String) = coroutineScope {
        noteLinkDao.deleteBySrc(noteId)

        val localDeferred = async { localLinker.compute(noteId) }
        val wikiDeferred = async { wikilinkIndexer.index(noteId) }
        val entityDeferred = async { entityBacklinker.compute(noteId) }

        val localCandidates = localDeferred.await()
        val wikiRows = wikiDeferred.await()
        val entityRows = entityDeferred.await()

        val now = System.currentTimeMillis()
        val localRows = localCandidates.map { c ->
            NoteLinkEntity(
                srcNoteId = noteId,
                dstNoteId = c.dstNoteId,
                linkType = c.linkType,
                weight = c.weight,
                createdAt = c.createdAt,
                updatedAt = now,
                evidence = c.evidence
            )
        }
        val allRows = (localRows + wikiRows + entityRows)
        // tasks §3.5:2:1 截断(ENTITY_HIT 最多占 66%)
        val capped = NoteLinkCap.enforce(allRows)
        if (capped.isNotEmpty()) noteLinkDao.upsertAll(capped)

        // tasks §3.3:共享实体 < 1 时回退到 LLM 语义抽取
        val entityDstCount = entityRows.map { it.dstNoteId }.distinct().size
        if (assocSettings.isEnabled() && entityDstCount < 1) {
            try {
                llmExtractor.extractAndPersist(noteId)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
        }
    }

    /**
     * R3 fix M8:之前是死 SPI(`return 0`)。现在真做:取全部 note id,
     * 逐条串行 `recomputeForNote`(已经有 500ms debounce + scope 收口,并行反而撞 Room)。
     * 返回成功处理的 note 数 —— 任何 per-note 失败不算数,caller 可重试。
     */
    override suspend fun recomputeAll(): Int {
        val ids = noteDao.getAllIds()
        var ok = 0
        for (id in ids) {
            try {
                recomputeForNote(id)
                ok++
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // 单条 poisoned note 不能 abort 整个 backfill —— H8 教训。
                // 跳过并 log,继续下一条。
                android.util.Log.w("CompositeNoteLinker", "recomputeAll: failed for noteId=$id", e)
            }
        }
        return ok
    }

    override suspend fun getRelated(noteId: String, limit: Int): List<RelatedNote> {
        val rows: List<RelatedRow> = noteLinkDao.getRelated(noteId, limit)
        return rows.map { it.map() }
    }

    override suspend fun getBacklinks(noteId: String, limit: Int): List<RelatedNote> {
        val rows: List<RelatedRow> = noteLinkDao.getBacklinks(noteId, limit)
        return rows.map { it.map() }
    }
}

internal fun RelatedRow.map(): RelatedNote = RelatedNote(
    noteId = noteId,
    title = title,
    preview = preview,
    score = score,
    signals = LinkWeights.parseSignals(signals),
    evidence = evidence
)
