package com.yy.writingwithai.feature.quicknote.detail

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.em
import com.yy.writingwithai.R
import com.yy.writingwithai.feature.quicknote.model.EntityHighlight

/**
 * fix M72 (full-review):从 QuickNoteDetailScreen.kt(1377 行)拆出 — review 把
 * "单文件 1245 行 mega-composable" 列为 maintainability MEDIUM。3 个无 side-effect
 * 的纯函数和 1 个常量本就是 helper 性质,与主屏 UI 拓扑无关,集中到本文件后
 * detail screen 主文件下行数从 1377 → ~1250,降到 1500 行 guideline 内。
 *
 * helper 与主屏在同包(`feature.quicknote.detail`),都标 `internal`,主屏通过
 * 包内 import 调用,无新公开 API。
 */

/**
 * ux-2026-06-28 #8:缩略图 inSampleSize 计算。80dp ≈ 160px(2x density),
 * `reqPx` 上下浮动，长边 ≤ reqPx 时 sampleSize=1。
 */
internal fun calculateThumbSample(srcWidth: Int, srcHeight: Int, reqPx: Int): Int {
    if (srcWidth <= 0 || srcHeight <= 0) return 1
    var sample = 1
    val halfW = srcWidth / 2
    val halfH = srcHeight / 2
    while (halfW / sample >= reqPx && halfH / sample >= reqPx) {
        sample *= 2
    }
    return sample
}

/** M3:把 lastAiOp("expand"/"polish"/"organize") 映射到 R.string.aiwriting_action_* 中文资源。 */
internal fun opKeyToRes(opKey: String): Int = when (opKey) {
    "expand" -> R.string.aiwriting_action_expand
    "polish" -> R.string.aiwriting_action_polish
    "organize" -> R.string.aiwriting_action_organize
    "summarize" -> R.string.aiwriting_action_summarize
    "translate" -> R.string.aiwriting_action_translate
    else -> R.string.aiwriting_action_expand
}

/**
 * entity-management-and-ai-decompose §3:构建带实体高亮的 AnnotatedString。
 *
 * - 蓝色字体:`SpanStyle(color = primaryColor)`,不再用 Underline。
 * - 每个实体文本末尾紧贴 ✦ 字符(右上角超小号 superscript,视觉像星标)。
 * - 重叠实体按 span 长度降序处理(最长优先),短 span 的 annotation 会被长 span 覆盖。
 *
 * 注意:addStyle / addStringAnnotation 的 start/end 必须是 AnnotatedString 实际索引,
 * 所以每段 append 后用 `length` 拿到新坐标,不再混用 content 原始索引。
 */
internal fun buildEntityAnnotatedString(
    content: String,
    highlights: List<EntityHighlight>,
    primaryColor: Color
): AnnotatedString = buildAnnotatedString {
    if (content.isEmpty()) return@buildAnnotatedString

    val sorted = highlights.sortedByDescending { it.contentEnd - it.contentStart }

    // 1) 计算 normalized entity ranges(夹紧到 content 范围内,过滤空区间)
    val entityRanges: List<Pair<IntRange, EntityHighlight>> = sorted
        .map { h ->
            val s = h.contentStart.coerceIn(0, content.length)
            val e = h.contentEnd.coerceIn(s, content.length)
            if (s >= e) null else (s until e) to h
        }
        .filterNotNull()

    // 2) 按原始 content 顺序生成"实体段 / 普通段"事件序列
    // entity-overlap-coverage:用"最长覆盖 entity"替代"严格边界匹配"。
    // 原 line 87 要求 `r.first == segStart && r.last + 1 == segEnd`,嵌套场景(如
    // "有效上下文覆盖" + "初筛阶段" 重叠)下内层段没 owner,被当普通段,click 不弹 sheet。
    // 新规则:对每个 breakpoint 段找"完全覆盖该段的最长 entity";没有则普通段。
    val events = buildList<Pair<IntRange, EntityHighlight?>> {
        val points = sortedSetOf(0, content.length)
        entityRanges.forEach { (r, _) ->
            points.add(r.first)
            points.add(r.last + 1)
        }
        val sortedPoints = points.toList()
        // 按 span 长度降序:取覆盖段时选最长的那个(优先显示大段)
        val sortedByLength = entityRanges.sortedByDescending { it.first.last - it.first.first }
        for (i in 0 until sortedPoints.size - 1) {
            val segStart = sortedPoints[i]
            val segEnd = sortedPoints[i + 1]
            if (segStart >= segEnd) continue
            val covering = sortedByLength.firstOrNull { (r, _) ->
                r.first <= segStart && r.last + 1 >= segEnd
            }
            add(
                if (covering != null) {
                    segStart until segEnd to covering.second
                } else {
                    segStart until segEnd to null
                }
            )
        }
    }

    // 3) 按顺序 append,同时用 length 跟踪 AnnotatedString 实际索引
    val starInserted = mutableSetOf<String>()
    for ((range, hl) in events) {
        if (hl == null) {
            append(content.substring(range.first, range.last + 1))
        } else {
            val s = range.first
            val e = range.last + 1
            val textStart = length
            append(content.substring(s, e))
            val textEnd = length
            addStyle(
                style = SpanStyle(color = primaryColor),
                start = textStart,
                end = textEnd
            )
            // 唯一实体(按 key)追加 ✦ + annotation
            if (starInserted.add(hl.entityKey)) {
                addStringAnnotation(
                    tag = "entity",
                    annotation = hl.entityKey,
                    start = textStart,
                    end = textEnd
                )
                val starStart = length
                append(EntityCrossStarChar)
                val starEnd = length
                addStyle(
                    style = SpanStyle(
                        color = primaryColor,
                        fontSize = 0.55.em,
                        baselineShift = BaselineShift.Superscript
                    ),
                    start = starStart,
                    end = starEnd
                )
            }
        }
    }
}

internal const val EntityCrossStarChar: String = "✦"
