package com.yy.writingwithai.feature.aiwriting.usage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yy.writingwithai.R
import com.yy.writingwithai.core.data.db.DailyUsageBucket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * ai-usage-statistics §4:Compose Canvas 条形图,**不**用第三方图表库。
 *
 * 设计(token):
 * - 高度固定 160.dp;
 * - 柱子颜色 = `MaterialTheme.colorScheme.primary`,深色模式自动适配;
 * - 柱数 = buckets.size;无 token (sumTotal=0) 日子画 `drawRect(height=0)` no-op,不上刻度;
 * - Y 轴最大数字 label 用 `nativeCanvas.drawText` 画在右上角;
 * - barGap = 4.dp(柱子之间留白);
 * - 顶部 padding 留给 Y 轴 label,底部 padding 给 X 轴视觉空气。
 *
 * 验收:tokens 为 empty 时 Canvas 不画;为单 token 时一根柱顶到顶。
 */
@Composable
fun UsageBarChart(buckets: List<DailyUsageBucket>, modifier: Modifier = Modifier) {
    val barColor: Color = MaterialTheme.colorScheme.primary
    val labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
    val density = LocalDensity.current

    // fix-review-r1 F7:a11y H3 — chart 之前只挂 "AI usage bar chart" 静态文案,TalkBack
    // 用户听不到每根柱代表哪天、用了多少 token。改为 Box 包一层,挂整体 summary +
    // 每根柱独立的 contentDescription(用 bucket_fmt)。TalkBack 滑过图表会逐根读。
    val chartSummary = stringResource(R.string.aiwriting_usage_chart_a11y)
    val bucketFmt = stringResource(R.string.aiwriting_usage_chart_bucket_fmt)
    val dateFmt = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
    val bucketDescriptions = remember(buckets, dateFmt) {
        buckets.map { b ->
            val date = dateFmt.format(Date(b.dayBucket * 86_400_000L))
            bucketFmt.format(date, b.sumTotal)
        }
    }

    Box(
        modifier = modifier.semantics {
            contentDescription = buildString {
                append(chartSummary)
                append(". ")
                append(bucketDescriptions.joinToString(separator = "; "))
            }
        }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val barGapPx = with(density) { 4.dp.toPx() }
            val topPaddingPx = with(density) { 20.dp.toPx() }
            val bottomPaddingPx = with(density) { 8.dp.toPx() }

            val w = size.width
            val h = size.height
            val chartH = (h - topPaddingPx - bottomPaddingPx).coerceAtLeast(0f)
            val chartW = w.coerceAtLeast(0f)

            val n = buckets.size
            if (n == 0 || chartW <= 0f || chartH <= 0f) return@Canvas

            val barWidth = ((chartW - barGapPx * (n + 1)) / n).coerceAtLeast(0f)
            val maxTotal = buckets.maxOf { it.sumTotal }.coerceAtLeast(1)

            // Y 轴最大数字 label(右上角)
            val labelPaint = android.graphics.Paint().apply {
                color = labelColor.toArgb()
                textSize = with(density) { 12.sp.toPx() }
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.RIGHT
            }
            drawContext.canvas.nativeCanvas.drawText(
                maxTotal.toString(),
                w,
                topPaddingPx - with(density) { 4.dp.toPx() },
                labelPaint
            )

            buckets.forEachIndexed { i, b ->
                val left = barGapPx + i * (barWidth + barGapPx)
                val ratio = b.sumTotal.toFloat() / maxTotal
                val barH = chartH * ratio
                val top = topPaddingPx + (chartH - barH)
                // 0 token 日子:不画(避免满图 0 高柱误导,spec §"empty state" 句)
                if (b.sumTotal > 0 && barWidth > 0f && barH > 0f) {
                    drawRect(
                        color = barColor,
                        topLeft = androidx.compose.ui.geometry.Offset(left, top),
                        size = androidx.compose.ui.geometry.Size(barWidth, barH)
                    )
                } else if (barWidth > 0f) {
                    // 占位细条:暗主题下还是能看到 1px 灰线提示"这天有数据 0 token"
                    drawRect(
                        color = barColor.copy(alpha = 0.18f),
                        topLeft = androidx.compose.ui.geometry.Offset(
                            left,
                            topPaddingPx + chartH - with(density) { 2.dp.toPx() }
                        ),
                        size = androidx.compose.ui.geometry.Size(barWidth, with(density) { 2.dp.toPx() })
                    )
                }
            }
        }
    }
}
