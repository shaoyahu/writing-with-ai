package com.yy.writingwithai.feature.aiwriting.streaming

import android.content.Context
import com.yy.writingwithai.core.ai.api.AiError
import com.yy.writingwithai.core.ai.api.AiGateway
import com.yy.writingwithai.core.ai.api.AiStreamEvent
import com.yy.writingwithai.core.ai.api.WritingOp
import com.yy.writingwithai.core.ai.provider.FakeProviderPrefsStore
import com.yy.writingwithai.core.data.model.Note
import com.yy.writingwithai.core.data.model.NoteWithTags
import com.yy.writingwithai.core.data.repo.NoteRepository
import com.yy.writingwithai.core.prefs.ConsentState
import com.yy.writingwithai.core.prefs.FakeConsentStore
import com.yy.writingwithai.core.prefs.FakePromptTemplateStore
import com.yy.writingwithai.core.prefs.FakeSecureApiKeyStore
import com.yy.writingwithai.core.prefs.FakeUserPrefsStore
import com.yy.writingwithai.core.widget.QuickNoteWidgetUpdater
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
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

@OptIn(ExperimentalCoroutinesApi::class)
class AiActionViewModelTest {
    private val aiGateway: AiGateway = mockk()
    private val noteRepository: NoteRepository = mockk(relaxed = true)
    private val widgetUpdater: QuickNoteWidgetUpdater = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)
    private val dispatcher = UnconfinedTestDispatcher()

    // M4-4:VM 构造注入 ConsentStore + SecureApiKeyStore。M3 测试默认全同意 + 无 apikey
    // (走 fake provider 路径，行为与 M3 一致)。
    private val consentStore = FakeConsentStore().apply {
        seed(ConsentState(accepted = true, acceptedAt = 1L, version = 1))
    }

    // remove-debug-fake-fallback §7.3:FakeAiProvider 不再注册;单测必须 seed 真 provider id
    // + apikey 才能跑通 start() 路径(同 release 行为:无 apikey → ProviderNotConfigured)。
    // FakeSecureApiKeyStore.save() 是接口 public 方法;FakeProviderPrefsStore 走 deepseek 真 provider id。
    private val secureApiKeyStore = FakeSecureApiKeyStore()
    private val promptTemplateStore = FakePromptTemplateStore()
    private val providerPrefsStore = FakeProviderPrefsStore(initial = "deepseek")

    @BeforeEach
    fun setUp() = runTest {
        Dispatchers.setMain(dispatcher)
        // fix-2026-06-28-ai-model-selection-actually-used:VM.start 调 aiGateway.listProviders()
        // 拿 defaultModel(走 resolveActualModel 算 actualModel);默认返空，fake provider 路径继续。
        coEvery { aiGateway.listProviders() } returns emptyList()
        // remove-debug-fake-fallback §7.3:seed 真 provider apikey 让 start() 跑通(无 apikey → ProviderNotConfigured)
        secureApiKeyStore.save("deepseek", "test-deepseek-key")
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun start_drives_state_to_done_with_accumulated_text() = runTest {
        coEvery { aiGateway.streamWritingOp(any(), any(), any(), any(), any(), any(), any()) } returns
            flowOf(
                AiStreamEvent.Delta("你"),
                AiStreamEvent.Delta("好"),
                AiStreamEvent.Usage(2, 3, 5),
                AiStreamEvent.Done
            )

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
        vm.start(WritingOp.EXPAND, "x", "n1")
        advanceUntilIdle()
        val done = vm.state.value as AiActionUiState.Done
        assertEquals("你好", done.finalText)
        assertEquals(WritingOp.EXPAND, done.op)
        assertEquals(2, done.usage!!.inputTokens)
    }

    @Test
    fun accept_replace_upserts_and_writes_ai_metadata() = runTest {
        val note = sampleNote("n1", content = "x")
        val noteWithTags = NoteWithTags(note, emptyList())
        coEvery { aiGateway.streamWritingOp(any(), any(), any(), any(), any(), any(), any()) } returns
            flowOf(AiStreamEvent.Delta("新"), AiStreamEvent.Done)
        // M2 修后:acceptReplace 用 observeNoteWithTags 一次 IO 拿 Note + tags，不再单独调 getNote。
        coEvery { noteRepository.observeNoteWithTags("n1") } returns flowOf(noteWithTags)
        coEvery { noteRepository.upsert(any(), any()) } returns Unit
        coEvery { noteRepository.updateAiMetadata("n1", any(), any()) } returns Unit

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
        vm.start(WritingOp.POLISH, "x", "n1")
        advanceUntilIdle()
        vm.acceptReplace()
        advanceUntilIdle()

        coVerify { noteRepository.observeNoteWithTags("n1") }
        coVerify { noteRepository.upsert(match { it.content == "新" }, emptyList()) }
        coVerify { noteRepository.updateAiMetadata("n1", "polish", any()) }
        // acceptReplace 成功后 emit Replaced(op=POLISH); undo 后才会到 Idle。
        val replaced = vm.state.value as AiActionUiState.Replaced
        assertEquals(WritingOp.POLISH, replaced.op)
    }

    @Test
    fun reject_does_not_touch_repository() = runTest {
        coEvery { aiGateway.streamWritingOp(any(), any(), any(), any(), any(), any(), any()) } returns
            flowOf(AiStreamEvent.Delta("新"), AiStreamEvent.Done)

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
        vm.start(WritingOp.EXPAND, "x", "n1")
        advanceUntilIdle()
        vm.reject()
        advanceUntilIdle()

        coVerify(exactly = 0) { noteRepository.upsert(any(), any()) }
        assertEquals(AiActionUiState.Idle, vm.state.value)
    }

    @Test
    fun failed_event_drives_state_to_failed_and_dismiss_returns_to_idle() = runTest {
        coEvery { aiGateway.streamWritingOp(any(), any(), any(), any(), any(), any(), any()) } returns
            flowOf(AiStreamEvent.Failed(AiError.Network(500, "timeout"), recoverable = true))

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
        vm.start(WritingOp.EXPAND, "x", "n1")
        advanceUntilIdle()
        val failed = vm.state.value as AiActionUiState.Failed
        assertTrue(failed.error is AiError.Network)
        vm.dismiss()
        advanceUntilIdle()
        assertEquals(AiActionUiState.Idle, vm.state.value)
    }

    @Test
    fun regenerate_reuses_last_op_source_and_noteId() = runTest {
        val seq = mutableListOf<WritingOp>()
        coEvery { aiGateway.streamWritingOp(any(), any(), any(), any(), any(), any(), any()) } answers {
            val op = firstArg<WritingOp>()
            seq.add(op)
            flowOf(AiStreamEvent.Delta("v${seq.size}"), AiStreamEvent.Done)
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
        vm.start(WritingOp.EXPAND, "x", "n1")
        advanceUntilIdle()
        vm.regenerate()
        advanceUntilIdle()
        assertEquals(listOf(WritingOp.EXPAND, WritingOp.EXPAND), seq)
        val done = vm.state.value as AiActionUiState.Done
        assertEquals("v2", done.finalText)
    }

    @Test
    fun cancel_during_streaming_returns_to_idle() = runTest {
        val channel = Channel<AiStreamEvent>(capacity = Channel.UNLIMITED)
        coEvery { aiGateway.streamWritingOp(any(), any(), any(), any(), any(), any(), any()) } returns
            channel.consumeAsFlow()

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
        vm.start(WritingOp.EXPAND, "x", "n1")
        advanceUntilIdle()
        assertTrue(vm.state.value is AiActionUiState.Streaming)
        vm.cancel()
        channel.close()
        advanceUntilIdle()
        assertEquals(AiActionUiState.Idle, vm.state.value)
    }

    private fun sampleNote(id: String, content: String = "test") = Note(
        id = id,
        title = "",
        content = content,
        createdAt = 1_000L,
        updatedAt = 1_000L,
        isPinned = false,
        lastAiOp = null,
        lastAiAt = null
    )
}
