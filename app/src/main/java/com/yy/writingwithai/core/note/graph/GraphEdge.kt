package com.yy.writingwithai.core.note.graph

import com.yy.writingwithai.core.data.db.entity.LinkType

/**
 * note-graph-view · 图边 DTO。
 *
 * 边方向按数据源约定:[srcId] = `note_links.srcNoteId` 或 1-hop 节点,[dstId] = 邻居;
 * UI 层方向是参考,展示为无向 line(箭头仅用于 WIKILINK,见 NoteGraphCanvas)。
 */
data class GraphEdge(
    val srcId: String,
    val dstId: String,
    val weight: Float,
    val linkType: LinkType
)
