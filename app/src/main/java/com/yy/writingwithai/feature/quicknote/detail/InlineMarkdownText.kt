package com.yy.writingwithai.feature.quicknote.detail

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yy.writingwithai.R
import com.yy.writingwithai.core.data.db.dao.NoteAttachmentDao
import com.yy.writingwithai.core.media.AttachmentMarkdownParser
import com.yy.writingwithai.core.media.LruBitmapCache
import com.yy.writingwithai.core.media.MarkdownSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * attachment-inline-render §3 · 笔记正文 Markdown 内联渲染。
 *
 * 策略:
 * - `remember(content) { AttachmentMarkdownParser.parse(content) }` —— content
 *   变才重 parse,避免每次 recomposition 跑正则。
 * - `Text` 段直接 `Text(...)`,样式沿用 `MaterialTheme.typography.bodyLarge`。
 * - `AttachmentImage` 段:
 *   1. `attachmentDao.observeById(attachmentId).first()` 拿 `localPath`(IO 不必,
 *      Room Flow 本身就在 IO dispatcher);
 *   2. `produceState<Bitmap?>` 在 `Dispatchers.IO` 上用 `BitmapFactory.decodeFile`
 *      + `calculateThumbSample(256)` + `inPreferredConfig = RGB_565` 解码;
 *   3. 优先命中 `LruBitmapCache.instance.get(localPath)`,miss decode 后 `put`。
 *   4. 渲染:成功 → `Image(asImageBitmap, aspectRatio, clickable)`;失败/不存在 →
 *      96dp 灰色占位 + 「图片加载失败」小字。
 *
 * 不耦合 noteId:依赖 §4 的 `observeById` 反查,signature 内聚。
 */
@Composable
fun InlineMarkdownText(
    content: String,
    attachmentDao: NoteAttachmentDao,
    onAttachmentClick: (attachmentId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val segments = remember(content) {
        AttachmentMarkdownParser.parse(content)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        segments.forEach { segment ->
            when (segment) {
                is MarkdownSegment.Text -> {
                    Text(
                        text = segment.raw,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }

                is MarkdownSegment.AttachmentImage -> {
                    Spacer(Modifier.height(4.dp))
                    InlineAttachmentImage(
                        attachmentId = segment.attachmentId,
                        attachmentDao = attachmentDao,
                        onClick = { onAttachmentClick(segment.attachmentId) }
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

/**
 * 单张附件缩略图。`LruBitmapCache` 命中直接取,miss 走 `BitmapFactory` 解码
 * 256px 长边 + RGB_565(内存占位 1/3,正文内联多图场景下更稳)。
 */
@Composable
private fun InlineAttachmentImage(attachmentId: String, attachmentDao: NoteAttachmentDao, onClick: () -> Unit) {
    val context = LocalContext.current
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        key1 = attachmentId
    ) {
        val entity = withContext(Dispatchers.IO) {
            runCatching { attachmentDao.observeById(attachmentId).first() }.getOrNull()
        }
        val localPath = entity?.localPath
        if (localPath.isNullOrEmpty()) {
            value = null
            return@produceState
        }
        LruBitmapCache.instance.get(localPath)?.let { cached ->
            value = cached.asImageBitmap()
            return@produceState
        }
        val decoded = withContext(Dispatchers.IO) {
            runCatching {
                BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    .let { bounds ->
                        BitmapFactory.decodeFile(localPath, bounds)
                        BitmapFactory.Options().apply {
                            inSampleSize = calculateThumbSample(
                                bounds.outWidth,
                                bounds.outHeight,
                                256
                            )
                            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                        }
                    }
                    .let { opts ->
                        BitmapFactory.decodeFile(localPath, opts)
                    }
            }.getOrNull()
        }
        if (decoded != null) {
            LruBitmapCache.instance.put(localPath, decoded)
            value = decoded.asImageBitmap()
        } else {
            value = null
        }
    }

    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant
    val failedText = stringResource(R.string.quicknote_attachment_image_load_failed)
    // fix-review-r1 F7:a11y H5 — Image 之前 contentDescription = null,TalkBack 滑过
    // 缩略图直接沉默;附件 clickable Box 也是 null,失败占位同理。用 localPath basename
    // (或兜底 attachmentId 前 8 位)做轻量描述,屏幕阅读器至少能听到「附件图片:<名>」。
    val entity by produceState<com.yy.writingwithai.core.data.db.entity.NoteAttachmentEntity?>(
        initialValue = null,
        key1 = attachmentId
    ) {
        value = withContext(Dispatchers.IO) {
            runCatching { attachmentDao.observeById(attachmentId).first() }.getOrNull()
        }
    }
    val displayName = remember(entity, attachmentId) {
        val path = entity?.localPath
        when {
            path.isNullOrEmpty() -> attachmentId.take(8)
            else -> path.substringAfterLast('/').ifBlank { attachmentId.take(8) }
        }
    }
    val imageA11y = stringResource(R.string.quicknote_attachment_image_a11y, displayName)

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = imageA11y,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(placeholderColor)
                .clickable(onClick = onClick)
        )
    } else {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(placeholderColor)
                .clickable(onClick = onClick),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Text(
                text = failedText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
