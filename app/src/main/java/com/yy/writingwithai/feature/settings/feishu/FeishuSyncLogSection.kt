package com.yy.writingwithai.feature.settings.feishu

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yy.writingwithai.R
import com.yy.writingwithai.core.feishu.sync.FeishuSyncEventEntity
import com.yy.writingwithai.core.feishu.sync.SyncDirection
import java.text.DateFormat
import java.util.Date

/**
 * ThreadLocal DateFormat:DateFormat / SimpleDateFormat 非线程安全，共享顶层实例在并行
 * 重组下会损坏 Calendar 状态。每个线程持一份，format() 互不干扰。
 */
private val SYNC_EVENT_TIME_FORMAT: ThreadLocal<DateFormat> =
    ThreadLocal.withInitial { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM) }

/**
 * feishu-bidir-sync · 设置页「飞书同步日志」section(tasks §7)。
 *
 * feishu-sync-end-to-end 重构:本 section 纯渲染，数据由 caller 通过 [events] 传入
 * (caller 用 [FeishuSyncEventDao.observeLast] collectAsStateWithLifecycle 拿响应式 list)。
 *
 * 显示最近 20 条 sync event;每条:时间 / 方向 / 状态 / 错误。
 * 顶部 disclaimer:同步不消耗 AI token。
 */
@Composable
fun FeishuSyncLogSection(events: List<FeishuSyncEventEntity>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.feishu_sync_log_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.padding(top = 2.dp))
        Text(
            text = stringResource(R.string.feishu_sync_log_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.padding(top = 8.dp))
        HorizontalDivider()
        if (events.isEmpty()) {
            Text(
                text = stringResource(R.string.feishu_sync_log_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            events.forEach { event ->
                SyncEventRow(event)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun SyncEventRow(event: FeishuSyncEventEntity) {
    val directionRes = when (event.direction) {
        SyncDirection.PUSH -> R.string.feishu_sync_direction_push
        SyncDirection.PULL -> R.string.feishu_sync_direction_pull
        SyncDirection.BIDIR -> R.string.feishu_sync_direction_bidir
    }
    val statusRes = when (event.status) {
        "OK" -> R.string.feishu_sync_status_ok
        "ERROR" -> R.string.feishu_sync_status_error
        "FALLBACK_TO_UPDATE" -> R.string.feishu_sync_status_fallback_to_update
        else -> null
    }
    val directionText = stringResource(directionRes)
    val statusText = statusRes?.let { stringResource(it) } ?: event.status
    val timeText = SYNC_EVENT_TIME_FORMAT.get().format(Date(event.createdAt))

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$directionText · $statusText",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.padding(top = 2.dp))
            Text(
                text = timeText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!event.errorMessage.isNullOrEmpty()) {
                Spacer(Modifier.padding(top = 2.dp))
                Text(
                    text = event.errorMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = event.noteId.take(8),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
