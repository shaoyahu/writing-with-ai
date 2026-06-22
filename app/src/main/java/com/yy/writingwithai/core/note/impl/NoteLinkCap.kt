package com.yy.writingwithai.core.note.impl

import com.yy.writingwithai.core.data.db.entity.LinkType
import com.yy.writingwithai.core.data.db.entity.NoteLinkEntity
import kotlin.math.floor

/**
 * entity-extraction-association · note link candidate cap 策略。
 *
 * v1 规则:单笔记最多 [cap] 条,ENTITY_HIT 约占 [entityRatio](默认 66%),
 * 其余留给 LLM_EXTRACT / TAG_OVERLAP / WIKILINK / CONTENT_SIM。任一侧不足时由另一侧补齐。
 */
object NoteLinkCap {
    fun enforce(
        candidates: List<NoteLinkEntity>,
        cap: Int = DEFAULT_CAP,
        entityRatio: Double = DEFAULT_ENTITY_RATIO
    ): List<NoteLinkEntity> {
        if (cap <= 0 || candidates.isEmpty()) return emptyList()

        val sorted = candidates.sortedByDescending { it.weight }
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

    private const val DEFAULT_CAP = 100
    private const val DEFAULT_ENTITY_RATIO = 0.66
}
