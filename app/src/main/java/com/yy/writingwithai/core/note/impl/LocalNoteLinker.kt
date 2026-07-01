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
        // R3 fix M1:escape BEFORE take(50) — 避免 take 落在反斜杠或 `%` 上，后续 escape
        // 把字符串挤长，LIKE 边界与 ESCAPE 对不齐。先 escape 完，再裁到 50,
        // 保证 `LIKE :q ESCAPE '\'` 的转义有效长度精确。
        val escaped = sanitizeForSearch(srcContent).take(LIKE_PREFIX_LEN)
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

        // R3 fix M1:escape 后裁剪的可见字符上限。原始 50 是"取 50 后 escape",
        // 现在是"escape 后取 50"，所以不需要再 take 一次(escape 已保证 LIKE 安全) —
        // 这里仍然设上限防止极长 content 把 LIKE pattern 撑成几百 KB。
        const val LIKE_PREFIX_LEN = 50

        fun sanitizeForSearch(c: String): String = c.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

        fun keywordOverlapWeight(src: String, dst: String): Float {
            val srcTokens = tokenize(src)
            if (srcTokens.isEmpty()) return 0f
            val dstLower = dst.lowercase()
            // fix-2026-06-25-review-r1 H3:CJK 大段(中文不分词)也要算 token,
            // 否则纯中文笔记 overlap=0，本地链接完全失效(LLM 抽取又被 C1 冻住)。
            val hits = srcTokens.count { dstLower.contains(it) }
            return (hits.toFloat() / srcTokens.size).coerceIn(0f, 1f)
        }

        /**
         * CJK-aware tokenizer:空白分词 + CJK 连续段抽 unigram + bigram(1 + 2-gram)。
         * 例:"今天天气好" → ["今","今天","天","天天","气","天气","好","气好"]。
         * 英文 / 数字 token 仍走 [a-z0-9]+ 段。
         *
         * R3 fix M2:之前只抽 bigram，导致单字 CJK 笔记(例:"人")完全没 token,
         * keyword overlap = 0，本地链接失效。现在每个汉字先入 unigram，再叠 bigram,
         * 短 / 长 CJK 都覆盖。
         */
        internal fun tokenize(text: String): List<String> {
            if (text.isEmpty()) return emptyList()
            val tokens = mutableListOf<String>()
            val lower = text.lowercase()
            // 1) 英文 / 数字 / 下划线 段(空白分词)。
            Regex("[a-z0-9_\\-]+").findAll(lower).forEach { m ->
                if (m.value.length > 1) tokens += m.value
            }
            // 2) CJK 段:unigram(每字) + bigram(相邻字)。
            val cjkRuns = Regex("[一-鿿]+").findAll(lower).map { it.value }.toList()
            cjkRuns.forEach { run ->
                run.forEach { ch -> tokens += ch.toString() }
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
