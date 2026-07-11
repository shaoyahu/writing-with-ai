package com.yy.writingwithai.feature.aiwriting.usage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yy.writingwithai.R
import com.yy.writingwithai.core.ai.api.WritingOp
import com.yy.writingwithai.core.ai.provider.ProviderRegistry
import com.yy.writingwithai.core.data.db.OpUsageBucket
import com.yy.writingwithai.core.data.db.ProviderUsageBucket
import com.yy.writingwithai.core.data.repo.UsagePeriod
import com.yy.writingwithai.core.data.repo.UsageSnapshot

/**
 * ai-usage-statistics §4:`AiUsageScreen` 顶层组合根。
 *
 * 渲染 7d/30d 双 chip + totalTokens 横幅 + Canvas 条形图 + op 表 + provider 表 + empty state。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiUsageScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: AiUsageViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ai_usage_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            PeriodChips(
                current = state.period,
                onSelect = { viewModel.setPeriod(it) }
            )
            Spacer(Modifier.height(16.dp))
            when (val s = state) {
                is AiUsageUiState.Loading -> CenterBox { CircularProgressIndicator() }
                is AiUsageUiState.Empty -> CenterBox {
                    Text(
                        text = stringResource(R.string.ai_usage_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is AiUsageUiState.Ready -> ReadyContent(state = s, vm = viewModel)
            }
        }
    }
}

@Composable
private fun PeriodChips(current: UsagePeriod, onSelect: (UsagePeriod) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = current is UsagePeriod.Last7Days,
            onClick = { onSelect(UsagePeriod.Last7Days()) },
            label = { Text(stringResource(R.string.ai_usage_period_7d)) }
        )
        FilterChip(
            selected = current is UsagePeriod.Last30Days,
            onClick = { onSelect(UsagePeriod.Last30Days()) },
            label = { Text(stringResource(R.string.ai_usage_period_30d)) }
        )
    }
}

@Composable
private fun ReadyContent(state: AiUsageUiState.Ready, vm: AiUsageViewModel) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = stringResource(R.string.ai_usage_total_tokens, state.snapshot.totalTokens.toInt()),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        item {
            UsageBarChart(
                buckets = state.snapshot.byDay,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            )
        }
        item {
            SectionHeader(stringResource(R.string.ai_usage_section_by_op))
        }
        items(state.snapshot.byOp) { bucket ->
            OpRow(bucket)
        }
        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        }
        item {
            SectionHeader(stringResource(R.string.ai_usage_section_by_provider))
        }
        items(state.snapshot.byProvider) { bucket ->
            ProviderRow(bucket, costUsd = state.costByProvider[bucket.providerId])
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun OpRow(bucket: OpUsageBucket) {
    val label = opLabel(bucket.op)
    UsageRow(label = label, tokens = bucket.sumTotal)
}

@Composable
private fun ProviderRow(bucket: ProviderUsageBucket, costUsd: Double?) {
    val label = ProviderRegistry.displayName(bucket.providerId)
    Column(modifier = Modifier.fillMaxWidth()) {
        UsageRow(label = label, tokens = bucket.sumTotal)
        val costText = when {
            bucket.sumTotal == 0 -> ""
            costUsd == null -> "  ·  " + stringResource(R.string.ai_usage_cost_disabled)
            else -> "  ·  " + stringResource(R.string.ai_usage_cost_fmt, costUsd)
        }
        if (costText.isNotEmpty()) {
            Text(
                text = costText.trim(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun UsageRow(label: String, tokens: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.size(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "$tokens",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun CenterBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) { content() }
}

/** `op` string → 走 R.string 的展示名,未知值直接显示原串。 */
@Composable
private fun opLabel(op: String): String {
    val resId = when (op) {
        WritingOp.EXPAND.name -> R.string.ai_usage_op_expand
        WritingOp.POLISH.name -> R.string.ai_usage_op_polish
        WritingOp.ORGANIZE.name -> R.string.ai_usage_op_organize
        WritingOp.TRANSLATE.name -> R.string.ai_usage_op_translate
        WritingOp.SUMMARIZE.name -> R.string.ai_usage_op_summarize
        else -> null
    }
    return if (resId != null) stringResource(resId) else op
}

@Preview(showBackground = true)
@Composable
private fun AiUsageScreenPreview() {
    MaterialTheme {
        AiUsageScreen(onBack = {})
    }
}

@Suppress("unused")
private fun touchSnapshotType(s: UsageSnapshot) = s.totalTokens
