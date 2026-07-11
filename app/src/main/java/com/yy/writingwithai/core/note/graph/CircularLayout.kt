package com.yy.writingwithai.core.note.graph

import javax.inject.Inject
import javax.inject.Singleton

/**
 * note-graph-view · Fallback 布局器。
 *
 * 当 [ForceLayout.converge] 在 maxIter 内不收敛(二分图 / 完全非连通环等)时,
 * 用确定性环形排版(`noteId.hashCode` 派生角度)给出"看起来像那么回事"的几何排列,
 * 避免屏上 render 失败或卡 spring oscillation。
 *
 * 设计:
 * - 中心节点固定 `(0, 0)`
 * - 1-hop 节点排外圈 `r = 100dp`
 * - 2-hop 节点排内圈 `r = 60dp`(在中心与 1-hop 之间)
 * - 每个 hop 的角度按 `noteId.hashCode().toDouble() / Int.MAX_VALUE * 2π` 计算,
 *   hashCode 是 stable,所以重建后角度不变(配合缓存更好定位)
 *
 * spec §"Note-graph-view layout shows center note" D8 兜底分支。
 */
@Singleton
class CircularLayout
@Inject
constructor() {
    /**
     * 计算节点坐标。
     *
     * @param snapshot 图快照(中心 + 1-hop + 2-hop)
     * @return `noteId` -> `(x, y)` 坐标映射(viewport 中心为 0,0)
     */
    fun fallback(snapshot: GraphSnapshot): Map<String, Pair<Float, Float>> {
        val out = mutableMapOf<String, Pair<Float, Float>>()
        val center = snapshot.nodes.find { it.noteId == snapshot.centerNodeId }
        if (center == null) return out
        // 中心固定到 (0, 0)
        out[center.noteId] = 0f to 0f

        val hop1 = snapshot.nodes.filter { it.hopLevel == 1 }
        val hop2 = snapshot.nodes.filter { it.hopLevel == 2 }
        // 100dp 视作 100 px(画布坐标系);Compose dp 转换由调用方决定
        // spec:外圈 r=100dp,内圈 r=60dp
        layoutRing(hop1, radius = 100f, out)
        layoutRing(hop2, radius = 60f, out)
        return out
    }

    private fun layoutRing(nodes: List<GraphNode>, radius: Float, out: MutableMap<String, Pair<Float, Float>>) {
        val n = nodes.size
        if (n == 0) return
        // 角度稳定:hashCode 派生,Int 转 double 再归一化到 [0, 2π)
        nodes.forEach { node ->
            val ang = (node.noteId.hashCode().toDouble() / Int.MAX_VALUE) * 2.0 * Math.PI
            val x = (radius * Math.cos(ang)).toFloat()
            val y = (radius * Math.sin(ang)).toFloat()
            out[node.noteId] = x to y
        }
    }
}
