package com.yy.writingwithai.core.note.impl

import com.yy.writingwithai.core.data.db.entity.LinkType
import com.yy.writingwithai.core.data.db.entity.NoteLinkEntity
import kotlin.math.floor

/**
 * entity-extraction-association · note link candidate cap 策略。
 *
 * v1 规则:单笔记最多 [cap] 条，ENTITY_HIT 约占 [entityRatio](默认 66%),
 * 其余留给 LLM_EXTRACT / TAG_OVERLAP / WIKILINK / CONTENT_SIM。任一侧不足时由另一侧补齐。
 */
object NoteLinkCap {
    /**
     * entity-extraction-polish §2.4:加 [threshold] 形参 — score ≤ threshold 的候选在 cap 之前先剔除。
     * SQL 层(`NoteLinkDao.getRelated` / `getBacklinks`)也做同样过滤，这里冗余防御 +
     * 覆盖 entity backlinker / composite 路径写入的边(不走 DAO 聚合)。
     */
    fun enforce(
        candidates: List<NoteLinkEntity>,
        cap: Int = DEFAULT_CAP,
        entityRatio: Double = DEFAULT_ENTITY_RATIO,
        threshold: Double = DEFAULT_THRESHOLD
    ): List<NoteLinkEntity> {
        if (cap <= 0 || candidates.isEmpty()) return emptyList()

        val sorted = candidates.sortedByDescending { it.weight }
            .filter { it.weight > threshold }
        if (sorted.isEmpty()) return emptyList()
        if (sorted.size <= cap) return sorted

        val entityQuota = floor(cap * entityRatio).toInt().coerceIn(0, cap)
        val nonEntityQuota = cap - entityQuota
        val entityHits = sorted.filter { it.linkType == LinkType.ENTITY_HIT }
        val nonEntityHits = sorted.filter { it.linkType != LinkType.ENTITY_HIT }

        val selected = mutableListOf<NoteLinkEntity>()
        val selectedEntity = entityHits.take(entityQuota)
        val selectedNonEntity = nonEntityHits.take(nonEntityQuota)
        selected += selectedEntity
        selected += selectedNonEntity

        val remainingSlots = cap - selected.size
        if (remainingSlots > 0) {
            val selectedKeys = selected.map { it.key() }.toSet()
            val fillers = sorted
                .filterNot { it.key() in selectedKeys }
                .take(remainingSlots)
            selected += fillers
        }

        return selected
            .distinctBy { it.key() }
            .sortedByDescending { it.weight }
            .take(cap)
    }

    private fun NoteLinkEntity.key(): String = "$srcNoteId|$dstNoteId|$linkType"

    const val DEFAULT_CAP = 100
    private const val DEFAULT_ENTITY_RATIO = 0.66

    /** entity-extraction-polish §2.5:默认阈值对齐 SQL 当前值 0.10(用户可在设置页 0.05–0.80 调)。 */
    const val DEFAULT_THRESHOLD = 0.10
}
