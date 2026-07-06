package com.yy.writingwithai.feature.quicknote.list

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yy.writingwithai.R
import com.yy.writingwithai.app.ui.theme.LocalSpacing
import com.yy.writingwithai.core.feishu.sync.FeishuRefStatus
import com.yy.writingwithai.core.media.LruBitmapCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ui-redesign-v2 · 笔记行组件:IM 风格列表行 — 零圆角、零外间距、固定 88dp 高度,
 * 左侧 3dp 彩色竖条(tag 色或 primary)，底部 1dp outlineVariant 分隔线。
 *
 * note-list-card-im-style · 2026-07-03 重构:
 * - 卡片上下左右完全贴边:视窗边(无 horizontal padding)+ 卡片相邻(无 vertical arrangement)
 * - **固定 88dp 高度**(非 min),无 tag 也保持 88dp,跟左滑按钮底边零误差对齐
 * - 渲染段:顶部 metadata 行(置顶 + 时间 + 同步状态 chip)+ 标题 1 行 + 标签 chips(FlowRow)
 * - **去掉正文预览行**(用户反馈:列表只显示时间/标题/同步状态/标签,正文不放列表)
 *
 * note-list-thumbnail · 2026-07-03 加缩略图:
 * - `firstImagePath` 非 null 时,在卡片最右侧渲染 72dp × 72dp 方形 ContentScale.Crop 缩略图
 * - 用 `produceState` + `BitmapFactory.decodeFile` 在 IO dispatcher 解码,失败回退 null
 * - 无图时(`null`)完全不出缩略图位,跨卡片节奏一致(无占位色,用户确认)
 * - 缩略图缩进:有图时 Column 拿 weight(1f) 缩窄,右侧 72dp 留给缩略图
 *
 * 渲染内容段 vs 卡片高度 88dp 的对齐策略:
 * - 顶部 metadata 行(14sp) + 1 行标题(titleMedium ≈ 16sp) + 标签 chips(12-16sp)
 * - 中间用 Spacer 自由分配 vertical 留白,确保 3 段居中,空 tag 时垂直居中不空荡
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteRow(
    title: String,
    @Suppress("UNUSED_PARAMETER") content: String,
    tags: List<String>,
    // note-list-card-redesign · 改 String? 为 FeishuRefStatus?:NoteRow 内部用 stringResource
    // 解析文案 + 自己配 chip 颜色。null = 普通笔记(无同步状态)。
    syncStatus: FeishuRefStatus?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPinned: Boolean = false,
    updatedAt: String? = null,
    // note-list-card-actions · 长按回调，携带触摸位置(相对于卡片左上角)，
    // 用于下拉菜单定位。默认空，保持向后兼容。
    onLongClick: (Offset) -> Unit = {},
    // note-list-thumbnail · 笔记首张图片绝对路径(`note_attachments.localPath`),
    // null = 无图,卡片右侧不渲染缩略图。详见文件顶部注释。
    firstImagePath: String? = null
) {
    val spacing = LocalSpacing.current
    // M3 fix: remember(tags) 缓存颜色，避免每次重组重新计算 Color.hsl()
    val isDark = isSystemInDarkTheme()
    // fix-review-r4:tagAccentColor 在 remember{} 内调用，不是 @Composable 上下文，
    // 不能在里面读 MaterialTheme。把 primary 颜色从 Composable 上下文传入。
    val primaryColor = MaterialTheme.colorScheme.primary
    val accentColor = remember(tags, isDark) { tagAccentColor(tags, isDark, primaryColor) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            // note-list-card-im-style · 固定 88dp 高度(非 min),无 tag 时不矮,
            // 左滑 SwipeActionButton 底边跟 Card 实际底零误差对齐
            .height(88.dp)
            // note-list-card-actions · 单击进详情 + 长按弹菜单(携带触摸位置)。
            // 用 detectTapGestures 统一处理 onTap + onLongPress，避免 clickable 与
            // detectTapGestures 手势冲突导致短按 onClick 不触发。
            .pointerInput(onClick, onLongClick) {
                detectTapGestures(
                    onLongPress = { offset -> onLongClick(offset) },
                    onTap = { onClick() }
                )
            },
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = null
    ) {
        // note-list-card-divider-out · Card 内部不再渲染底部分割线,改在 QuickNoteListScreen
        // 外层 Box(承载 NoteRow + swipe 露背景按钮)内用 HorizontalDivider(align BottomCenter)
        // 渲染。原因:Card 传 children 的 maxHeight=Infinity,内部 Box(fillMaxSize).fillMaxHeight
        // 在 Infinity 约束下退化为 wrap content,Divider 永远落在 content 自然末尾(~80dp),
        // 跟 heightIn(min=88dp) 兜底的 Card 实际底部 88dp 差 8dp → 短卡片 Divider 浮在 swipe
        // 按钮底边之上(用户最初反馈的 bug)。外层 Box 有 heightIn 兜底 + 实际高度(等于 Card
        // 实际高度)确定,BoxScope.align(BottomCenter) 把 Divider 锁在 Box 实际底部 1dp,
        // 跟左滑 SwipeActionButton 底边零误差对齐。
        Row(
            modifier = Modifier.fillMaxWidth().fillMaxHeight()
        ) {
            // 左侧彩色竖条
            Spacer(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(accentColor)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    // note-list-card-im-style · 固定 88dp 高度,vertical padding 用
                    // xs(4dp) 留上下极小留白,中间 3 段内容填满
                    .padding(horizontal = spacing.md, vertical = spacing.xs)
            ) {
                // fix-card-layout-shift · metadata 行固定高度(labelSmall 行高 ≈ 16dp +
                // chip 不增加行高),确保有无 syncStatus chip 都不偏移标题位置。
                // 使用 heightIn(min = 20.dp) 保证行高下限,chip 有无不影响后续元素 Y 坐标。
                Row(
                    modifier = Modifier.heightIn(min = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    if (isPinned) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (updatedAt != null) {
                        Text(
                            text = updatedAt,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (syncStatus != null) {
                        SyncStatusChip(status = syncStatus, isDark = isDark)
                    }
                }
                // fix-card-layout-shift · 标题行:固定 20dp 高度(titleMedium 行高),
                // 与 metadata 行间距 4dp(xs),确保有无 chip 标题 Y 坐标不变。
                Spacer(Modifier.height(spacing.xs))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.heightIn(min = 20.dp)
                )
                // fix-card-layout-shift · 标签区:固定 20dp 高度(与 FlowRow 等高),
                // 无论有无标签都占 20dp,保证上方标题行和 metadata 行 Y 坐标固定。
                // 有标签时正常渲染 FlowRow,无标签时占位空白。
                Spacer(Modifier.height(spacing.xs))
                Box(modifier = Modifier.heightIn(min = 20.dp)) {
                    if (tags.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            tags.take(3).forEach { tag ->
                                // M5 fix: SuggestionChip 改为非交互式 Text+背景，避免误导可点击
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .background(
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                            if (tags.size > 3) {
                                Text(
                                    text = "+${tags.size - 3}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .align(Alignment.CenterVertically)
                                        .padding(start = spacing.xs)
                                )
                            }
                        }
                    }
                }
            }
            // note-list-thumbnail · 卡片右侧 72dp × 72dp 方形缩略图。
            // 有图才出现,无图时整个 Box 不渲染 → 跨卡片节奏一致。
            // 用 produceState 异步 decode,加载完成前 bitmap 为 null,
            // Box 保持透明占位(用户确认),图片出现时「淡入」Compose 默认行为无闪烁。
            // M7 fix:先查 LruBitmapCache，命中则跳过 decodeFile；未命中则解码后写入缓存。
            if (firstImagePath != null) {
                val bitmapCache = LruBitmapCache.instance
                val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
                    initialValue = null,
                    key1 = firstImagePath
                ) {
                    value = withContext(Dispatchers.IO) {
                        // 缓存命中：直接转 ImageBitmap
                        bitmapCache.get(firstImagePath)?.asImageBitmap()
                            // 缓存未命中：解码 + 写入缓存
                            ?: runCatching {
                                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                BitmapFactory.decodeFile(firstImagePath, bounds)
                                val opts = BitmapFactory.Options().apply {
                                    inSampleSize = calculateNoteThumbSample(
                                        bounds.outWidth,
                                        bounds.outHeight,
                                        targetPx = 144
                                    )
                                    inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                                }
                                BitmapFactory.decodeFile(firstImagePath, opts)
                            }.getOrNull()?.also { bitmapCache.put(firstImagePath, it) }
                                ?.asImageBitmap()
                    }
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp)
                            .align(Alignment.CenterVertically)
                            .padding(end = spacing.sm)
                    )
                }
            }
        }
    }
}

