package com.yy.writingwithai.core.ai

import com.yy.writingwithai.core.ai.api.AiError
import com.yy.writingwithai.core.ai.api.AiGateway
import com.yy.writingwithai.core.ai.api.AiProvider
import com.yy.writingwithai.core.ai.api.AiRequest
import com.yy.writingwithai.core.ai.api.AiStreamEvent
import com.yy.writingwithai.core.ai.api.ApiFormat
import com.yy.writingwithai.core.ai.api.WritingOp
import com.yy.writingwithai.core.ai.fake.FakeAiProvider
import com.yy.writingwithai.core.ai.provider.CustomProviderStore
import com.yy.writingwithai.core.ai.provider.ProviderConfig
import com.yy.writingwithai.core.ai.provider.ProviderPrefsStore
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * fix-review-r3-high · [CoreAiGateway] 契约 + 空配置回归测试。
 *
 * H2:`ping()` 的契约是 `@return null 表示成功`，之前对 fake provider 抛
 *    IllegalStateException 打断契约。修后返回 `ProviderNotConfigured` 摘要。
 * H4:之前 `modelName ?: provider.supportedModels.firstOrNull() ?: "unknown"`
 *    把 "unknown" 字符串静默发到 provider，混淆 provider 真错 vs 配置错。
 *    修后两者都为空时 emit Failed(ProviderNotConfigured)。
 */
class CoreAiGatewayR3RegressionTest {

    /**
     * fix H2 regression:ping() 对 fake provider 不再抛 IllegalStateException,
     * 而是返回 ProviderNotConfigured 摘要，符合 `null == 成功` 契约。
     */
    @Test
    fun ping_on_fake_provider_returns_provider_not_configured_summary_not_throw() = runTest {
        val gateway = gatewayWithProviders(mapOf("fake" to FakeAiProvider()))

        val result = gateway.ping(
            providerId = "fake",
            apikey = "any",
            modelName = "fake-model"
        )

        // 契约:非 null = 失败原因(给 UI 显示)。
        assertNotNull(result)
        assertEquals(AiError.ProviderNotConfigured.summary(), result)
    }

    /**
     * fix H2 regression:ping() 在 fake 上不再抛 IllegalStateException，只是返回。
     * 此测试用 try-finally 显式断言"不抛"(通过直接调用 ping + 不 catch 即可)。
     */
    @Test
    fun ping_on_fake_provider_does_not_throw_exception() = runTest {
        val gateway = gatewayWithProviders(mapOf("fake" to FakeAiProvider()))

        // 不加 try-catch，若 IllegalStateException 漏出，runTest 会标红。
        val result = gateway.ping(
            providerId = "fake",
            apikey = "any",
            modelName = "fake-model"
        )
        assertEquals(AiError.ProviderNotConfigured.summary(), result)
    }

    /**
     * fix H2 regression:ping() 对未注册的 providerId 仍返 "not registered"。
     * 验证 H2 改动未影响其他失败路径。
     */
    @Test
    fun ping_on_unknown_provider_returns_not_registered_message() = runTest {
        val gateway = gatewayWithProviders(emptyMap())

        val result = gateway.ping(
            providerId = "nonexistent",
            apikey = "any",
            modelName = "model"
        )

        assertNotNull(result)
        assertTrue(result!!.contains("not registered"))
    }

    /**
     * fix H4 regression:modelName == null 且 supportedModels 为空时，
     * 不再把 "unknown" 字符串发给 provider;直接 emit Failed(ProviderNotConfigured)。
     */
    @Test
    fun stream_with_empty_models_emits_provider_not_configured_failed() = runTest {
        val emptyProvider = EmptyModelsProvider()
        val gateway = gatewayWithProviders(mapOf("empty" to emptyProvider))

        val events = gateway.streamWritingOp(
            op = WritingOp.EXPAND,
            sourceText = "hello",
            providerId = "empty",
            apikey = "any",
            modelName = null
        ).toList()

        val failed = events.filterIsInstance<AiStreamEvent.Failed>().firstOrNull()
        assertNotNull(failed)
        assertEquals(AiError.ProviderNotConfigured, failed!!.error)
        // 空模型的 provider.stream 不应被调用
        assertEquals(0, emptyProvider.streamCallCount)
    }

