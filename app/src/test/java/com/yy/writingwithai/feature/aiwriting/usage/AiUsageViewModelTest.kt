package com.yy.writingwithai.feature.aiwriting.usage

import com.yy.writingwithai.core.data.db.ProviderUsageBucket
import com.yy.writingwithai.core.data.repo.AiUsageRepository
import com.yy.writingwithai.core.data.repo.UsagePeriod
import com.yy.writingwithai.core.data.repo.UsageSnapshot
import com.yy.writingwithai.core.prefs.ProviderCostStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ai-usage-statistics §7.3:VM 单测覆盖 [AiUsageViewModel] 关键路径:
 * - Empty:无 token → EmptyUiState;
 * - Ready:有 token + 单 provider 费率未配 → map(cost) = null;
 * - Ready:有 token + 费率已配 → cost > 0;
 * - `estimateCostUsd` 公开纯函数直接断言。
 *
 * VM 内部 `stateIn(viewModelScope, ...)` 需要 main dispatcher — 用
 * `Dispatchers.setMain(UnconfinedTestDispatcher())` 替换,避免 init 时抛
 * "Module with the Main dispatcher is missing"。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AiUsageViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `empty snapshot maps to Empty state`() = runTest {
        val dao = mockk<AiUsageRepository>()
        val costStore = mockk<ProviderCostStore>()
        every { costStore.getCostRate(any<String>()) } returns (0.0 to 0.0)
        every { dao.observeUsage(any()) } returns flowOf(
            UsageSnapshot(byDay = emptyList(), byOp = emptyList(), byProvider = emptyList(), totalTokens = 0L)
        )
        val vm = AiUsageViewModel(dao, costStore)

        vm.setPeriod(UsagePeriod.Last7Days())
        // stateIn(WhileSubscribed(5000)) requires active subscription — use first { } to advance past Loading
        val state = vm.uiState.first { it !is AiUsageUiState.Loading }
        assertTrue("expected Empty, got $state", state is AiUsageUiState.Empty)
    }

    @Test
    fun `ready snapshot with no cost rate maps cost to null`() = runTest {
        val dao = mockk<AiUsageRepository>()
        val costStore = mockk<ProviderCostStore>()
        every { costStore.getCostRate(any<String>()) } returns (0.0 to 0.0)
        val byProvider = listOf(
            ProviderUsageBucket(providerId = "deepseek", sumInput = 100, sumOutput = 50, sumTotal = 150, count = 1)
        )
        every { dao.observeUsage(any()) } returns flowOf(
            UsageSnapshot(
                byDay = listOf(
                    com.yy.writingwithai.core.data.db.DailyUsageBucket(
                        dayBucket = 0, sumInput = 100, sumOutput = 50, sumTotal = 150, count = 1
                    )
                ),
                byOp = emptyList(),
                byProvider = byProvider,
                totalTokens = 150L
            )
        )
        val vm = AiUsageViewModel(dao, costStore)
        vm.setPeriod(UsagePeriod.Last7Days())
        val state = vm.uiState.first { it !is AiUsageUiState.Loading } as AiUsageUiState.Ready
        assertNull(state.costByProvider["deepseek"])
    }

    @Test
    fun `estimateCostUsd computes input+output per 1k`() = runTest {
        val dao = mockk<AiUsageRepository>()
        val costStore = mockk<ProviderCostStore>()
        // rate (0.001, 0.002) USD/1k-token
        every { costStore.getCostRate("deepseek") } returns (0.001 to 0.002)
        val vm = AiUsageViewModel(dao, costStore)
        // 1000 in + 500 out → 1*0.001 + 0.5*0.002 = 0.002
        val cost = vm.estimateCostUsd("deepseek", 1000, 500)
        assertEquals(0.002, cost!!, 1e-9)
    }

    @Test
    fun `estimateCostUsd returns null when both rates are zero`() = runTest {
        val dao = mockk<AiUsageRepository>()
        val costStore = mockk<ProviderCostStore>()
        every { costStore.getCostRate("deepseek") } returns (0.0 to 0.0)
        val vm = AiUsageViewModel(dao, costStore)
        val cost = vm.estimateCostUsd("deepseek", 1000, 500)
        assertNull(cost)
    }

    @Suppress("unused")
    private val mutableFlowForCompiler = MutableStateFlow(UsagePeriod.Last7Days())
}
