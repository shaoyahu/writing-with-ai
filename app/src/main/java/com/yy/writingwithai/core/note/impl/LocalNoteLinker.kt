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
            val srcTokens = tokenize(src)
            if (srcTokens.isEmpty()) return 0f
            val dstLower = dst.lowercase()
            // fix-2026-06-25-review-r1 H3:CJK 大段(中文不分词)也要算 token,
            // 否则纯中文笔记 overlap=0,本地链接完全失效(LLM 抽取又被 C1 冻住)。
            val hits = srcTokens.count { dstLower.contains(it) }
            return (hits.toFloat() / srcTokens.size).coerceIn(0f, 1f)
        }

        /**
         * CJK-aware tokenizer:空白分词 + CJK 连续段抽 bigram(2-gram)。
         * 例:"今天 天气好" → ["今天","天天","气好", "今天", "天气好"] (后两是空白词,前者是 CJK bigram)。
         * 英文 / 数字 token 仍走 [a-z0-9]+ 段。
         */
        internal fun tokenize(text: String): List<String> {
            if (text.isEmpty()) return emptyList()
            val tokens = mutableListOf<String>()
            val lower = text.lowercase()
            // 1) 英文 / 数字 / 下划线 段(空白分词)。
            Regex("[a-z0-9_\\-]+").findAll(lower).forEach { m ->
                if (m.value.length > 1) tokens += m.value
            }
            // 2) CJK 段:从连续汉字里抽 bigram。
            val cjkRuns = Regex("[一-鿿]+").findAll(lower).map { it.value }.toList()
            cjkRuns.forEach { run ->
                if (run.length >= 2) {
                    for (i in 0..run.length - 2) tokens += run.substring(i, i + 2)
                }
            }
            return tokens
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