/**
 * note-list-card-redesign · 同步状态 chip:非交互式浅色底圆角文字,
 * 4 个状态(SYNCED / DIRTY / CONFLICT / REMOTE_DELETED)用 4 种色相区分。
 *
 * 渲染样式参考已有 tag chip 模式(见 [NoteRow] 内 tag Text 块):
 * Text + background(bg, RoundedCornerShape(8.dp)) + padding(horizontal=8.dp, vertical=2.dp)
 *
 * 颜色:Hue 按 status 分配(SYNCED=绿 ~140°,DIRTY=琥珀 ~35°,CONFLICT=红 ~0°,
 * REMOTE_DELETED=灰用 surfaceVariant 即可),饱和度 0.55,亮度跟 isDark 走,
 * 跟现有 [tagAccentColor] 风格一致。
 */
@Composable
private fun SyncStatusChip(status: FeishuRefStatus, isDark: Boolean) {
    val label = when (status) {
        FeishuRefStatus.SYNCED -> stringResource(R.string.quicknote_feishu_status_synced)
        FeishuRefStatus.DIRTY -> stringResource(R.string.quicknote_feishu_status_dirty)
        FeishuRefStatus.CONFLICT -> stringResource(R.string.quicknote_feishu_status_conflict)
        FeishuRefStatus.REMOTE_DELETED -> stringResource(R.string.quicknote_feishu_status_remote_deleted)
        // feishu-import-from-folder:导入部分图片失败的笔记
        FeishuRefStatus.PARTIAL_IMPORT_FAIL ->
            stringResource(R.string.quicknote_list_sync_status_partial_import_fail)
    }
    val (bg, fg) = syncStatusChipColors(status, isDark)
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        modifier = Modifier
            .background(color = bg, shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

/**
 * note-list-card-redesign · 4 个同步状态对应 chip 底色 + 文字色(亮 / 暗模式各一组)。
 *
 * 配色策略:
 * - SYNCED 绿:成功语义
 * - DIRTY 琥珀:待处理语义
 * - CONFLICT 红:错误语义,跟 SwipeActionButton 错误色一致
 * - REMOTE_DELETED 灰:中性消亡语义
 *
 * HSL 选择:沿用 [tagAccentColor] 的 saturation ≈ 0.6,但 lightness 分亮 / 暗两档
 * (亮 0.88 底 + 0.30 字;暗 0.28 底 + 0.85 字),保证 WCAG 对比度 ≥ 4.5。
 */
private fun syncStatusChipColors(status: FeishuRefStatus, isDark: Boolean): Pair<Color, Color> {
    val (bgLightness, fgLightness) = if (isDark) 0.28f to 0.85f else 0.88f to 0.30f
    return when (status) {
        FeishuRefStatus.SYNCED ->
            Color.hsl(140f, 0.55f, bgLightness) to Color.hsl(140f, 0.55f, fgLightness)
        FeishuRefStatus.DIRTY ->
            Color.hsl(35f, 0.60f, bgLightness) to Color.hsl(35f, 0.65f, fgLightness)
        FeishuRefStatus.CONFLICT ->
            Color.hsl(0f, 0.55f, bgLightness) to Color.hsl(0f, 0.60f, fgLightness)
        FeishuRefStatus.REMOTE_DELETED ->
            // 中性灰:低饱和,接近 surfaceVariant
            Color.hsl(220f, 0.08f, bgLightness) to Color.hsl(220f, 0.10f, fgLightness)
        // feishu-import-from-folder:琥珀偏红,警告色,跟 DIRTY(35°) 区分但比 CONFLICT 弱
        FeishuRefStatus.PARTIAL_IMPORT_FAIL ->
            Color.hsl(20f, 0.55f, bgLightness) to Color.hsl(20f, 0.60f, fgLightness)
    }
}

/**
 * 从第一个 tag 名推导竖条颜色:tag 为空则用 primary。
 * 暗色模式用更高 lightness 保证对比度。
 *
 * fix-2026-06-26-review-r3 M10:用 `kotlin.math.abs` 替代 `mod(360f)` 把 hash 映射到
 * `[0, 360)` 区间。原 `Int.mod(Float)` 实现对负数先做 `%`，再用 `let { if (it < 0) ... }`
 * 二次校正，语义上等价但读起来绕;改写为单次无分支映射更直观。同时把 `hashCode` 先
 * 转 `UInt` 再取模，避免 `Int.MIN_VALUE` 在 `% 360` 时被解释成负数后再校正。
 *
 * fix-2026-06-27-review-r4 M11:空 tag 不再用 hardcoded hex，统一走
 * `MaterialTheme.colorScheme.primary`，跟暗色/亮色主题自动适配。
 */
private fun tagAccentColor(tags: List<String>, isDark: Boolean, primaryColor: Color): Color {
    if (tags.isEmpty()) {
        return primaryColor
    }
    val rawHash = tags.first().hashCode()
    // UInt 转换 → 无符号 32-bit → % 360 → [0, 360)
    val hue = (rawHash.toUInt() % 360u).toFloat()
    val lightness = if (isDark) 0.6f else 0.45f
    return Color.hsl(hue, 0.6f, lightness)
}

/**
 * note-list-thumbnail · 缩略图采样率计算(2 的幂,inSampleSize 要求)。
 *
 * 复用 QuickNoteDetailScreen.calculateThumbSample 的同款算法(取最接近但不小于
 * 目标尺寸的 2^n 缩放,大幅降内存占用)。列表侧独立 inline 写一份,避免跨 feature
 * 共享私有函数带来的反向依赖 — 列表 / 详情两屏的缩略图尺寸/策略可能独立演化
 * (如列表升级 96dp,详情仍 80dp),分开维护更易调。
 */
private fun calculateNoteThumbSample(srcWidth: Int, srcHeight: Int, targetPx: Int): Int {
    if (srcWidth <= 0 || srcHeight <= 0) return 1
    var sample = 1
    val halfW = srcWidth / 2
    val halfH = srcHeight / 2
    while (halfW / sample >= targetPx && halfH / sample >= targetPx) {
        sample *= 2
    }
    return sample
}
