package com.yy.writingwithai.feature.aiwriting.usage

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.core.data.repo.AiUsageRepository
import com.yy.writingwithai.core.data.repo.UsagePeriod
import com.yy.writingwithai.core.data.repo.UsageSnapshot
import com.yy.writingwithai.core.prefs.ProviderCostStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * ai-usage-statistics §3:UI 状态。
 *
 * - Loading:尚未拿到任何 snapshot(罕见,只在第 1 帧出现)。
 * - Empty:整窗口(`byProvider.all { it.sumTotal == 0 }` && `byOp.all { it.sumTotal == 0 }`)
 *   无任何 token 消耗 → UI 走 empty state,不渲染图表 / 表格。
 * - Ready:正常数据。
 *
 * `costByProvider` 是**展开过**的成本估算,key = providerId;value = null 表示"未配置费率",
 * 否则 = USD 估值。VM 层一次性算完,UI 不读 ProviderCostStore,职责清晰。
 */
@Immutable
sealed interface AiUsageUiState {
    val period: UsagePeriod

    data class Loading(override val period: UsagePeriod) : AiUsageUiState

    data class Empty(override val period: UsagePeriod) : AiUsageUiState

    data class Ready(
        override val period: UsagePeriod,
        val snapshot: UsageSnapshot,
        val costByProvider: Map<String, Double?>
    ) : AiUsageUiState
}

/**
 * ai-usage-statistics §3:`AiUsageScreen` 专 @HiltViewModel。
 *
 * - `_period` 切 7d/30d → `flatMapLatest` 自动重拉 repo.observeUsage;
 * - `stateIn(viewModelScope, WhileSubscribed(5000), Loading)` 保活 UI 配置变更;
 * - `setPeriod` 由 UI chip 点击调;
 * - `estimateCostUsd` 公开纯函数,UI 表行计算用。
 *
 * `ExperimentalCoroutinesApi` 软抑制 `flatMapLatest` 警告(libs §Coroutines 已启用 FlowPreview)。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AiUsageViewModel
@Inject
constructor(
    private val repo: AiUsageRepository,
    private val costStore: ProviderCostStore
) : ViewModel() {
    private val _period = MutableStateFlow<UsagePeriod>(UsagePeriod.Last7Days())
    val period: StateFlow<UsagePeriod> = _period

    val uiState: StateFlow<AiUsageUiState> =
        _period
            .flatMapLatest { p: UsagePeriod ->
                repo.observeUsage(p).map { snap -> withCost(snap, p) }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = AiUsageUiState.Loading(UsagePeriod.Last7Days())
            )

    fun setPeriod(p: UsagePeriod) {
        // UI chip 互斥,可能传回同 period;flatMapLatest 自带去重语义 OK。
        _period.value = p
    }

    private fun withCost(snap: UsageSnapshot, p: UsagePeriod): AiUsageUiState {
        val anyToken = snap.byProvider.any { it.sumTotal > 0 } || snap.byOp.any { it.sumTotal > 0 }
        if (!anyToken && snap.totalTokens == 0L) {
            return AiUsageUiState.Empty(p)
        }
        val cost = snap.byProvider.associate { bucket ->
            bucket.providerId to estimateCostUsd(bucket.providerId, bucket.sumInput, bucket.sumOutput)
        }
        return AiUsageUiState.Ready(p, snap, cost)
    }

    /**
     * 按 (inputRate, outputRate) USD-per-1k-token 计算。
     * - 任一率为 0 → null(UI 显示"未配置成本费率");
     * - token=0 → 返回 `0.0`(非 null,避免空表行歧义);
     * - 全 0 + 全 0 token → null 也属合理,UI 据此隐藏。
     */
    fun estimateCostUsd(providerId: String, sumInput: Int, sumOutput: Int): Double? {
        val (inRate, outRate) = costStore.getCostRate(providerId)
        if (inRate == 0.0 && outRate == 0.0) return null
        return (sumInput * inRate + sumOutput * outRate) / 1000.0
    }
}
