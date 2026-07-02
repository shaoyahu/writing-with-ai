package com.yy.writingwithai.feature.settings.prompt

import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.core.ai.api.WritingOp
import com.yy.writingwithai.core.ai.prompt.DefaultPrompts
import com.yy.writingwithai.core.prefs.FakePromptTemplateStore
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * PromptTemplateViewModel 单测:
 * - init 从 store 加载 drafts，null 时 fallback 到 DefaultPrompts
 * - onPromptChange 更新 draft 并将 op 加入 pendingSave
 * - save 持久化到 store 并从 pendingSave 移除
 * - resetToDefault 清除 store 条目和 draft
 * - onTabSwitch 仅切换 currentOp
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PromptTemplateViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: PromptTemplateViewModel
    private lateinit var store: FakePromptTemplateStore

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>()) } returns 0

        store = FakePromptTemplateStore()
    }

    @AfterEach
    fun tearDown() {
        if (::viewModel.isInitialized) {
            viewModel.viewModelScope.cancel()
        }
        Dispatchers.resetMain()
        unmockkStatic(android.util.Log::class)
    }

    @Test
    fun init_loadsDraftsFromStore_nullFallsBackToDefaultPrompts() = runTest(dispatcher) {
        // store 默认所有 op 为 null → fallback 到 DefaultPrompts
        viewModel = PromptTemplateViewModel(store)
        advanceUntilIdle()

        val drafts = viewModel.uiState.value.drafts
        assertEquals(DefaultPrompts.forOp(WritingOp.EXPAND), drafts[WritingOp.EXPAND])
        assertEquals(DefaultPrompts.forOp(WritingOp.POLISH), drafts[WritingOp.POLISH])
        assertEquals(DefaultPrompts.forOp(WritingOp.ORGANIZE), drafts[WritingOp.ORGANIZE])
        viewModel.viewModelScope.cancel()
        advanceUntilIdle()
    }

    @Test
    fun init_loadsDraftsFromStore_withSeededValues() = runTest(dispatcher) {
        // store 有自定义值 → 使用 store 值而非 DefaultPrompts
        store.seed(WritingOp.EXPAND, "custom expand prompt")
        viewModel = PromptTemplateViewModel(store)
        advanceUntilIdle()

        val drafts = viewModel.uiState.value.drafts
        assertEquals("custom expand prompt", drafts[WritingOp.EXPAND])
        // 未 seed 的仍 fallback
        assertEquals(DefaultPrompts.forOp(WritingOp.POLISH), drafts[WritingOp.POLISH])
        viewModel.viewModelScope.cancel()
        advanceUntilIdle()
    }

    @Test
    fun onPromptChange_updatesDraft_andAddsOpToPendingSave() = runTest(dispatcher) {
        viewModel = PromptTemplateViewModel(store)
        advanceUntilIdle()

        viewModel.onPromptChange(WritingOp.EXPAND, "modified expand")
        val state = viewModel.uiState.value

        assertEquals("modified expand", state.drafts[WritingOp.EXPAND])
        assertTrue(state.pendingSave.contains(WritingOp.EXPAND))
        // 其他 op 不受影响
        assertFalse(state.pendingSave.contains(WritingOp.POLISH))
        viewModel.viewModelScope.cancel()
        advanceUntilIdle()
    }

    @Test
    fun save_persistsToStore_andRemovesFromPendingSave() = runTest(dispatcher) {
        viewModel = PromptTemplateViewModel(store)
        advanceUntilIdle()

        viewModel.onPromptChange(WritingOp.POLISH, "new polish prompt")
        assertTrue(viewModel.uiState.value.pendingSave.contains(WritingOp.POLISH))

        viewModel.save(WritingOp.POLISH)
        advanceUntilIdle()

        // pendingSave 已清除
        assertFalse(viewModel.uiState.value.pendingSave.contains(WritingOp.POLISH))
        // store 已持久化
        assertEquals("new polish prompt", store.getForOp(WritingOp.POLISH))
        viewModel.viewModelScope.cancel()
        advanceUntilIdle()
    }

    @Test
    fun resetToDefault_clearsStoreEntry_andDraft() = runTest(dispatcher) {
        store.seed(WritingOp.ORGANIZE, "custom organize")
        viewModel = PromptTemplateViewModel(store)
        advanceUntilIdle()
        assertEquals("custom organize", viewModel.uiState.value.drafts[WritingOp.ORGANIZE])

        viewModel.onPromptChange(WritingOp.ORGANIZE, "dirty organize")
        assertTrue(viewModel.uiState.value.pendingSave.contains(WritingOp.ORGANIZE))

        viewModel.resetToDefault(WritingOp.ORGANIZE)
        advanceUntilIdle()

        // draft 被清为空字符串
        assertEquals("", viewModel.uiState.value.drafts[WritingOp.ORGANIZE])
        // pendingSave 已清除
        assertFalse(viewModel.uiState.value.pendingSave.contains(WritingOp.ORGANIZE))
        // store 也被重置(getForOp 返回 null 因为空字符串被 takeIf 过滤)
        assertEquals(null, store.getForOp(WritingOp.ORGANIZE))
        viewModel.viewModelScope.cancel()
        advanceUntilIdle()
    }

    @Test
    fun onTabSwitch_changesCurrentOpOnly() = runTest(dispatcher) {
        viewModel = PromptTemplateViewModel(store)
        advanceUntilIdle()
        assertEquals(WritingOp.EXPAND, viewModel.uiState.value.currentOp)

        viewModel.onTabSwitch(WritingOp.POLISH)
        assertEquals(WritingOp.POLISH, viewModel.uiState.value.currentOp)

        // drafts 和 pendingSave 不受影响
        assertEquals(DefaultPrompts.forOp(WritingOp.EXPAND), viewModel.uiState.value.drafts[WritingOp.EXPAND])
        assertTrue(viewModel.uiState.value.pendingSave.isEmpty())
        viewModel.viewModelScope.cancel()
        advanceUntilIdle()
    }
}
