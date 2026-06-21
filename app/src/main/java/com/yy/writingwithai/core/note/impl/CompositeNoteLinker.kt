package com.yy.writingwithai.core.note.impl

import com.yy.writingwithai.core.data.db.dao.NoteLinkDao
import com.yy.writingwithai.core.data.db.dao.RelatedRow
import com.yy.writingwithai.core.data.db.entity.NoteLinkEntity
import com.yy.writingwithai.core.note.NoteLinker
import com.yy.writingwithai.core.note.RelatedNote
import com.yy.writingwithai.core.note.config.LinkWeights
import com.yy.writingwithai.feature.settings.NoteAssociationSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

@Singleton
class CompositeNoteLinker
@Inject
constructor(
    private val noteLinkDao: NoteLinkDao,
    private val localLinker: LocalNoteLinker,
    private val wikilinkIndexer: WikilinkIndexer,
    private val llmExtractor: LlmNoteLinkExtractor,
    private val assocSettings: NoteAssociationSettings
) : NoteLinker {

    override suspend fun recomputeForNote(noteId: String) = coroutineScope {
        noteLinkDao.deleteBySrc(noteId)

        val localDeferred = async { localLinker.compute(noteId) }
        val wikiDeferred = async { wikilinkIndexer.index(noteId) }

        val localCandidates = localDeferred.await()
        val wikiRows = wikiDeferred.await()

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
        val allRows = localRows + wikiRows
        if (allRows.isNotEmpty()) noteLinkDao.upsertAll(allRows)

        if (assocSettings.isEnabled()) {
            try {
                llmExtractor.extractAndPersist(noteId)
            } catch (_: Exception) { }
        }
    }

    override suspend fun recomputeAll(): Int = 0

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
    signals = LinkWeights.parseSignals(signals)
)
