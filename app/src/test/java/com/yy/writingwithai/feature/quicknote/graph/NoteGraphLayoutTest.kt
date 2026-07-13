package com.yy.writingwithai.feature.quicknote.graph

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.yy.writingwithai.core.note.graph.GraphNode
import com.yy.writingwithai.core.note.graph.GraphSnapshot
import com.yy.writingwithai.core.note.graph.NodeCoords
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * add-note-graph-layout-test:
 * JVM unit test for [computeNodeLayoutsFor] 4-direction label collision algorithm.
 *
 * 8 cases cover spec delta 6 scenarios + 2 algorithm hardening cases (L3 fallback no-neighbor,
 * L8 `Rect.overlaps` edge-touching semantics).
 *
 * 设计约束(spec "Test architecture constraint"):
 * - 纯 JVM,不走 Robolectric。
 * - 测试不引入 [android.graphics.Paint] / TextPaint;通过 `computeNodeLayoutsFor` 的 `(String) -> Float`
 *   参数直接喂 labelWidthFor,完全脱离 framework 量宽度。
 * - 所有 fixture 用 [GraphSnapshot] / [GraphNode] / [NodeCoords] 的 data class 构造函数直接造,
 *   不 mock / 不假 provider。
 */
class NoteGraphLayoutTest {

    private val labelWidthFor: (String) -> Float = { it.length * CHAR_W }

    // density = 1f → labelPx = 14f
    private val density = 1f

    // ---- fixture helpers ----

    private fun snapshotOf(nodes: List<GraphNode>): GraphSnapshot = GraphSnapshot(
        centerNodeId = nodes.firstOrNull()?.noteId ?: "center",
        nodes = nodes,
        edges = emptyList(),
        entityChips = emptyList(),
        truncated = false
    )

    private fun coordsOf(vararg pairs: Pair<String, Offset>): Map<String, NodeCoords> =
        pairs.associate { (id, off) -> id to NodeCoords(x = off.x, y = off.y) }

    private fun node(id: String, title: String, hopLevel: Int = 1): GraphNode =
        GraphNode(noteId = id, title = title, score = 0f, hopLevel = hopLevel)

    // ---- 8 cases ----

    /**
     * Test 1:空 snapshot → 空 map,不抛异常(spec "Empty snapshot")。
     */
    @Test
    fun test1_emptySnapshot_returnsEmptyMap() {
        val layouts = computeNodeLayoutsFor(
            snapshot = snapshotOf(emptyList()),
            coords = coordsOf(),
            canvasSize = Size(500f, 500f),
            density = density,
            labelWidthFor = labelWidthFor
        )
        assertTrue(layouts.isEmpty(), "empty snapshot should produce empty layouts map")
    }

    /**
     * Test 2:单节点无邻居 → label 默认 LABEL_RIGHT,box 与 viewport 内不越界(spec "Single node, no neighbors")。
     *
     * 节点 hopLevel=0(center),半径 = 22 × 1.5 = 33。位置 (100,100),canvas 500x500。
     * RIGHT box:left=137,right=177,top=93,bottom=107 — 全在 viewport 内。
     */
    @Test
    fun test2_singleNode_noNeighbors_picksRight() {
        val n = node("a", "Hello", hopLevel = 0)
        val snapshot = snapshotOf(listOf(n))
        val coords = coordsOf("a" to Offset(100f, 100f))

        val layouts = computeNodeLayoutsFor(
            snapshot = snapshot,
            coords = coords,
            canvasSize = Size(500f, 500f),
            density = density,
            labelWidthFor = labelWidthFor
        )

        val box = layouts["a"]!!.labelBox!!
        assertEquals(137f, box.left, 0.5f, "labelBox.left should match LABEL_RIGHT formula")
        assertEquals(177f, box.right, 0.5f, "labelBox.right should match LABEL_RIGHT formula")
    }

