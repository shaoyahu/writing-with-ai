package com.yy.writingwithai.feature.quicknote.graph

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yy.writingwithai.R
import com.yy.writingwithai.core.data.db.entity.LinkType
import com.yy.writingwithai.core.note.graph.GraphSnapshot
import com.yy.writingwithai.core.note.graph.NodeCoords
import kotlin.math.atan2
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
    val labelPx = with(density) { 14.sp.toPx() }

    val scheme = MaterialTheme.colorScheme
    val centerColor = scheme.primary.copy(alpha = 0.85f)
    val nodeColor = scheme.primary.copy(alpha = 0.55f)
    // improve-note-graph-readability D2:theme-aware edge color。
    // light scheme 用 onSurfaceVariant + alpha 0.6;dark scheme 用 outlineVariant 不降 alpha。
    val isDark = isSystemInDarkTheme()
    val edgeColor = if (isDark) {
        scheme.outlineVariant
    } else {
        scheme.onSurfaceVariant.copy(alpha = 0.6f)
    }
    val centerStroke = scheme.outline
    val labelColor = scheme.onSurface.toArgb()

    // fix-note-graph-rendering:Box 尺寸捕获,推导 canvasCenter 供 pickNode 用同一参考系。
    var boxSizePx by remember { mutableStateOf(IntSize.Zero) }
    val canvasCenter = remember(boxSizePx) {
        Offset(boxSizePx.width / 2f, boxSizePx.height / 2f)
    }
    Box(
        // fix-note-graph-rendering:把布局坐标系 (中心 0,0) 平移到 Box 几何中心。
        // Box 尺寸用 onSizeChanged 捕获,推导 canvasCenter 供 pickNode 用同一参考系。
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { boxSizePx = it }
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
                            thresholdPx = tapThresholdPx,
                            canvasCenter = canvasCenter
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
                centerStroke = centerStroke,
                labelColor = labelColor,
                labelPx = labelPx
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
 *
 * improve-note-graph-readability D1:每个节点预先算 `NodeLayout`(渲染位置 + 标签 box + 方向),
 * 避免每帧重复计算 + 标签互相压字。
 */
private fun DrawScope.drawGraph(
    snapshot: GraphSnapshot,
    coords: Map<String, NodeCoords>,
    centerColor: Color,
    nodeColor: Color,
    edgeColor: Color,
    centerStroke: Color,
    labelColor: Int,
    labelPx: Float
) {
    // fix-note-graph-rendering:把布局坐标系 (中心 0,0) 平移到 canvas 几何中心,
    // 这样中心节点落在画布中央,1-hop 围绕外圈,不被截掉。
    val canvasCenter = Offset(size.width / 2f, size.height / 2f)
    val textPaint = android.graphics.Paint().apply {
        color = labelColor
        textSize = labelPx
        isAntiAlias = true
    }
    val nodeCount = snapshot.nodes.size
    // TODO(perf):nodeCount > 100 时考虑 spatial index,目前 O(n²) 渲染开销 < 1ms / 帧。
    val layouts = computeNodeLayouts(snapshot, coords, canvasCenter, textPaint)
    // edges first (under nodes)
    for (edge in snapshot.edges) {
        val a = layouts[edge.srcId] ?: continue
        val b = layouts[edge.dstId] ?: continue
        // improve-note-graph-readability D2:stroke 粗细 + 颜色升级。
        val strokeWidth = 2.5f + edge.weight.coerceIn(0f, 1f) * 3f
        drawLine(
            color = edgeColor,
            start = a.center,
            end = b.center,
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
        val layout = layouts[node.noteId] ?: continue
        val color = if (node.hopLevel == 0) centerColor else nodeColor
        drawCircle(
            color = color,
            radius = layout.radius,
            center = layout.center
        )
        if (node.hopLevel == 0) {
            drawCircle(
                color = centerStroke,
                radius = layout.radius,
                center = layout.center,
                style = Stroke(width = 2f)
            )
        }
        // fix-note-graph-rendering P0:节点标题用 nativeCanvas drawText,空白 title 跳过。
        // improve-note-graph-readability D1:标签位置从 layout.labelBox 取(4 方向避让后),
        // 替代 fix-note-graph-rendering 的固定右偏移。title 截到 12 字符 + `…`。
        if (node.title.isNotBlank() && layout.labelBox != null) {
            val label = if (node.title.length > 12) node.title.take(12) + "…" else node.title
            val baselineX = layout.labelBox.left
            // nativeCanvas.drawText baseline 在文字底部;这里把 box top 当文本视觉顶端,
            // + textSize 让基线落在 box 底边附近,视觉上和 box 对齐。
            val baselineY = layout.labelBox.top + textPaint.textSize
            drawContext.canvas.nativeCanvas.drawText(
                label,
                baselineX,
                baselineY,
                textPaint
            )
        }
    }
}

/**
 * 每个节点的渲染信息(位置 / 半径 / 标签 box)。NodeLayout 在 drawGraph 入口一次性算好,
 * edges / nodes 两轮循环共用。improve-note-graph-readability D1 引入。
 */
private data class NodeLayout(
    val center: Offset,
    val radius: Float,
    val labelBox: Rect?
)

/** 标签方向(对应 labelBoxFor 的四个偏移方向 + NONE)。 */
private const val LABEL_NONE = 0
private const val LABEL_RIGHT = 1
private const val LABEL_LEFT = 2
private const val LABEL_ABOVE = 3
private const val LABEL_BELOW = 4
private val LABEL_PRIORITY = intArrayOf(LABEL_RIGHT, LABEL_BELOW, LABEL_LEFT, LABEL_ABOVE)

/**
 * improve-note-graph-readability D1:
 * - 计算每个节点的 center / radius / 标签 box(4 方向避让后)。
 * - 邻居 = 距离 < `2 * (r_self + r_other + labelW)` 的其它节点。
 * - 四方向(right / left / above / below)枚举,取首个不与任何邻居节点 box + 邻居 label box 相交的方向;
 *   全重叠则选"与最近邻居夹角最大"的方向(优先级表 LABEL_PRIORITY 兜底 → right 优先打破平衡)。
 *
 * 为避免每节点 O(n²) 重复 measureText,先把每个节点的标签宽度缓存到 Pre.labelWidth。
 */
private fun DrawScope.computeNodeLayouts(
    snapshot: GraphSnapshot,
    coords: Map<String, NodeCoords>,
    canvasCenter: Offset,
    textPaint: Paint
): Map<String, NodeLayout> {
    // first pass:每个节点的 center / radius / label 文本 + 宽度
    val labelPx = textPaint.textSize
    val pres = ArrayList<NodePre>(snapshot.nodes.size)
    for (node in snapshot.nodes) {
        val p = coords[node.noteId] ?: continue
        val radius = nodeRadiusFor(node.score, isCenter = node.hopLevel == 0)
        val center = canvasCenter + Offset(p.x, p.y)
        val labelText: String? = if (node.title.isBlank()) {
            null
        } else if (node.title.length > 12) {
            node.title.take(12) + "…"
        } else {
            node.title
        }
        val labelWidth = labelText?.let { textPaint.measureText(it) } ?: 0f
        pres.add(NodePre(node.noteId, center, radius, labelText, labelWidth))
    }

    val result = LinkedHashMap<String, NodeLayout>(pres.size)
    for (self in pres) {
        if (self.labelText == null) {
            result[self.id] = NodeLayout(self.center, self.radius, null)
            continue
        }
        // 邻居:距离 < 2*(r_self + r_other + labelW_self)
        val collisionRange = 2f * (self.radius + self.labelWidth)
        val others = pres.filter {
            it !== self && (it.center - self.center).getDistance() < collisionRange + it.radius + it.labelWidth
        }
        var chosen = LABEL_NONE
        for (dir in LABEL_PRIORITY) {
            val candidate = labelBoxFor(self.center, self.radius, self.labelWidth, labelPx, dir) ?: continue
            val overlap = others.any { other ->
                val otherNodeBox = nodeBBox(other.center, other.radius)
                if (candidate.overlaps(otherNodeBox)) return@any true
                if (other.labelText == null) return@any false
                // 邻居 label 还没决定方向(本算法是 sweep,非迭代),
                // 任何可能的方向都可能与本节点冲突 → 任一方向 OR 重叠就视为占位。
                // 复杂度 O(4n) = O(n),仍 ≤ 1ms / 帧。
                LABEL_PRIORITY.any { otherDir ->
                    val otherLabelBox = labelBoxFor(
                        other.center,
                        other.radius,
                        other.labelWidth,
                        labelPx,
                        otherDir
                    )
                    otherLabelBox != null && candidate.overlaps(otherLabelBox)
                }
            }
            if (!overlap) {
                chosen = dir
                break
            }
        }
        if (chosen == LABEL_NONE) {
            // fallback:与最近邻居夹角最大 → 取最近邻居反方向 + 优先级表中第一个不背离的方向。
            // 实现用最近邻居的位置推导"远离它"的方向。
            chosen = pickFallbackDirection(self, pres)
        }
        result[self.id] = NodeLayout(
            self.center,
            self.radius,
            labelBoxFor(self.center, self.radius, self.labelWidth, labelPx, chosen)
        )
    }
    return result
}

/** collision 算法内部用的轻量 pre-computed 节点信息。 */
private data class NodePre(
    val id: String,
    val center: Offset,
    val radius: Float,
    val labelText: String?,
    val labelWidth: Float
)

/** 节点圆外切 box。 */
private fun nodeBBox(center: Offset, radius: Float): Rect = Rect(
    left = center.x - radius,
    top = center.y - radius,
    right = center.x + radius,
    bottom = center.y + radius
)

/** 在指定方向放 label 后的 box(返回 null 表示方向无效)。 */
private fun labelBoxFor(center: Offset, radius: Float, labelWidth: Float, labelPx: Float, dir: Int): Rect? {
    if (dir == LABEL_NONE) return null
    val labelHeight = labelPx
    return when (dir) {
        LABEL_RIGHT -> Rect(
            left = center.x + radius + 4f,
            top = center.y - labelHeight / 2f,
            right = center.x + radius + 4f + labelWidth,
            bottom = center.y + labelHeight / 2f
        )
        LABEL_LEFT -> Rect(
            left = center.x - radius - 4f - labelWidth,
            top = center.y - labelHeight / 2f,
            right = center.x - radius - 4f,
            bottom = center.y + labelHeight / 2f
        )
        LABEL_ABOVE -> Rect(
            left = center.x - labelWidth / 2f,
            top = center.y - radius - 4f - labelHeight,
            right = center.x + labelWidth / 2f,
            bottom = center.y - radius - 4f
        )
        LABEL_BELOW -> Rect(
            left = center.x - labelWidth / 2f,
            top = center.y + radius + 4f,
            right = center.x + labelWidth / 2f,
            bottom = center.y + radius + 4f + labelHeight
        )
        else -> null
    }
}

/** 取最近邻居,选与之角度最大的方向(右优先)。 */
private fun pickFallbackDirection(self: NodePre, all: List<NodePre>): Int {
    var nearest: NodePre? = null
    var nearestDist = Float.MAX_VALUE
    for (other in all) {
        if (other === self) continue
        val d = (other.center - self.center).getDistance()
        if (d < nearestDist) {
            nearestDist = d
            nearest = other
        }
    }
    val neighbor = nearest ?: return LABEL_RIGHT
    val dx = self.center.x - neighbor.center.x
    val dy = self.center.y - neighbor.center.y
    val angle = atan2(dy.toDouble(), dx.toDouble())
    // 选方向单位向量与 angle 最接近的方向(=远离邻居的方向)
    val dirs = listOf(
        LABEL_RIGHT to 0.0,
        LABEL_BELOW to Math.PI / 2.0,
        LABEL_LEFT to Math.PI,
        LABEL_ABOVE to -Math.PI / 2.0
    )
    return dirs.minByOrNull { (_, dirAngle) -> angularDiff(angle, dirAngle) }?.first ?: LABEL_RIGHT
}

private fun angularDiff(a: Double, b: Double): Double {
    var d = a - b
    while (d > Math.PI) d -= 2 * Math.PI
    while (d < -Math.PI) d += 2 * Math.PI
    return Math.abs(d)
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
    thresholdPx: Float,
    canvasCenter: Offset
): String? {
    var best: String? = null
    var bestDist = Float.MAX_VALUE
    for (node in snapshot.nodes) {
        val p = coords[node.noteId] ?: continue
        val r = nodeRadiusFor(node.score, isCenter = node.hopLevel == 0) + thresholdPx
        // fix-note-graph-rendering:tapAt 用 Box 像素坐标,需要减 canvasCenter
        // 才能与 drawGraph 用的 canvasCenter+Offset(...) 同坐标系。
        val dx = canvasCenter.x + p.x - tapAt.x
        val dy = canvasCenter.y + p.y - tapAt.y
        val d = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (d <= r && d < bestDist) {
            bestDist = d
            best = node.noteId
        }
    }
    return best
}

// ---- entity chip overlay ----

private val chipCornerShape = RoundedCornerShape(12.dp)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoxScope.EntityChipOverlay(chips: List<String>) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    Box(
        // fix-note-graph-rendering:从 TopCenter 改 BottomCenter,避免遮挡中心节点。
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(start = 8.dp, end = 8.dp, bottom = 16.dp)
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
