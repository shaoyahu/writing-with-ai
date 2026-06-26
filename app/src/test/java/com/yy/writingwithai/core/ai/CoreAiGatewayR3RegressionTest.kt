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
 * H2:`ping()` 的契约是 `@return null 表示成功`,之前对 fake provider 抛
 *    IllegalStateException 打断契约。修后返回 `ProviderNotConfigured` 摘要。
 * H4:之前 `modelName ?: provider.supportedModels.firstOrNull() ?: "unknown"`
 *    把 "unknown" 字符串静默发到 provider,混淆 provider 真错 vs 配置错。
 *    修后两者都为空时 emit Failed(ProviderNotConfigured)。
 */
class CoreAiGatewayR3RegressionTest {

    /**
     * fix H2 regression:ping() 对 fake provider 不再抛 IllegalStateException,
     * 而是返回 ProviderNotConfigured 摘要,符合 `null == 成功` 契约。
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
     * fix H2 regression:ping() 在 fake 上不再抛 IllegalStateException,只是返回。
     * 此测试用 try-finally 显式断言"不抛"(通过直接调用 ping + 不 catch 即可)。
     */
    @Test
    fun ping_on_fake_provider_does_not_throw_exception() = runTest {
        val gateway = gatewayWithProviders(mapOf("fake" to FakeAiProvider()))

        // 不加 try-catch,若 IllegalStateException 漏出,runTest 会标红。
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
     * fix H4 regression:modelName == null 且 supportedModels 为空时,
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
     * fix H4 regression:modelName 已显式提供时,即使 supportedModels 为空也应走 user-supplied。
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

        // 应走到 provider.stream(用 user-supplied model),不是 Failed。
        assertEquals(1, emptyProvider.streamCallCount)
        assertEquals(emptyProvider.lastModel, "user-supplied-model")
        assertTrue(events.any { it is AiStreamEvent.Failed || it is AiStreamEvent.Started })
    }

    // ---- helpers ----

    private fun gatewayWithProviders(providers: Map<String, AiProvider>): AiGateway {
        val prefs = NoopProviderPrefsStore
        val customStore = EmptyCustomProviderStore
        // fix:测试中 provider.stream 成功时 CoreAiGateway.onCompletion 会调
        // historyRepo.get().record(),用 MockK mock 代替抛错的 noop。
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
            providerPrefsStore = prefs
        )
    }

    /** supportedModels == emptyList() 的 fake provider,用于 H4 测试。 */
    private class EmptyModelsProvider : AiProvider {
        override val id = "empty"
        override val displayName = "Empty"
        override val supportedModels: List<String> = emptyList()
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
    override fun observeSelectedModel(providerId: String): Flow<String?> = flowOf(null)
    override suspend fun getApiFormat(providerId: String): ApiFormat? = null
    override suspend fun setApiFormat(providerId: String, format: ApiFormat) {}
    override fun observeApiFormat(providerId: String): Flow<ApiFormat?> = flowOf(null)
}

/** 测试用 CustomProviderStore:永远空,不依赖 prefs 也不 fire listener。 */
internal object EmptyCustomProviderStore : CustomProviderStore {
    override suspend fun getAll(): List<ProviderConfig> = emptyList()
    override suspend fun getById(id: String): ProviderConfig? = null
    override suspend fun save(config: ProviderConfig) {}
    override suspend fun delete(id: String) {}
    override fun observeAll(): Flow<List<ProviderConfig>> = flowOf(emptyList())
    override fun addInvalidateListener(listener: (String) -> Unit) {}
    override fun removeInvalidateListener(listener: (String) -> Unit) {}
}
