package com.yy.writingwithai.core.note.impl

import com.yy.writingwithai.core.data.db.entity.LinkType
import com.yy.writingwithai.core.data.db.entity.NoteLinkEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** entity-extraction-association · NoteLinkCap 单测(tasks §10.3)。 */
class NoteLinkCapTest {
    @Test
    fun `enforce returns empty for cap zero`() {
        val result = NoteLinkCap.enforce(listOf(link("a", LinkType.ENTITY_HIT, 1f)), cap = 0)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `enforce keeps sorted list when under cap`() {
        val result = NoteLinkCap.enforce(
            listOf(
                link("a", LinkType.ENTITY_HIT, 0.5f),
                link("b", LinkType.LLM_EXTRACT, 0.9f)
            ),
            cap = 10
        )
        assertEquals(listOf("b", "a"), result.map { it.dstNoteId })
    }

    @Test
    fun `enforce applies two-to-one ratio when both groups exceed quotas`() {
        val candidates = buildList {
            repeat(80) { i -> add(link("e$i", LinkType.ENTITY_HIT, 1f - i * 0.001f)) }
            repeat(80) { i -> add(link("l$i", LinkType.LLM_EXTRACT, 0.9f - i * 0.001f)) }
        }
        val result = NoteLinkCap.enforce(candidates, cap = 100, entityRatio = 0.66)
        assertEquals(100, result.size)
        assertEquals(66, result.count { it.linkType == LinkType.ENTITY_HIT })
        assertEquals(34, result.count { it.linkType != LinkType.ENTITY_HIT })
    }

    @Test
    fun `enforce lets non-entity fill when entity insufficient`() {
        val candidates = buildList {
            repeat(10) { i -> add(link("e$i", LinkType.ENTITY_HIT, 1f - i * 0.001f)) }
            repeat(150) { i -> add(link("l$i", LinkType.LLM_EXTRACT, 0.9f - i * 0.001f)) }
        }
        val result = NoteLinkCap.enforce(candidates, cap = 100, entityRatio = 0.66)
        assertEquals(100, result.size)
        assertEquals(10, result.count { it.linkType == LinkType.ENTITY_HIT })
        assertEquals(90, result.count { it.linkType != LinkType.ENTITY_HIT })
    }

    @Test
    fun `enforce lets entity fill when non-entity insufficient`() {
        val candidates = buildList {
            repeat(150) { i -> add(link("e$i", LinkType.ENTITY_HIT, 1f - i * 0.001f)) }
            repeat(10) { i -> add(link("l$i", LinkType.LLM_EXTRACT, 0.9f - i * 0.001f)) }
        }
        val result = NoteLinkCap.enforce(candidates, cap = 100, entityRatio = 0.66)
        assertEquals(100, result.size)
        assertEquals(90, result.count { it.linkType == LinkType.ENTITY_HIT })
        assertEquals(10, result.count { it.linkType != LinkType.ENTITY_HIT })
    }

    // entity-extraction-polish §6.5:阈值联动 2 case

    @Test
    fun `enforce drops low-score candidates before 2-to-1 ratio truncation`() {
        val candidates = buildList {
            // 200 个，score ∈ [0.05, 0.95]
            repeat(100) { i -> add(link("e$i", LinkType.ENTITY_HIT, 0.95f - i * 0.005f)) }
            repeat(100) { i -> add(link("l$i", LinkType.LLM_EXTRACT, 0.50f - i * 0.003f)) }
        }
        // threshold 0.25 → ENTITY_HIT 全保留(score 0.50..0.95),LLM_EXTRACT 部分掉到 ≤ 0.25
        val result = NoteLinkCap.enforce(candidates, cap = 100, entityRatio = 0.66, threshold = 0.25)
        assertEquals(100, result.size)
        // ENTITY_HIT 高分全进
        assertEquals(66, result.count { it.linkType == LinkType.ENTITY_HIT })
        // LLM_EXTRACT 只剩 score > 0.25 的部分
        assertTrue(result.count { it.linkType == LinkType.LLM_EXTRACT } <= 34)
    }

    @Test
    fun `enforce returns empty when all candidates below threshold`() {
        val candidates = listOf(
            link("a", LinkType.ENTITY_HIT, 0.05f),
            link("b", LinkType.LLM_EXTRACT, 0.08f)
        )
        val result = NoteLinkCap.enforce(candidates, cap = 100, threshold = 0.25)
        assertTrue(result.isEmpty())
    }

    private fun link(dst: String, type: LinkType, weight: Float): NoteLinkEntity = NoteLinkEntity(
        srcNoteId = "src",
        dstNoteId = dst,
        linkType = type,
        weight = weight,
        createdAt = 1L,
        updatedAt = 1L,
        evidence = null
    )
}
