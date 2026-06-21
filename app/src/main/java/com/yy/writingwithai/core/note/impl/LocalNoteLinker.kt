package com.yy.writingwithai.core.note.impl

import com.yy.writingwithai.core.data.db.NoteDao
import com.yy.writingwithai.core.data.db.NoteTagDao
import com.yy.writingwithai.core.data.db.entity.LinkType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class LocalNoteLinker
@Inject
constructor(
    private val noteDao: NoteDao,
    private val noteTagDao: NoteTagDao
) {
    suspend fun compute(srcNoteId: String): List<LocalLinkCandidate> {
        val src = noteDao.getById(srcNoteId) ?: return emptyList()
        val srcTags: List<String> = noteTagDao.observeTagsFor(srcNoteId).first()
        val tagCandidates = computeTagJaccard(srcNoteId, srcTags)
        val contentCandidates = computeContentSim(srcNoteId, src.content)
        return tagCandidates + contentCandidates
    }

    private suspend fun computeTagJaccard(srcNoteId: String, srcTags: List<String>): List<LocalLinkCandidate> {
        if (srcTags.isEmpty()) return emptyList()
        val srcTagsSet = srcTags.toSet()
        val allRefs = noteTagDao.observeAllCrossRefs().first()
        val byNote = allRefs.groupBy({ it.noteId }, { it.tag })
        val now = System.currentTimeMillis()
        return byNote.mapNotNull { (noteId, tags) ->
            if (noteId == srcNoteId) return@mapNotNull null
            val tagsSet = tags.toSet()
            val intersection = srcTagsSet intersect tagsSet
            if (intersection.isEmpty()) return@mapNotNull null
            val union = srcTagsSet + tagsSet
            val jaccard = intersection.size.toFloat() / union.size.toFloat()
            LocalLinkCandidate(
                dstNoteId = noteId,
                linkType = LinkType.TAG_OVERLAP,
                weight = jaccard,
                createdAt = now,
                updatedAt = now,
                evidence = buildTagEvidence(intersection.toList())
            )
        }
    }

    private suspend fun computeContentSim(srcNoteId: String, srcContent: String): List<LocalLinkCandidate> {
        if (srcContent.isBlank()) return emptyList()
        val escaped = sanitizeForSearch(srcContent.take(50))
        val q = "%$escaped%"
        val matches = noteDao.search(q).first().filter { it.id != srcNoteId }
        if (matches.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        return matches.take(LIKE_TOP_K).map { m ->
            val weight = keywordOverlapWeight(srcContent, m.content)
            LocalLinkCandidate(
                dstNoteId = m.id,
                linkType = LinkType.CONTENT_SIM,
                weight = weight,
                createdAt = now,
                updatedAt = now
            )
        }
    }

    companion object {
        const val LIKE_TOP_K = 20

        fun sanitizeForSearch(c: String) = c.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_").take(50)

        fun keywordOverlapWeight(src: String, dst: String): Float {
            val srcWords = src.split(Regex("\\s+")).map { it.lowercase().trim() }.filter { it.length > 1 }
            if (srcWords.isEmpty()) return 0f
            val dstLower = dst.lowercase()
            val hits = srcWords.count { dstLower.contains(it) }
            return (hits.toFloat() / srcWords.size).coerceIn(0f, 1f)
        }

        fun buildTagEvidence(sharedTags: List<String>): String {
            val escaped = sharedTags.sorted().joinToString(",") { "\"$it\"" }
            return "{\"sharedTags\":[$escaped]}"
        }
    }
}

data class LocalLinkCandidate(
    val dstNoteId: String,
    val linkType: LinkType,
    val weight: Float,
    val createdAt: Long,
    val updatedAt: Long,
    val evidence: String? = null
)