    /**
     * fix H4 regression:modelName 已显式提供时，即使 supportedModels 为空也应走 user-supplied。
     */
    @Test
    fun stream_with_explicit_model_name_uses_it_even_when_supported_empty() = runTest {
        val emptyProvider = EmptyModelsProvider()
        val gateway = gatewayWithProviders(mapOf("empty" to emptyProvider))

        val events = gateway.streamWritingOp(
            op = WritingOp.EXPAND,
            sourceText = "hello",
            providerId = "empty",
            apikey = "any",
            modelName = "user-supplied-model"
        ).toList()

        // 应走到 provider.stream(用 user-supplied model)，不是 Failed。
        assertEquals(1, emptyProvider.streamCallCount)
        assertEquals(emptyProvider.lastModel, "user-supplied-model")
        assertTrue(events.any { it is AiStreamEvent.Failed || it is AiStreamEvent.Started })
    }

    /**
     * fix-2026-06-28-ai-model-selection-actually-used 5.1:modelName=null + provider 显式
     * 声明 `defaultModel="X"` → fallback 走 defaultModel，不走 `supportedModels[0]`。
     * 这正是 change 标题"选 pro 实际用 flash"要修的语义。`supportedModels` 故意把
     * "list-first" 放最前，如果 gateway 还在用 firstOrNull fallback 就会发 list-first,
     * 测出来 fail。
     */
    @Test
    fun stream_with_null_modelName_falls_back_to_defaultModel_not_supported_first() = runTest {
        val provider = DefaultModelProvider(
            id = "deepseek",
            supportedModels = listOf("deepseek-v4-flash", "deepseek-v4-pro"),
            defaultModel = "deepseek-v4-pro"
        )
        val gateway = gatewayWithProviders(mapOf("deepseek" to provider))

        val events = gateway.streamWritingOp(
            op = WritingOp.EXPAND,
            sourceText = "hi",
            providerId = "deepseek",
            apikey = "k",
            modelName = null
        ).toList()

        // 走 defaultModel("deepseek-v4-pro")，不是 supportedModels[0]("deepseek-v4-flash")。
        assertEquals(1, provider.streamCallCount)
        assertEquals("deepseek-v4-pro", provider.lastModel)
        assertTrue(events.any { it is AiStreamEvent.Started || it is AiStreamEvent.Done })
    }

    /**
     * fix-2026-06-28-ai-model-selection-actually-used 5.2:modelName=null + provider
     * `defaultModel == ""`(啥都没声明)→ emit Failed(ProviderNotConfigured),
     * `provider.stream` 0 次调用(不静默发 "unknown" 字面量)。
     */
    @Test
    fun stream_with_null_modelName_and_blank_defaultModel_emits_provider_not_configured() = runTest {
        val provider = DefaultModelProvider(
            id = "blank-default",
            supportedModels = listOf("a", "b"),
            defaultModel = ""
        )
        val gateway = gatewayWithProviders(mapOf("blank-default" to provider))

        val events = gateway.streamWritingOp(
            op = WritingOp.EXPAND,
            sourceText = "hi",
            providerId = "blank-default",
            apikey = "k",
            modelName = null
        ).toList()

        val failed = events.filterIsInstance<AiStreamEvent.Failed>().firstOrNull()
        assertNotNull(failed)
        assertEquals(AiError.ProviderNotConfigured, failed!!.error)
        assertEquals(0, provider.streamCallCount)
    }

    // ---- helpers ----

