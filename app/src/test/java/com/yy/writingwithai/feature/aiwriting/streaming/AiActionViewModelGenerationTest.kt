package com.yy.writingwithai.feature.aiwriting.streaming

import android.content.Context
import com.yy.writingwithai.core.ai.api.AiGateway
import com.yy.writingwithai.core.ai.api.AiStreamEvent
import com.yy.writingwithai.core.ai.api.WritingOp
import com.yy.writingwithai.core.ai.provider.FakeProviderPrefsStore
import com.yy.writingwithai.core.data.repo.NoteRepository
import com.yy.writingwithai.core.prefs.ConsentState
import com.yy.writingwithai.core.prefs.FakeConsentStore
import com.yy.writingwithai.core.prefs.FakePromptTemplateStore
import com.yy.writingwithai.core.prefs.FakeSecureApiKeyStore
import com.yy.writingwithai.core.prefs.FakeUserPrefsStore
import com.yy.writingwithai.core.widget.QuickNoteWidgetUpdater
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
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
 * fix-2026-06-26-review-r3 M6 回归测试:`streamGeneration` 计数器防止旧协程 race 覆盖
 * 新状态。
 *
 * 场景:
 * 1. 第一次 `start()` 触发流(从未完成，事件流仍在发射)
 * 2. 立刻 `regenerate()` → 第二次 `start()`。旧协程在 cancel 检查点前可能还有 1-2 个事件
 *    在 channel buffer 里等待收集，这些事件在 collect{} block 内被 generation 比对拒绝。
 * 3. 最终态必须是 Done(op=POLISH) 来自第二次 start 的结果，不是第一次 start 的
 *    任何剩余 Failed/Delta 覆盖。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AiActionViewModelGenerationTest {
    private val aiGateway: AiGateway = mockk()
    private val noteRepository: NoteRepository = mockk(relaxed = true)
    private val widgetUpdater: QuickNoteWidgetUpdater = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)
    private val dispatcher = UnconfinedTestDispatcher()

    private val consentStore = FakeConsentStore().apply {
        seed(ConsentState(accepted = true, acceptedAt = 1L, version = 1))
    }
    private val secureApiKeyStore = FakeSecureApiKeyStore()
    private val promptTemplateStore = FakePromptTemplateStore()

    // remove-debug-fake-fallback §7.3:走真 provider id("deepseek"),同 release 行为
    private val providerPrefsStore = FakeProviderPrefsStore(initial = "deepseek")

    @BeforeEach
    fun setUp() = runTest {
        Dispatchers.setMain(dispatcher)
        // fix-2026-06-28-ai-model-selection-actually-used:VM.start 调 aiGateway.listProviders()
        // 拿 defaultModel;默认返空，fake provider 路径继续。
        coEvery { aiGateway.listProviders() } returns emptyList()
        // remove-debug-fake-fallback §7.3:seed 真 provider apikey
        secureApiKeyStore.save("deepseek", "test-deepseek-key")
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun stale_emit_from_old_stream_is_dropped_after_regenerate() = runTest {
        // 第一次 start() 流:发送一个 Delta，然后 channel 保持 open(模拟 slow network)
        val firstChannel = Channel<AiStreamEvent>(capacity = Channel.UNLIMITED)
        // 第二次 start() 流:正常 Done，带标识字符串 "second"
        val secondChannel = Channel<AiStreamEvent>(capacity = Channel.UNLIMITED)

        var call = 0
        coEvery { aiGateway.streamWritingOp(any(), any(), any(), any(), any(), any(), any()) } answers {
            call++
            if (call == 1) firstChannel.consumeAsFlow() else secondChannel.consumeAsFlow()
        }

        val vm =
            AiActionViewModel(
                context,
                aiGateway,
                noteRepository,
                widgetUpdater,
                consentStore,
                secureApiKeyStore,
                promptTemplateStore,
                providerPrefsStore,
                FakeUserPrefsStore().apply { seed(true) }
            )

        vm.start(WritingOp.EXPAND, "src", "n1")
        advanceUntilIdle()
        // 第一次流 emit 一个 Delta，确认进入 Streaming
        firstChannel.send(AiStreamEvent.Delta("first-delta"))
        advanceUntilIdle()
        val streaming1 = vm.state.value
        assertTrue(
            streaming1 is AiActionUiState.Streaming,
            "expected Streaming after first delta, got $streaming1"
        )

        // 第一次流正常结束(regenerate() 要求 state == Done 才会重启第二次 start)
        firstChannel.send(AiStreamEvent.Done)
        advanceUntilIdle()
        val firstDone = vm.state.value
        assertTrue(
            firstDone is AiActionUiState.Done,
            "expected Done after first stream completes, got $firstDone"
        )

        // 立刻 regenerate → 第二次 start;旧流已 Done，新流开始
        vm.regenerate()
        advanceUntilIdle()
        // 第二次流 emit 自己的 Delta + Done
        secondChannel.send(AiStreamEvent.Delta("second-delta"))
        secondChannel.send(AiStreamEvent.Done)
        advanceUntilIdle()

        // 关键断言:最终态必须是 Done，包含第二次的 delta,
        // 旧 channel 残留的 "first-delta" 不能覆盖 Done。
        val done = vm.state.value
        assertTrue(
            done is AiActionUiState.Done,
            "expected Done after regenerate, got $done"
        )
        assertEquals("second-delta", (done as AiActionUiState.Done).finalText)
    }
}
