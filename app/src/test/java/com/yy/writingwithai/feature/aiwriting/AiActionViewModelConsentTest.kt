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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
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
 * M4-4 · AiActionViewModel consent 闸门单测。
 *
 * spec: openspec/changes/onboarding-consent/specs/ai-actions/spec.md
 * "AiActionViewModel gates AI calls behind user consent" + "未同意时 start() 失败" Scenario。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AiActionViewModelConsentTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `start() with not-consented yields Failed(UserConsentRequired), no gateway call`() = runTest {
        val consent = FakeConsentStore().apply { seed(ConsentState.EMPTY) }
        val apikey = FakeSecureApiKeyStore()
        val gateway = ThrowingAiGateway()
        val vm =
            AiActionViewModel(
                context = mockk<Context>(relaxed = true),
                aiGateway = gateway,
                noteRepository = mockk<NoteRepository>(relaxed = true),
                widgetUpdater = mockk<QuickNoteWidgetUpdater>(relaxed = true),
                consentStore = consent,
                secureApiKeyStore = apikey,
                promptTemplateStore = FakePromptTemplateStore(),
                providerPrefsStore = FakeProviderPrefsStore(initial = "deepseek"),
                userPrefsStore = FakeUserPrefsStore().apply { seed(true) }
            )

        vm.start(WritingOp.EXPAND, sourceText = "晨跑", noteId = "n1")
        val finalState = vm.state.value
        assertTrue(finalState is AiActionUiState.Failed) { "Expected Failed, got $finalState" }
        finalState as AiActionUiState.Failed
        assertEquals(AiError.UserConsentRequired, finalState.error)
        assertEquals(0, gateway.callCount, "AiGateway must not be called when consent is missing")
    }

    @Test
    fun `start() with consent and no apikey resolves to fake provider (M3 fallback)`() = runTest {
        val consent =
            FakeConsentStore().apply {
                seed(ConsentState(accepted = true, acceptedAt = 1L, version = 1))
            }
        val apikey = FakeSecureApiKeyStore() // 空
        val gateway = RecordingAiGateway()
        val vm =
            AiActionViewModel(
                context = mockk<Context>(relaxed = true),
                aiGateway = gateway,
                noteRepository = mockk<NoteRepository>(relaxed = true),
                widgetUpdater = mockk<QuickNoteWidgetUpdater>(relaxed = true),
                consentStore = consent,
                secureApiKeyStore = apikey,
                promptTemplateStore = FakePromptTemplateStore(),
                providerPrefsStore = FakeProviderPrefsStore(initial = "deepseek"),
                userPrefsStore = FakeUserPrefsStore().apply { seed(true) }
            )

        // remove-debug-fake-fallback §7.3:FakeAiProvider 不再注入,M3 fake fallback 行为作废。
        // 新行为:有 consent + 无 apikey → ProviderNotConfigured 错误,不会调 gateway。
        vm.start(WritingOp.EXPAND, sourceText = "晨跑", noteId = "n1")
        val finalState = vm.state.value
        assertTrue(finalState is AiActionUiState.Failed) { "Expected Failed, got $finalState" }
        finalState as AiActionUiState.Failed
        assertEquals(AiError.ProviderNotConfigured, finalState.error)
        assertEquals(null, gateway.lastProviderId, "AiGateway must not be called without apikey")
        assertEquals(0, gateway.callCount)
    }

    @Test
    fun `start() with consent and deepseek apikey resolves to deepseek provider`() = runTest {
        val consent =
            FakeConsentStore().apply {
                seed(ConsentState(accepted = true, acceptedAt = 1L, version = 1))
            }
        val apikey =
            FakeSecureApiKeyStore().apply {
                runBlocking { save("deepseek", "sk-real") }
            }
        // remove-debug-fake-fallback §7.3:走真 provider id("deepseek"),同 release 行为
        val providerPrefs = FakeProviderPrefsStore(initial = "deepseek")
        val gateway = RecordingAiGateway()
        val vm =
            AiActionViewModel(
                context = mockk<Context>(relaxed = true),
                aiGateway = gateway,
                noteRepository = mockk<NoteRepository>(relaxed = true),
                widgetUpdater = mockk<QuickNoteWidgetUpdater>(relaxed = true),
                consentStore = consent,
                secureApiKeyStore = apikey,
                promptTemplateStore = FakePromptTemplateStore(),
                providerPrefsStore = providerPrefs,
                userPrefsStore = FakeUserPrefsStore().apply { seed(true) }
            )

        vm.start(WritingOp.POLISH, sourceText = "test", noteId = "n2")
        assertEquals("deepseek", gateway.lastProviderId)
    }
}

/** 任何 stream 调用都抛，用来验证"根本没被调"。 */
private class ThrowingAiGateway : AiGateway {
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
        throw IllegalStateException("AiGateway must NOT be called when consent missing")
    }

    override suspend fun ping(
        providerId: String,
        apikey: String,
        modelName: String,
        apiFormatOverride: com.yy.writingwithai.core.ai.api.ApiFormat?
    ): String? = "test: ping not invoked in consent-missing path"
}

/** 记录最后一次调用的 providerId 并 emit 完整流。 */
private class RecordingAiGateway : AiGateway {
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
        return flowOf(
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
