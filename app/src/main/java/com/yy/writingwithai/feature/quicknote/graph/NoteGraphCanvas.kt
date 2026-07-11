package com.yy.writingwithai.feature.quicknote.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yy.writingwithai.R
import com.yy.writingwithai.core.data.db.entity.LinkType
import com.yy.writingwithai.core.note.graph.GraphSnapshot
import com.yy.writingwithai.core.note.graph.NodeCoords
import kotlin.math.exp
import kotlin.math.hypot

/**
 * note-graph-view · Canvas + 缓存 + pan/zoom 手势(tasks §3.2 - §3.6)。
 *
 * 布局:
 * - 整屏 Canvas 填满父 Box
 * - 顶部叠 entity chip 浮层(走 Compose FlowRow,高质量文本)
 * - 单 `Modifier.graphicsLayer` 处理 pan/zoom(单 transform 比逐 drawBlock 便宜)
 *
 * 颜色:
 * - 中心节点:surfaceVariant → primaryContainer 渐变,半径 1.5×
 * - 1-hop / 2-hop:primary 单色
 * - 边:outline + alpha 0.5,粗细按 weight 1~3dp;WIKILINK 实线,其它虚线
 *
 * tap/drag 阈值:点击命中 `nodeRadius + 12dp`;拖拽 < 12dp 走 detectTapGestures,≥ 12dp 走 transformGestures
 */
@Composable
fun NoteGraphCanvas(
    snapshot: GraphSnapshot,
    coords: Map<String, NodeCoords>,
    onNodeTap: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var translationX by remember { mutableFloatStateOf(0f) }
    var translationY by remember { mutableFloatStateOf(0f) }
    var scale by remember { mutableFloatStateOf(1f) }

    val density = LocalDensity.current
    val tapThresholdPx = with(density) { 12.dp.toPx() }

    val scheme = MaterialTheme.colorScheme
    val centerColor = scheme.primary.copy(alpha = 0.85f)
    val nodeColor = scheme.primary.copy(alpha = 0.55f)
    val edgeColor = scheme.outline.copy(alpha = 0.5f)
    val centerStroke = scheme.outline

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // fix-review-r1 F7:a11y H4 — Canvas 没语义节点,TalkBack 滑过图直接沉默。
        // 加一层不参与渲染的 a11y-only overlay(visible 关闭,但 focusable + contentDescription),
        // 列出全部节点 + 边,让 TalkBack 用户能听到图大致结构。
        val graphSummary = stringResource(R.string.note_graph_a11y_summary)
        val nodeFmt = stringResource(R.string.note_graph_node_fmt)
        val edgeFmt = stringResource(R.string.note_graph_edge_fmt)
        val untitledLabel = stringResource(R.string.note_graph_node_untitled)
        val nodeTitleMap = remember(snapshot.nodes) { snapshot.nodes.associate { it.noteId to it.title } }
        val a11yDescription = remember(snapshot, graphSummary, nodeFmt, edgeFmt, untitledLabel) {
            buildString {
                append(graphSummary)
                append(". ")
                if (snapshot.nodes.isNotEmpty()) {
                    append("Nodes: ")
                    append(
                        snapshot.nodes.joinToString(separator = "; ") { n ->
                            nodeFmt.format(n.title.ifBlank { untitledLabel })
                        }
                    )
                    append(". ")
                }
                if (snapshot.edges.isNotEmpty()) {
                    append("Edges: ")
                    append(
                        snapshot.edges.joinToString(separator = "; ") { e ->
                            val src = nodeTitleMap[e.srcId]?.ifBlank { untitledLabel } ?: e.srcId
                            val dst = nodeTitleMap[e.dstId]?.ifBlank { untitledLabel } ?: e.dstId
                            edgeFmt.format(src, dst)
                        }
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .semantics(mergeDescendants = true) {
                    contentDescription = a11yDescription
                    liveRegion = LiveRegionMode.Polite
                }
        )
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = translationX,
                    translationY = translationY
                )
                // 单 transformGesture:pan + pinch(rotation 忽略)
                .pointerInput(Unit) {
                    detectTransformGestures(panZoomLock = false) { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(0.5f, 4f)
                        scale = newScale
                        translationX += pan.x
                        translationY += pan.y
                    }
                }
                // tapGesture:hit-test node,半径 + 12dp
                .pointerInput(snapshot.nodes) {
                    detectTapGestures(onTap = { offset ->
                        val hit = pickNode(
                            snapshot = snapshot,
                            coords = coords,
                            tapAt = offset,
                            thresholdPx = tapThresholdPx
                        )
                        // 中心节点不导航(spec §"Tap on the center node is a no-op")
                        if (hit != null && hit != snapshot.centerNodeId) onNodeTap(hit)
                    })
                }
        ) {
            drawGraph(
                snapshot = snapshot,
                coords = coords,
                centerColor = centerColor,
                nodeColor = nodeColor,
                edgeColor = edgeColor,
                centerStroke = centerStroke
            )
        }

        // ---- entity chips 浮层(走 Compose 自身 Text 渲染,canvas 文本质量低)----
        if (snapshot.entityChips.isNotEmpty()) {
            EntityChipOverlay(chips = snapshot.entityChips)
        }
    }
}

