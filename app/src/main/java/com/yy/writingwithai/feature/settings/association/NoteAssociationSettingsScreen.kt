package com.yy.writingwithai.feature.settings.association

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import com.yy.writingwithai.R

/**
 * entity-extraction-polish §4.2:笔记关联设置屏。
 *
 * - TopAppBar 返回
 * - 「关联阈值」Slider(0.05–0.80 step 14,默认 0.10)+ 当前值 Text
 * - 「暂停实体回填」Switch(打开后 BackfillScheduler + EntityBackfillWorker 都不跑)
 * - 「立即重跑回填」OutlinedButton(force=true,Worker 自检仍生效)
 * - 「回填进度」LinearProgressIndicator + 状态 Text
 * - 一次性迁移 banner(upgrade 检测到旧 >0.50 值时显示)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteAssociationSettingsScreen(onBack: () -> Unit, viewModel: NoteAssociationSettingsViewModel = hiltViewModel()) {
    val threshold by viewModel.threshold.collectAsStateWithLifecycle()
    val paused by viewModel.paused.collectAsStateWithLifecycle()
    val workInfo by viewModel.workInfo.collectAsStateWithLifecycle()
    val migrationBanner by viewModel.migrationBanner.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.note_association_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // §4.3:一次性迁移 banner
            if (migrationBanner) {
                MigrationBanner(onDismiss = viewModel::acknowledgeMigrationBanner)
            }

            // §4.1:阈值 Slider
            ThresholdSection(
                value = threshold,
                onValueChangeFinished = viewModel::onThresholdChangeFinished,
                range = viewModel.sliderRange,
                steps = viewModel.sliderSteps
            )

            // §4.1:暂停 Switch
            PauseSection(
                paused = paused,
                onToggle = viewModel::onPauseToggle
            )

            // §4.2:立即重跑 + 进度
            ReRunSection(
                workInfo = workInfo,
                paused = paused,
                onReRunClick = viewModel::onReRunClick
            )
        }
    }
}

@Composable
private fun MigrationBanner(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.note_association_migration_banner),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onDismiss) {
            Text(stringResource(R.string.common_acknowledge))
        }
    }
}

@Composable
private fun ThresholdSection(
    value: Float,
    onValueChangeFinished: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    steps: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.note_association_threshold_label),
            style = MaterialTheme.typography.titleMedium
        )
        // R4-1-3 fix:用 remember { mutableFloatStateOf(value) } 持有拖动中的实时值。
        // 原实现 `onValueChangeFinished = { onValueChangeFinished(value) }` 里的 `value`
        // 是 outer 形参 snapshot,Slider 拖完 release 时外层 value 还未 recompose,写盘的是旧值。
        // 现在 onValueChange 实时更新 dragState,onValueChangeFinished 写 dragState(实时位置),
        // 然后重置 dragState 回 outer value(等下次 recompose 同步)。
        val dragState = remember { mutableFloatStateOf(value) }
        Slider(
            value = value,
            onValueChange = { dragState.floatValue = it },
            onValueChangeFinished = {
                onValueChangeFinished(dragState.floatValue)
                dragState.floatValue = value
            },
            valueRange = range,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = stringResource(R.string.note_association_threshold_value_fmt, value),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PauseSection(paused: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.note_association_pause_label),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = paused, onCheckedChange = onToggle)
    }
}

@Composable
private fun ReRunSection(workInfo: WorkInfo?, paused: Boolean, onReRunClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.note_association_backfill_section),
            style = MaterialTheme.typography.titleMedium
        )

        // §4.4:进度 — RUNNING 时显示,无活跃 work 时隐藏
        if (workInfo != null) {
            val processed = workInfo.progress.getInt("processed", 0)
            val total = workInfo.progress.getInt("total", 0)
            val succeeded = workInfo.progress.getInt("succeeded", 0)
            val failed = workInfo.progress.getInt("failed", 0)
            val ratio = if (total > 0) processed.toFloat() / total else 0f

            LinearProgressIndicator(
                progress = { ratio.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResource(
                    R.string.note_association_backfill_progress_fmt,
                    processed,
                    total,
                    succeeded,
                    failed
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(workInfoStateRes(workInfo)),
                style = MaterialTheme.typography.bodySmall,
                color = when (workInfo.state) {
                    WorkInfo.State.FAILED -> MaterialTheme.colorScheme.error
                    WorkInfo.State.SUCCEEDED -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        // §4.2:立即重跑按钮 — RUNNING 时禁用
        val isRunning = workInfo?.state == WorkInfo.State.RUNNING ||
            workInfo?.state == WorkInfo.State.ENQUEUED
        // R4-1-2 fix:paused 状态下按钮也禁用,避免 force=true enqueue 后 Worker 立刻因 pause 失败
        // 显示 red FAILED "已暂停"。要让"立即重跑"生效,用户必须先关掉暂停开关。
        Button(
            onClick = onReRunClick,
            enabled = !isRunning && !paused,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.note_association_rerun_button))
        }
        if (paused) {
            Text(
                text = stringResource(R.string.note_association_paused_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

private fun workInfoStateRes(workInfo: WorkInfo) = when (workInfo.state) {
    WorkInfo.State.RUNNING -> R.string.note_association_backfill_status_running
    WorkInfo.State.ENQUEUED -> R.string.note_association_backfill_status_enqueued
    WorkInfo.State.SUCCEEDED -> R.string.note_association_backfill_status_succeeded
    WorkInfo.State.FAILED -> R.string.note_association_backfill_status_failed
    WorkInfo.State.BLOCKED -> R.string.note_association_backfill_status_blocked
    WorkInfo.State.CANCELLED -> R.string.note_association_backfill_status_cancelled
}
