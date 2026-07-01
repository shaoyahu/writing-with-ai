package com.yy.writingwithai.core.note.config

import com.yy.writingwithai.core.data.db.entity.LinkType

/**
 * 笔记链接打分权重与阈值。
 *
 * 集中在一处，便于后续 A/B 调参(spec.md §"Read query aggregation")。
 *
 * - `WEIGHT_WIKILINK` = 1.00:显式最强
 * - `WEIGHT_TAG_OVERLAP` = 1.50:tag 共现，在中等规模库(1k)下信号强
 * - `WEIGHT_CONTENT_SIM` = 1.00:内容相似
 * - `WEIGHT_LLM_EXTRACT` = 0.80:LLM 置信度，折扣避免幻觉高权重
 * - `SCORE_THRESHOLD` = 0.10:低于此分数的候选不进 `getRelated` 结果(spec.md §"Threshold filters noise")
 */
object LinkWeights {
    const val WEIGHT_WIKILINK: Float = 1.00f
    const val WEIGHT_TAG_OVERLAP: Float = 1.50f
    const val WEIGHT_CONTENT_SIM: Float = 1.00f
    const val WEIGHT_LLM_EXTRACT: Float = 0.80f
    const val SCORE_THRESHOLD: Float = 0.10f

    /**
     * bm25 归一化:把 FTS5 原始 bm25 score(典型 0..10)映射到 [0,1] 区间。
     * `weight = 1 - clamp(bm25 / 10)`(bm25 越小越相关，所以 1-x)。
     */
    fun normalizeBm25(bm25: Float): Float {
        val raw = (bm25 / 10f).coerceIn(0f, 1f)
        return (1f - raw).coerceIn(0f, 1f)
    }

    /**
     * 给定各信号 weight，聚合为最终 score(应用层 fallback，生产路径走 SQL)。
     *
     * @return 最终 score(可能超过 1，调用方按需 normalize)
     */
    fun aggregate(hasWikilink: Boolean, tagOverlap: Float, contentSim: Float, llmExtract: Float): Float {
        var s = 0f
        if (hasWikilink) s += WEIGHT_WIKILINK
        if (tagOverlap > 0f) s += WEIGHT_TAG_OVERLAP * tagOverlap
        s += WEIGHT_CONTENT_SIM * contentSim.coerceIn(0f, 1f)
        s += WEIGHT_LLM_EXTRACT * llmExtract.coerceIn(0f, 1f)
        return s
    }

    /**
     * 把 `RelatedRow.signals`(逗号分隔 LinkType 名字)解析为 Set<LinkType>。
     */
    fun parseSignals(raw: String): Set<LinkType> = raw.split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { name -> runCatching { LinkType.valueOf(name) }.getOrNull() }
        .toSet()
}