    private fun gatewayWithProviders(providers: Map<String, AiProvider>): AiGateway {
        val prefs = NoopProviderPrefsStore
        val customStore = EmptyCustomProviderStore
        // fix:测试中 provider.stream 成功时 CoreAiGateway.onCompletion 会调
        // historyRepo.get().record()，用 MockK mock 代替抛错的 noop。
        val noopHistoryRepo = mockk<com.yy.writingwithai.core.data.repo.AiHistoryRepository>(
            relaxed = true
        )
        val historyLazy = object : dagger.Lazy<com.yy.writingwithai.core.data.repo.AiHistoryRepository> {
            override fun get() = noopHistoryRepo
        }
        return CoreAiGateway(
            providers = providers,
            customProviderStore = customStore,
            okHttpClient = OkHttpClient(),
            historyRepo = historyLazy,
            providerPrefsStore = prefs,
            // fix-2026-06-30-full-review-r1 H10:CoreAiGateway 加 ConsentStore 门控，
            // 测试里 mock 为"已同意"放行。
            consentStore = mockk(relaxed = true) {
                io.mockk.coEvery { isConsented(any()) } returns true
            }
        )
    }

    /** supportedModels == emptyList() 的 fake provider，用于 H4 测试。 */
    private class EmptyModelsProvider : AiProvider {
        override val id = "empty"
        override val displayName = "Empty"
        override val supportedModels: List<String> = emptyList()

        // fix-2026-06-28-ai-model-selection-actually-used:H4 场景默认 defaultModel
        // 也空(模拟"provider 啥都没声明")，走 gateway fallback → Failed(ProviderNotConfigured)。
        // 想测 fallback 命中 defaultModel 的，新加 [DefaultModelProvider]。
        override val defaultModel: String = ""
        var streamCallCount = 0
        var lastModel: String? = null

        override fun stream(
            request: AiRequest,
            credentials: com.yy.writingwithai.core.ai.api.AiCredentials
        ): Flow<AiStreamEvent> {
            streamCallCount++
            lastModel = request.model
            return flowOf(AiStreamEvent.Started, AiStreamEvent.Done)
        }
    }

    /**
     * fix-2026-06-28-ai-model-selection-actually-used:支持显式声明 defaultModel 的 fake
     * provider。`id` / `supportedModels` / `defaultModel` 全部外部传入，方便 5.1/5.2
     * 不同组合验证 fallback 行为。
     */
    private class DefaultModelProvider(
        override val id: String,
        override val supportedModels: List<String>,
        override val defaultModel: String
    ) : AiProvider {
        override val displayName: String = "DefaultModelProvider"
        var streamCallCount = 0
        var lastModel: String? = null

        override fun stream(
            request: AiRequest,
            credentials: com.yy.writingwithai.core.ai.api.AiCredentials
        ): Flow<AiStreamEvent> {
            streamCallCount++
            lastModel = request.model
            return flowOf(AiStreamEvent.Started, AiStreamEvent.Done)
        }
    }
}

/** 测试用 ProviderPrefsStore:不需要 read prefs,H4 测试不依赖。 */
internal object NoopProviderPrefsStore : ProviderPrefsStore {
    override suspend fun getSelectedProviderId(): String? = null
    override suspend fun setSelectedProviderId(providerId: String) {}
    override fun observeSelectedProviderId(): Flow<String?> = flowOf(null)
    override suspend fun getSelectedModel(providerId: String): String? = null
    override suspend fun setSelectedModel(providerId: String, model: String) {}

    // fix-2026-06-28-ai-model-selection-actually-used:补新接口方法，noop 默认 no-op。
    override suspend fun setSelectedModelIfMissing(providerId: String, defaultModel: String) {}
    override fun observeSelectedModel(providerId: String): Flow<String?> = flowOf(null)
    override suspend fun getApiFormat(providerId: String): ApiFormat? = null
    override suspend fun setApiFormat(providerId: String, format: ApiFormat) {}
    override fun observeApiFormat(providerId: String): Flow<ApiFormat?> = flowOf(null)
}

/** 测试用 CustomProviderStore:永远空，不依赖 prefs 也不 fire listener。 */
internal object EmptyCustomProviderStore : CustomProviderStore {
    override suspend fun getAll(): List<ProviderConfig> = emptyList()
    override suspend fun getById(id: String): ProviderConfig? = null
    override suspend fun save(config: ProviderConfig) {}
    override suspend fun delete(id: String) {}
    override fun observeAll(): Flow<List<ProviderConfig>> = flowOf(emptyList())
    override fun addInvalidateListener(listener: (String) -> Unit) {}
    override fun removeInvalidateListener(listener: (String) -> Unit) {}
}
