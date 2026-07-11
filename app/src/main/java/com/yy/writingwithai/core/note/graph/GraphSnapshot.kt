package com.yy.writingwithai.core.note.graph

/**
 * note-graph-view · 图快照 DTO。
 *
 * 一次 [GraphDataLoader.load] 输出 = 1 个 center + 至多 30 个 1-hop + 至多 20 个 2-hop = 51 节点
 * (硬 cap 50,实际 1+30+19 = 50 = 见 `LoaderConfig.maxNodes`)。
 *
 * - [nodes]:必须含 center(hopLevel=0)。
 * - [edges]:边 id 必须在 nodes 里出现至少一端(否则画出悬挂线)。
 * - [entityChips]:中心笔记的 `note_entities` 取前 8 个 surfaceForm,UI 层作 chip 浮在画布上。
 * - [truncated]:任一阶段溢出 cap 时置 true(截断事实不可丢失,UI 用来提示)。
 *
 * 注意:[position] 字段在 loader 阶段为 null,由 [ForceLayout] / [CircularLayout] 在 ViewModel 阶段写入。
 */
data class GraphSnapshot(
    val centerNodeId: String,
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>,
    val entityChips: List<String>,
    val truncated: Boolean
)

/**
 * GraphDataLoader 可调参数。默认值匹配 spec §"Note-graph-view layout" + tasks §1.3。
 *
 * - [hop1Limit]:1-hop 节点上限(默认 30)。
 * - [hop2Limit]:2-hop 节点上限(默认 20)。
 * - [hop2PerNeighborLimit]:对每个 1-hop 邻居最多收 4 个 2-hop 候选(防止单邻居吃掉全部 2-hop 配额)。
 * - [entityChipLimit]:entity chip 上限(默认 8)。
 * - [maxNodes]:硬总节点 cap(1 center + hop1 + hop2,默认 50 = spec)。
 */
data class LoaderConfig(
    val hop1Limit: Int = 30,
    val hop2Limit: Int = 20,
    val hop2PerNeighborLimit: Int = 4,
    val entityChipLimit: Int = 8,
    val maxNodes: Int = 50
)
