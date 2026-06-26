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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yy.writingwithai.core.feishu.sync.FeishuSyncEventDao
import com.yy.writingwithai.core.feishu.sync.FeishuSyncEventEntity
import com.yy.writingwithai.core.feishu.sync.SyncDirection
import java.text.DateFormat
import java.util.Date

/**
 * fix-2026-06-26-review-r3 M2:`DateFormat` 实例提到顶层,避免 `SyncEventRow` 每行 / 每次
 * 重组重新构造。`DateFormat.getDateTimeInstance` 内部用 `Calendar.getInstance` + 缓存
 * `DateFormatSymbols`,构造开销小但频繁;顶部同步日志滚动时常驻可见。
 */
private val SYNC_EVENT_TIME_FORMAT =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)

/**
 * feishu-bidir-sync · 设置页「飞书同步日志」section(tasks §7)。
 *
 * 显示最近 20 条 sync event;每条:时间 / 方向 / 状态 / 错误。
 * 顶部 disclaimer:同步不消耗 AI token。
 */
@Composable
fun FeishuSyncLogSection(eventDao: FeishuSyncEventDao, modifier: Modifier = Modifier) {
    var events by remember { mutableStateOf<List<FeishuSyncEventEntity>>(emptyList()) }
    LaunchedEffect(Unit) {
        events = eventDao.listLast(20)
    }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = "飞书同步日志",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.padding(top = 2.dp))
        Text(
            text = "同步不消耗 AI token,只调飞书 API",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.padding(top = 8.dp))
        HorizontalDivider()
        if (events.isEmpty()) {
            Text(
                text = "暂无同步记录",
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
    val directionText = when (event.direction) {
        SyncDirection.PUSH -> "推送"
        SyncDirection.PULL -> "拉取"
        SyncDirection.BIDIR -> "双向"
    }
    val timeText = SYNC_EVENT_TIME_FORMAT.format(Date(event.createdAt))

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$directionText · ${event.status}",
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
