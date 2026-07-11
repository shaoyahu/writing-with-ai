package com.yy.writingwithai.core.note.graph

import com.yy.writingwithai.core.data.db.NoteDao
import com.yy.writingwithai.core.data.db.dao.NoteLinkDao
import com.yy.writingwithai.core.data.db.dao.RelatedRow
import com.yy.writingwithai.core.data.db.dao.entity.NoteEntityDao
import com.yy.writingwithai.core.note.NoteLinker
import com.yy.writingwithai.core.note.RelatedNote
import com.yy.writingwithai.core.prefs.NoteAssociationSettingsStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * note-graph-view · 把 [NoteLinker] + [NoteDao] + [NoteEntityDao] 聚合成一个 [GraphSnapshot]。
 *
 * 流程(对应 spec §"Note-graph-view layout shows center note, related notes, and entities" + tasks §1.3):
 * 1. 拿 1-hop 节点:[NoteLinker.getRelated] ∪ [NoteLinker.getBacklinks] dedup by noteId,截 [config.hop1Limit]。
 * 2. 对每个 1-hop 节点,走 [NoteLinkDao.getRelated](限 [config.hop2PerNeighborLimit]) 拿 2-hop 候选,
 *    整体排除中心 + 1-hop 已有,按 score 降序,截 [config.hop2Limit]。
 * 3. 拿 entity chips:[NoteEntityDao.getByNoteId](center) 取 surfaceForm,截 [config.entityChipLimit]。
 * 4. 整体 [config.maxNodes] 硬 cap,任一阶段溢 cap → [GraphSnapshot.truncated] = true。
 *
 * 不引入新 DAO / 新表 / Room schema 不动(见 design §D3)。
 */
