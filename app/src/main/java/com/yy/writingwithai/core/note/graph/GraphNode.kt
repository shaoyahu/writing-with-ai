package com.yy.writingwithai.core.note.graph

/**
 * note-graph-view · 图节点 DTO(纯 data class,不依赖 Room / Android)。
 *
 * - [hopLevel]:0 = 中心笔记;1 = 1-hop 直接关联;2 = 2-hop 间接关联。
 * - [score]:沿用 `NoteLinkDao.RelatedRow.score`(多信号聚合后的归一化分)。
 * - [position]:由 [ForceLayout] 或 [CircularLayout] 写入,UI 层 Canvas 直接读。
 */
data class GraphNode(
    val noteId: String,
    val title: String,
    val score: Float,
    val hopLevel: Int,
    val position: NodeCoords? = null
)

/** x/y 为画布坐标(dp);UI 层负责按 pan / zoom 做 viewport 变换。 */
data class NodeCoords(val x: Float, val y: Float)
