package com.yy.writingwithai.feature.aiwriting

import android.content.Context
import com.yy.writingwithai.core.ai.api.AiError
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
import com.yy.writingwithai.feature.aiwriting.streaming.AiActionUiState
import com.yy.writingwithai.feature.aiwriting.streaming.AiActionViewModel
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * onboarding-apikey-prompt · AiActionViewModel apikey 闸门单测。
 *
 * spec: openspec/changes/onboarding-apikey-prompt/tasks.md §7.3
 * "AI 入口拦截测试(扩写 ViewModel):ackApikeyPrompt=false 时不发起 AI 调用、弹 dialog 信号"
 *
 * 与 [AiActionViewModelConsentTest] 镜像:consent 通过 + apikey-ack=false → 阻断。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AiActionViewModelApikeyPromptTest {
    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `start() with consent but ack=false yields Failed(ApikeyPromptNotAcked), no gateway call`() = runTest {
        val consent =
            FakeConsentStore().apply {
                seed(ConsentState(accepted = true, acceptedAt = 1L, version = 1))
            }
        val userPrefs = FakeUserPrefsStore() // 默认 ack=false
        val gateway = ThrowingAiGatewayForAckTest()
        val vm =
            AiActionViewModel(
                context = mockk<Context>(relaxed = true),
                aiGateway = gateway,
                noteRepository = mockk<NoteRepository>(relaxed = true),
                widgetUpdater = mockk<QuickNoteWidgetUpdater>(relaxed = true),
                consentStore = consent,
                secureApiKeyStore = FakeSecureApiKeyStore(),
                promptTemplateStore = FakePromptTemplateStore(),
                providerPrefsStore = FakeProviderPrefsStore(initial = "fake"),
                userPrefsStore = userPrefs
            )

        vm.start(WritingOp.EXPAND, sourceText = "晨跑", noteId = "n1")
        val finalState = vm.state.value
        assertTrue(finalState is AiActionUiState.Failed) { "Expected Failed, got $finalState" }
        finalState as AiActionUiState.Failed
        assertEquals(AiError.ApikeyPromptNotAcked, finalState.error)
        assertEquals(0, gateway.callCount, "AiGateway must not be called when ack=false")
    }

    @Test
    fun `start() with consent AND ack=true proceeds to gateway call (gate fully open)`() = runTest {
        val consent =
            FakeConsentStore().apply {
                seed(ConsentState(accepted = true, acceptedAt = 1L, version = 1))
            }
        val userPrefs = FakeUserPrefsStore().apply { seed(true) }
        val gateway = RecordingAiGatewayForAckTest()
        val vm =
            AiActionViewModel(
                context = mockk<Context>(relaxed = true),
                aiGateway = gateway,
                noteRepository = mockk<NoteRepository>(relaxed = true),
                widgetUpdater = mockk<QuickNoteWidgetUpdater>(relaxed = true),
                consentStore = consent,
                secureApiKeyStore = FakeSecureApiKeyStore(),
                promptTemplateStore = FakePromptTemplateStore(),
                providerPrefsStore = FakeProviderPrefsStore(initial = "fake"),
                userPrefsStore = userPrefs
            )

        vm.start(WritingOp.EXPAND, sourceText = "晨跑", noteId = "n1")
        assertEquals("fake", gateway.lastProviderId)
    }
}

/** 任何 stream 调用都抛，用来验证"根本没被调"。 */
private class ThrowingAiGatewayForAckTest : AiGateway {
    var callCount: Int = 0

    override suspend fun listProviders(): List<com.yy.writingwithai.core.ai.api.ProviderDescriptor> = emptyList()

    override suspend fun streamWritingOp(
        op: WritingOp,
        sourceText: String,
        providerId: String,
        apikey: String,
        modelName: String?,
        systemPrompt: String?,
        apiFormatOverride: com.yy.writingwithai.core.ai.api.ApiFormat?
    ): kotlinx.coroutines.flow.Flow<AiStreamEvent> {
        callCount++
        throw IllegalStateException("AiGateway must NOT be called when ack=false")
    }

    override suspend fun ping(
        providerId: String,
        apikey: String,
        modelName: String,
        apiFormatOverride: com.yy.writingwithai.core.ai.api.ApiFormat?
    ): String? = null
}

/** 记录最后一次调用的 providerId 并 emit 完整流。 */
private class RecordingAiGatewayForAckTest : AiGateway {
    var callCount: Int = 0
    var lastProviderId: String? = null

    override suspend fun listProviders(): List<com.yy.writingwithai.core.ai.api.ProviderDescriptor> = emptyList()

    override suspend fun streamWritingOp(
        op: WritingOp,
        sourceText: String,
        providerId: String,
        apikey: String,
        modelName: String?,
        systemPrompt: String?,
        apiFormatOverride: com.yy.writingwithai.core.ai.api.ApiFormat?
    ): kotlinx.coroutines.flow.Flow<AiStreamEvent> {
        callCount++
        lastProviderId = providerId
        return kotlinx.coroutines.flow.flowOf(
            AiStreamEvent.Started,
            AiStreamEvent.Delta("测试结果"),
            AiStreamEvent.Usage(inputTokens = 1, outputTokens = 1, totalTokens = 2),
            AiStreamEvent.Done
        )
    }

    override suspend fun ping(
        providerId: String,
        apikey: String,
        modelName: String,
        apiFormatOverride: com.yy.writingwithai.core.ai.api.ApiFormat?
    ): String? = null
}