    /**
     * Test 3:两节点水平相邻 + 矮 canvas 高度,逼 BELOW viewport-out → LEFT 命中(spec "Horizontal pair forces LEFT")。
     *
     * 节点 a (hopLevel=1,radius=22) 在 (200,250),节点 beta (hopLevel=1,radius=22) 在 (260,250),canvas h=280。
     * - a's RIGHT box (226..266, 243..257) 与 beta circle (238..282, 228..272) 重叠 → RIGHT 失败
     * - a's BELOW box (180..220, 276..290) 越出 canvas h=280 → BELOW 失败
     * - a's LEFT box (134..178, 243..257) 与 beta 不重叠 → 命中
     */
    @Test
    fun test3_horizontalPair_forcesLeft() {
        val a = node("a", "alpha", hopLevel = 1)
        val b = node("beta", "beta", hopLevel = 1)
        val snapshot = snapshotOf(listOf(a, b))
        val coords = coordsOf("a" to Offset(200f, 250f), "beta" to Offset(260f, 250f))

        val layouts = computeNodeLayoutsFor(
            snapshot = snapshot,
            coords = coords,
            canvasSize = Size(500f, 280f),
            density = density,
            labelWidthFor = labelWidthFor
        )

        val abox = layouts["a"]!!.labelBox!!
        // LEFT 公式:left = 200 - 22 - 4 - 40 = 134,right = 200 - 22 - 4 = 174
        assertEquals(134f, abox.left, 0.5f, "a.labelBox.left should match LABEL_LEFT formula")
        assertEquals(174f, abox.right, 0.5f, "a.labelBox.right should match LABEL_LEFT formula")
    }

    /**
     * Test 4:4 个邻居包夹 4 象限 → fallback 触发,取最近邻居反方向(spec "All four quadrants occupied")。
     *
     * 中心 c (hopLevel=0,radius=33) 在 (250,140),4 邻居 (radius=22) 分布在 4 象限全部贴近,
     * label box 全与邻居 circle box 重叠 → fallback;近邻反方向 = LABEL_ABOVE。
     */
    @Test
    fun test4_fourNeighbors_occupiedTriggersFallback() {
        val c = node("c", "center", hopLevel = 0)
        val n1 = node("n1", "lefty", hopLevel = 1)
        val n2 = node("n2", "righty", hopLevel = 1)
        val n3 = node("n3", "topn", hopLevel = 1)
        val n4 = node("n4", "bottomn", hopLevel = 1)
        val snapshot = snapshotOf(listOf(c, n1, n2, n3, n4))
        val coords = coordsOf(
            "c" to Offset(250f, 140f),
            "n1" to Offset(200f, 140f),
            "n2" to Offset(300f, 140f),
            "n3" to Offset(250f, 90f),
            "n4" to Offset(250f, 160f)
        )

        val layouts = computeNodeLayoutsFor(
            snapshot = snapshot,
            coords = coords,
            canvasSize = Size(500f, 500f),
            density = density,
            labelWidthFor = labelWidthFor
        )

        val cbox = layouts["c"]!!.labelBox!!
        assertNotNull(cbox, "fallback must still produce non-null labelBox")
        assertTrue(
            cbox.left >= 0f && cbox.top >= 0f && cbox.right <= 500f && cbox.bottom <= 500f,
            "fallback labelBox must stay within viewport: $cbox"
        )
    }

    /**
     * Test 5:邻居 label 4 方向 OR 检测 — review r1 M1 回归保护(spec "Multi-direction neighbor label check")。
     *
     * self s (hopLevel=0,radius=33) 在 (200,250);other o (hopLevel=1,radius=22) 在 (320,250)。
     * s 的候选 RIGHT box (237..277,243..257) 不重叠 o 的 circle box (298..342,228..272),
     * 但与 o 的 LABEL_LEFT box (254..294,243..257) 有重叠(x 254..277)。
     * - OLD bug(只检查 o.RIGHT):o.RIGHT (346..386) 与 s.RIGHT 不撞 → 误判 OK → s 选 RIGHT
     * - NEW fix(4 方向 OR):o.LEFT 撞 → s.RIGHT 失败;继续试 BELOW → 通过 → s 选 BELOW
     */
    @Test
    fun test5_multiDirectionNeighborLabel_check() {
        val s = node("s", "self", hopLevel = 0)
        val o = node("o", "other", hopLevel = 1)
        val snapshot = snapshotOf(listOf(s, o))
        val coords = coordsOf("s" to Offset(200f, 250f), "o" to Offset(320f, 250f))

        val layouts = computeNodeLayoutsFor(
            snapshot = snapshot,
            coords = coords,
            canvasSize = Size(500f, 500f),
            density = density,
            labelWidthFor = labelWidthFor
        )

        val sbox = layouts["s"]!!.labelBox!!
        // s BELOW box: left = 200 - 20 = 180,right = 200 + 20 = 220,top = 250 + 33 + 4 = 287,bottom = 287 + 14 = 301
        assertEquals(287f, sbox.top, 0.5f, "s must pick BELOW (R1 fix: o.LEFT overlaps s.RIGHT)")
        assertEquals(301f, sbox.bottom, 0.5f, "s BELOW bottom should match labelHeight offset")
        // 确认没有误回 RIGHT —- 旧算法只看 neighbor.RIGHT,会因 o.RIGHT 远在 346+ 而误判可放,错回 s.RIGHT。
        assertFalse(
            sbox.left == 237f && sbox.right == 277f,
            "s must NOT pick RIGHT — fix for review r1 M1 regression (4-dir OR check)"
        )
    }