@Singleton
class GraphDataLoader
@Inject
constructor(
    private val noteLinker: NoteLinker,
    private val noteLinkDao: NoteLinkDao,
    private val noteDao: NoteDao,
    private val noteEntityDao: NoteEntityDao,
    private val assocSettings: NoteAssociationSettingsStore
) {

    /**
     * 加载中心笔记 [centerNoteId] 的图快照。
     *
     * @param centerNoteId 中心笔记 id(必存在)。
     * @param config 默认 [LoaderConfig](默认值与 spec 对齐)。
     */
    suspend fun load(centerNoteId: String, config: LoaderConfig = LoaderConfig()): GraphSnapshot {
        val centerNote = noteDao.getById(centerNoteId)
            ?: return emptySnapshot(centerNoteId)

        val centerNode = GraphNode(
            noteId = centerNoteId,
            title = centerNote.title,
            score = 0f,
            hopLevel = 0
        )

        // ---- 1-hop:getRelated ∪ getBacklinks dedup by noteId,score 降序,截 hop1Limit ----
        val threshold = assocSettings.threshold().toDouble()
        val outgoing: List<RelatedNote> = noteLinker.getRelated(centerNoteId, config.hop1Limit)
        val incoming: List<RelatedNote> = noteLinker.getBacklinks(centerNoteId, config.hop1Limit)
        val hop1ById: Map<String, RelatedNote> = (outgoing + incoming)
            .associateBy { it.noteId }
        val hop1Truncated = hop1ById.size >= config.hop1Limit

        // ---- 2-hop:对每个 1-hop 节点 dao.getRelated(perNeighborLimit),排除中心 + 1-hop 已有 ----
        val hop1Ids: Set<String> = hop1ById.keys
        val existing = buildSet {
            add(centerNoteId)
            addAll(hop1Ids)
        }
        val hop2Scores: MutableMap<String, Float> = mutableMapOf()
        for (hop1Id in hop1Ids) {
            val more: List<RelatedRow> = noteLinkDao.getRelated(
                hop1Id,
                limit = config.hop2PerNeighborLimit,
                threshold = threshold
            )
            for (row in more) {
                if (row.noteId in existing) continue
                // score 取最大(同一节点可能被多个 1-hop 命中)
                val prev = hop2Scores[row.noteId]
                if (prev == null || row.score > prev) hop2Scores[row.noteId] = row.score
            }
        }
        val hop2Sorted = hop2Scores.entries.sortedByDescending { it.value }
        val hop2Truncated = hop2Sorted.size > config.hop2Limit
        val hop2Picked = hop2Sorted.take(config.hop2Limit)

        // ---- 整体硬 cap:1 center + 30 1-hop + 20 2-hop = 51 → cap 50 ----
        // reserved = center(1);remainingForHop1 = maxNodes - 1 - hop2Limit(锁 2-hop 配额)
        val reservedHop2 = config.hop2Limit
        val remainingForHop1 = (config.maxNodes - 1 - reservedHop2).coerceAtLeast(0)
        val hop1Picked = hop1ById.values
            .sortedByDescending { it.score }
            .take(remainingForHop1.coerceAtMost(config.hop1Limit))

        // 节点列表:center + 1-hop + 2-hop
        // fix-review-r1 F8 4.2:2-hop 节点原本 title 写死空串 ""(原 L98-100)。
        // 节点渲染时 showNodeTitle 把空串降级成"无标题",图里 2-hop 全显示 "(Untitled)",
        // 实际上 RelatedRow 已经写回 title 字段在 NoteLinkDao,但 loader 没接住。
        // 改为 hop2 候选 id 一次性走 noteDao.getByIds(单 SQL IN(...),不走 N+1),
        // 1-hop 用 RelatedNote.title(已有)。
        val hop2Titles: Map<String, String> = hop2Picked
            .map { it.key }
            .takeIf { it.isNotEmpty() }
            ?.let { noteDao.getByIds(it).associate { it.id to it.title } }
            ?: emptyMap()
        val nodes: List<GraphNode> = buildList {
            add(centerNode)
            addAll(hop1Picked.map { GraphNode(it.noteId, it.title, it.score, hopLevel = 1) })
            for ((id, score) in hop2Picked) {
                add(GraphNode(id, hop2Titles[id].orEmpty(), score, hopLevel = 2))
            }
        }

        // ---- edges:每条 RelatedNote 的主要 linkType 当一条边(center↔1-hop)----
        val edges: List<GraphEdge> = buildList {
            for (hop1 in hop1Picked) {
                val primary = hop1.signals.firstOrNull()
                    ?: com.yy.writingwithai.core.data.db.entity.LinkType.CONTENT_SIM
                add(
                    GraphEdge(
                        srcId = centerNoteId,
                        dstId = hop1.noteId,
                        weight = hop1.score.coerceAtLeast(0.01f),
                        linkType = primary
                    )
                )
            }
            // 2-hop:边上挂哪个 1-hop 是次要信息,简化为 1-hop↔2-hop 配对 → 留作 v2 fallback
        }

        // ---- entity chips:中心笔记 surfaceForm 前 N 个 ----
        val entityChips: List<String> = noteEntityDao.getByNoteId(centerNoteId)
            .map { it.surfaceForm }
            .filter { it.isNotBlank() }
            .distinct()
            .take(config.entityChipLimit)

        val truncated = hop1Truncated || hop2Truncated ||
            (hop1Picked.size < hop1ById.size) ||
            (hop2Picked.size < hop2Sorted.size)

        return GraphSnapshot(
            centerNodeId = centerNoteId,
            nodes = nodes,
            edges = edges,
            entityChips = entityChips,
            truncated = truncated
        )
    }

    private fun emptySnapshot(centerNoteId: String): GraphSnapshot = GraphSnapshot(
        centerNodeId = centerNoteId,
        nodes = listOf(
            GraphNode(noteId = centerNoteId, title = "", score = 0f, hopLevel = 0)
        ),
        edges = emptyList(),
        entityChips = emptyList(),
        truncated = false
    )
}
