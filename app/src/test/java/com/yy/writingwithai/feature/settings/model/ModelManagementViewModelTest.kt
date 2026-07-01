package com.yy.writingwithai.feature.settings.model

import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.R
import com.yy.writingwithai.core.ai.api.AiGateway
import com.yy.writingwithai.core.ai.provider.CustomProviderStore
import com.yy.writingwithai.core.ai.provider.FakeProviderPrefsStore
import com.yy.writingwithai.core.ai.provider.ProviderPrefsStore
import com.yy.writingwithai.core.prefs.SecureApiKeyStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * fix-2026-06-28-ai-model-selection-actually-used §5.3/5.4 单测:
 * - 5.3 `onModelSelected` 写 prefs 失败 → emit `SaveResult.Failed(MODEL_SELECT)`
 * - 5.4 `saveProvider` 首次保存 → `setSelectedModelIfMissing` 自举到 provider 默认 model
 *
 * 直接构造 VM + FakeProviderPrefsStore + MockK SecureApiKeyStore / CustomProviderStore /
 * AiGateway;UnconfinedTestDispatcher 跑 viewModelScope.launch 同步推进协程。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ModelManagementViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: ModelManagementViewModel
    private lateinit var secureApiKeyStore: SecureApiKeyStore
    private lateinit var providerPrefsStore: ProviderPrefsStore
    private lateinit var customProviderStore: CustomProviderStore
    private lateinit var aiGateway: AiGateway

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // VM init {} 里失败兜底用 Log.e，屏蔽 mockk 未拦截的静态 Log 调用。
        mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>()) } returns 0

        secureApiKeyStore = mockk(relaxed = true)
        providerPrefsStore = FakeProviderPrefsStore()
        customProviderStore = mockk(relaxed = true)
        aiGateway = mockk(relaxed = true)
        // 默认观察为空集 / 空 list,init {} 的协程不会读到未 mock 的方法。
        every { secureApiKeyStore.observeConfiguredProviders() } returns flowOf(emptySet())
        every { customProviderStore.observeAll() } returns flowOf(emptyList())
        coEvery { aiGateway.listProviders() } returns emptyList()
    }

    @AfterEach
    fun tearDown() {
        if (::viewModel.isInitialized) {
            viewModel.viewModelScope.cancel()
        }
        Dispatchers.resetMain()
        unmockkStatic(android.util.Log::class)
    }

    /**
     * fix-2026-06-28-ai-model-selection-actually-used §5.3:`onModelSelected` 内部
     * `setSelectedModel` 抛 IOException → emit
     * `SaveResult.Failed(operationKind=OperationKind.MODEL_SELECT)`,UI 据此弹
     * "模型切换失败" Snackbar。
     */
    @Test
    fun onModelSelected_writeFailure_emitsSaveFailedEvent_withModelSelectKind() = runTest(dispatcher) {
        // 注入 IOException:走 FakeProviderPrefsStore 的 setSelectedModelError hook。
        (providerPrefsStore as FakeProviderPrefsStore).setSelectedModelError =
            java.io.IOException("disk full")
        viewModel = newViewModel()
        advanceUntilIdle()
        // SharedFlow replay=0，无 collector 时 tryEmit 丢。先启动 collector 再触发。
        // first() suspend 到 emit 出现，然后 await 拿值。
        val eventDeferred = async(dispatcher) { viewModel.saveEvents.first() }
        advanceUntilIdle()

        viewModel.onModelSelected("deepseek", "deepseek-v4-pro")

        val event = eventDeferred.await()
        assertTrue(event is SaveResult.Failed, "MUST emit Failed，实际 = $event")
        event as SaveResult.Failed
        assertEquals(SaveResult.OperationKind.MODEL_SELECT, event.operationKind)
        assertEquals(R.string.model_management_dropdown_save_failed, event.messageRes)
        assertEquals("disk full", event.rawDetail)
        // init 块 collect 协程 long-running，在 runTest 退出前手动 cancel 避免
        // UncompletedCoroutinesError(tearDown 发生在 runTest 之后)。cancel 是
        // 异步的，需要 advanceUntilIdle 让取消生效。
        viewModel.viewModelScope.cancel()
        advanceUntilIdle()
    }

    /**
     * fix-2026-06-28-ai-model-selection-actually-used §5.4:`saveProvider` 首次保存 →
     * VM 走 `setSelectedModelIfMissing(providerId, cfg.defaultModel)` 自举 selectedModel
     * 到 provider 默认 model，确保下次 AI 调用走 defaultModel(不是 supportedModels[0])。
     */
    @Test
    fun saveProvider_firstTime_autoInitsSelectedModelToDefault() = runTest(dispatcher) {
        // 当前 prefs 里 selectedModel["deepseek"] 必须为 null，否则跳过自举。
        // FakeProviderPrefsStore 初始 Map 为空，getSelectedModel → null。
        assertEquals(null, providerPrefsStore.getSelectedModel("deepseek"))
        viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.saveProvider("deepseek", apiKey = "sk-test", model = null)
        advanceUntilIdle()

        // 自举后 selectedModel == "deepseek-v4-pro"(DeepseekConfig.config.defaultModel;
        // review-r2 H1 fix:defaultModel 从 flash 改 pro，作为 fallback 入口与 provider
        // 文档"v1 UI 默认:pro(推荐体验)"对齐)。
        assertEquals("deepseek-v4-pro", providerPrefsStore.getSelectedModel("deepseek"))
        viewModel.viewModelScope.cancel()
        advanceUntilIdle()
    }

    private fun newViewModel(): ModelManagementViewModel = ModelManagementViewModel(
        secureApiKeyStore = secureApiKeyStore,
        providerPrefsStore = providerPrefsStore,
        customProviderStore = customProviderStore,
        aiGateway = aiGateway
    )
}