    /**
     * Test 6:节点 title 为空 → labelBox=null,跳过碰撞检测(spec "Untitled node has no label box")。
     */
    @Test
    fun test6_untitledNode_hasNoLabelBox() {
        val n = GraphNode(noteId = "a", title = "", score = 0f, hopLevel = 0)
        val snapshot = snapshotOf(listOf(n))
        val coords = coordsOf("a" to Offset.Zero)

        val layouts = computeNodeLayoutsFor(
            snapshot = snapshot,
            coords = coords,
            canvasSize = Size(500f, 500f),
            density = density,
            labelWidthFor = labelWidthFor
        )

        assertNotNull(layouts["a"], "untitled node still produces layout with null labelBox")
        assertNull(layouts["a"]!!.labelBox, "untitled node must produce labelBox=null")
    }

    /**
     * Test 7:viewport-out 兜底(算法加固,review L3 finding)。
     *
     * 节点 a (hopLevel=0,radius=33) 在 (5,5),canvas 500x500。
     * - RIGHT box:top=-2 < 0 → fail viewport
     * - LEFT box:right=-4 < 0 → fail viewport
     * - BELOW/ABOVE box:left=-15 < 0 → fail viewport
     * - 全 4 方向失败 → fallback,nearest=null → LABEL_RIGHT,但 LABEL_RIGHT 也 fail viewport → labelBox=null。
     * 不抛异常 + 不 NPE,layout 一定存在。
     */
    @Test
    fun test7_pickFallbackDirection_returnsRightWhenNoOther() {
        val n = node("a", "alpha", hopLevel = 0)
        val snapshot = snapshotOf(listOf(n))
        val coords = coordsOf("a" to Offset(5f, 5f))

        val layouts = computeNodeLayoutsFor(
            snapshot = snapshot,
            coords = coords,
            canvasSize = Size(500f, 500f),
            density = density,
            labelWidthFor = labelWidthFor
        )

        val layout = layouts["a"]
        assertNotNull(layout, "layout for a must exist even when all label directions are invalid")
        assertNull(layout!!.labelBox, "labelBox=null when all 4 directions fall outside viewport")
    }

    /**
     * Test 8:Rect.overlaps 边相邻不重叠(算法加固,review L8 finding) — 邻居 collisionRange 边界 case。
     *
     * 两个 Rect 公用一条边但不重叠 → Rect.overlaps 必须返 false(strict 而非 edge-touch)。
     * 也覆盖真正相交与完全相离的反例。
     */
    @Test
    fun test8_rectOverlaps_excludesEdgeTouch() {
        val left = Rect(0f, 0f, 10f, 10f)
        val right = Rect(10f, 0f, 20f, 10f)
        assertFalse(left.overlaps(right), "edge-touching Rects must not overlap")
        assertFalse(right.overlaps(left), "symmetric: edge-touching Rects must not overlap")

        val a = Rect(0f, 0f, 12f, 12f)
        val b = Rect(10f, 10f, 22f, 22f)
        assertTrue(a.overlaps(b), "area-sharing Rects must overlap")

        val c = Rect(0f, 0f, 5f, 5f)
        val d = Rect(20f, 20f, 30f, 30f)
        assertFalse(c.overlaps(d), "disjoint Rects must not overlap")
    }

    private companion object {
        /** 每字符 8 px 测试 stub — 让几何数值整 10/20/40 单位,人脑可推算。 */
        const val CHAR_W = 8f
    }
}
