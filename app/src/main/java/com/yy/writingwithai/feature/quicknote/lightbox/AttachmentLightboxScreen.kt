package com.yy.writingwithai.feature.quicknote.lightbox

import android.graphics.BitmapFactory
import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yy.writingwithai.R

/**
 * fix-review-r1 F2:全屏附件 lightbox。
 *
 * - 黑色背景 + [ContentScale.Fit] 显示原图;
 * - 顶栏 Close 按钮([R.string.quicknote_attachment_lightbox_close]);
 * - 底部 footer 显示文件名 + 格式化大小([R.string.quicknote_attachment_lightbox_size_fmt]);
 * - 单击黑色背景任意区域也可关闭;
 * - 系统 back / predictive back gesture 由 NavHost popBackStack 处理。
 */
@Composable
@Suppress("ktlint:standard:function-signature")
fun AttachmentLightboxScreen(
    onBack: () -> Unit,
    viewModel: AttachmentLightboxViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val closeLabel = stringResource(R.string.quicknote_attachment_lightbox_close)
    val notFoundLabel = stringResource(R.string.quicknote_attachment_lightbox_not_found)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(onClick = onBack)
    ) {
        when (val s = state) {
            is LightboxState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }
            is LightboxState.NotFound -> {
                Text(
                    text = notFoundLabel,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            is LightboxState.Ready -> {
                val bitmap = remember(s.localPath) {
                    runCatching {
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(s.localPath, bounds)
                        val decodeOpts = BitmapFactory.Options().apply {
                            inSampleSize = calculateSample(bounds.outWidth, bounds.outHeight)
                            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                        }
                        BitmapFactory.decodeFile(s.localPath, decodeOpts)
                    }.getOrNull()
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = s.displayName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = notFoundLabel,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                LightboxFooter(
                    displayName = s.displayName,
                    sizeText = Formatter.formatShortFileSize(context, s.fileSizeBytes),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                )
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = closeLabel,
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun LightboxFooter(displayName: String, sizeText: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = displayName,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = sizeText,
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/** 把大图压到长边 <= 2048px,防止 OOM;lightbox 全屏尺寸下尽量清晰。 */
private fun calculateSample(width: Int, height: Int): Int {
    val max = 2048
    var sample = 1
    var w = width
    var h = height
    while (w / 2 >= max || h / 2 >= max) {
        w /= 2
        h /= 2
        sample *= 2
    }
    return sample.coerceAtLeast(1)
}