/**
 * 简化版 drawGraph:每帧一次性 sweep,50 节点 + ~50 边走 CPU draw < 1ms,tasks §3.2 的
 * `drawWithCache` 升级留在 v2(节点数 > 200 后做 path 缓存才有收益)。
 */
private fun DrawScope.drawGraph(
    snapshot: GraphSnapshot,
    coords: Map<String, NodeCoords>,
    centerColor: Color,
    nodeColor: Color,
    edgeColor: Color,
    centerStroke: Color
) {
    // edges first (under nodes)
    for (edge in snapshot.edges) {
        val a = coords[edge.srcId] ?: continue
        val b = coords[edge.dstId] ?: continue
        val strokeWidth = 1f + edge.weight.coerceIn(0f, 1f) * 2f
        drawLine(
            color = edgeColor,
            start = Offset(a.x, a.y),
            end = Offset(b.x, b.y),
            strokeWidth = strokeWidth,
            pathEffect = if (edge.linkType == LinkType.WIKILINK) {
                null
            } else {
                PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
            }
        )
    }
    // nodes
    for (node in snapshot.nodes) {
        val p = coords[node.noteId] ?: continue
        val radius = nodeRadiusFor(node.score, isCenter = node.hopLevel == 0)
        val color = if (node.hopLevel == 0) centerColor else nodeColor
        drawCircle(
            color = color,
            radius = radius,
            center = Offset(p.x, p.y)
        )
        if (node.hopLevel == 0) {
            drawCircle(
                color = centerStroke,
                radius = radius,
                center = Offset(p.x, p.y),
                style = Stroke(width = 2f)
            )
        }
    }
}

/** 节点半径:12 + sigmoid(score) * 20;中心固定 1.5×(tasks §3.3)。 */
private fun nodeRadiusFor(score: Float, isCenter: Boolean): Float {
    val s = score.coerceIn(-5f, 5f)
    val sig = 1f / (1f + exp(-s))
    val base = 12f + sig * 20f
    return if (isCenter) base * 1.5f else base
}

/** 点击命中:距离 ≤ (radius + 12dp) 才算 tap。 */
private fun pickNode(
    snapshot: GraphSnapshot,
    coords: Map<String, NodeCoords>,
    tapAt: Offset,
    thresholdPx: Float
): String? {
    var best: String? = null
    var bestDist = Float.MAX_VALUE
    for (node in snapshot.nodes) {
        val p = coords[node.noteId] ?: continue
        val r = nodeRadiusFor(node.score, isCenter = node.hopLevel == 0) + thresholdPx
        val d = hypot((p.x - tapAt.x).toDouble(), (p.y - tapAt.y).toDouble()).toFloat()
        if (d <= r && d < bestDist) {
            bestDist = d
            best = node.noteId
        }
    }
    return best
}

// ---- entity chip overlay ----

@Suppress("unused")
private val chipCornerShape = RoundedCornerShape(12.dp)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoxScope.EntityChipOverlay(chips: List<String>) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(8.dp)
            .clip(chipCornerShape)
            .background(containerColor)
            .padding(8.dp)
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            chips.take(8).forEach { chip ->
                AssistChip(
                    onClick = { /* preview only */ },
                    label = {
                        Text(
                            text = chip.ifBlank { stringResource(R.string.note_graph_node_untitled) },
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Adjust,
                            contentDescription = null,
                            modifier = Modifier.size(AssistChipDefaults.IconSize)
                        )
                    }
                )
            }
        }
    }
}
