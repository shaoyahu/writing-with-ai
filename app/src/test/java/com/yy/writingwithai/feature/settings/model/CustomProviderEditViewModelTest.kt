package com.yy.writingwithai.feature.settings.model

import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.core.ai.api.AiGateway
import com.yy.writingwithai.core.ai.api.ApiFormat
import com.yy.writingwithai.core.ai.provider.AuthStyle
import com.yy.writingwithai.core.ai.provider.CustomProviderStore
import com.yy.writingwithai.core.ai.provider.ProviderConfig
import com.yy.writingwithai.core.ai.provider.ProviderPrefsStore
import com.yy.writingwithai.core.prefs.SecureApiKeyStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * custom-provider-api-format · Custom Provider 编辑表单 VM 单测。
 *
 * 覆盖:
 * - `onApiFormatChanged` 更新 state
 * - `loadExisting` 回填 apiFormat(OPENAI 配置加载后 state.apiFormat = OPENAI)
 * - `buildConfig` 选 OPENAI / ANTHROPIC → 返回 ProviderConfig.apiFormat 与之对应;
 *   baseUrl 直用,不追加 path(endpointPath 永远 = "")
 * - `save` 走的 config 含 OPENAI(apiFormat 通过 buildConfig 透传到 store.save)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CustomProviderEditViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: CustomProviderEditViewModel
    private lateinit var customProviderStore: CustomProviderStore
    private lateinit var secureApiKeyStore: SecureApiKeyStore
    private lateinit var providerPrefsStore: ProviderPrefsStore
    private lateinit var aiGateway: AiGateway

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>()) } returns 0

        customProviderStore = mockk(relaxed = true)
        secureApiKeyStore = mockk(relaxed = true)
        providerPrefsStore = mockk(relaxed = true)
        aiGateway = mockk(relaxed = true)
        every { customProviderStore.observeAll() } returns flowOf(emptyList())
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
     * custom-provider-api-format · 协议下拉变更 → state.apiFormat。
     */
    @Test
    fun onApiFormatChanged_updatesState() = runTest(dispatcher) {
        viewModel = newViewModel()
        assertEquals(ApiFormat.ANTHROPIC, viewModel.state.value.apiFormat)

        viewModel.onApiFormatChanged(ApiFormat.OPENAI)
        assertEquals(ApiFormat.OPENAI, viewModel.state.value.apiFormat)

        viewModel.onApiFormatChanged(ApiFormat.ANTHROPIC)
        assertEquals(ApiFormat.ANTHROPIC, viewModel.state.value.apiFormat)

        viewModel.viewModelScope.cancel()
        advanceUntilIdle()
    }

    /**
     * custom-provider-api-format · 加载已存 OPENAI 配置 → state.apiFormat 回填。
     */
    @Test
    fun loadExisting_openaiConfig_fillsApiFormatInState() = runTest(dispatcher) {
        val openaiConfig = newConfig(
            id = "deepseek-custom",
            baseUrl = "https://api.deepseek.com/v1/chat/completions",
            apiFormat = ApiFormat.OPENAI
        )
        coEvery { customProviderStore.getById("deepseek-custom") } returns openaiConfig
        coEvery { secureApiKeyStore.get("deepseek-custom") } returns "sk-test"
        viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.loadExisting("deepseek-custom")
        advanceUntilIdle()

        val s = viewModel.state.value
        assertEquals(ApiFormat.OPENAI, s.apiFormat)
        assertEquals("https://api.deepseek.com/v1/chat/completions", s.baseUrl)
        viewModel.viewModelScope.cancel()
        advanceUntilIdle()
    }

    /**
     * custom-provider-api-format · `buildConfig` 选 OPENAI → ProviderConfig.apiFormat = OPENAI,
     * endpointPath 自动拼 `/chat/completions`。
     */
    @Test
    fun buildConfig_openai_returnsConfigWithOpenaiAndChatCompletionsPath() = runTest(dispatcher) {
        viewModel = newViewModel()
        val s = CustomProviderEditUiState(
            displayName = "DeepSeek",
            providerId = "deepseek-custom",
            baseUrl = "https://api.deepseek.com",
            apiFormat = ApiFormat.OPENAI,
            authStyle = AuthStyle.AUTHORIZATION,
            defaultModel = "deepseek-chat",
            supportedModels = listOf("deepseek-chat")
        )
        val cfg = viewModel.buildConfig(s)
        assertNotNull(cfg)
        assertEquals(ApiFormat.OPENAI, cfg!!.apiFormat)
        assertEquals("/chat/completions", cfg.endpointPath)
        assertEquals("https://api.deepseek.com", cfg.baseUrl)
        // 最终 URL = baseUrl + endpointPath = "https://api.deepseek.com/chat/completions"
        assertEquals("https://api.deepseek.com/chat/completions", cfg.baseUrl + cfg.endpointPath)
        viewModel.viewModelScope.cancel()
        advanceUntilIdle()
    }

    /**
     * custom-provider-api-format · `buildConfig` 选 ANTHROPIC → ProviderConfig.apiFormat = ANTHROPIC,
     * endpointPath 自动拼 `/v1/messages`(DeepSeek / Minimax 等"base URL 含 /anthropic"模式)。
     */
    @Test
    fun buildConfig_anthropic_returnsConfigWithAnthropicAndMessagesPath() = runTest(dispatcher) {
        viewModel = newViewModel()
        val s = CustomProviderEditUiState(
            displayName = "DeepSeek",
            providerId = "deepseek-custom",
            baseUrl = "https://api.deepseek.com/anthropic",
            apiFormat = ApiFormat.ANTHROPIC,
            authStyle = AuthStyle.AUTHORIZATION,
            defaultModel = "deepseek-v4-flash",
            supportedModels = listOf("deepseek-v4-flash")
        )
        val cfg = viewModel.buildConfig(s)
        assertNotNull(cfg)
        assertEquals(ApiFormat.ANTHROPIC, cfg!!.apiFormat)
        assertEquals("/v1/messages", cfg.endpointPath)
        assertEquals("https://api.deepseek.com/anthropic", cfg.baseUrl)
        // 最终 URL = baseUrl + endpointPath = "https://api.deepseek.com/anthropic/v1/messages"
        assertEquals("https://api.deepseek.com/anthropic/v1/messages", cfg.baseUrl + cfg.endpointPath)
        viewModel.viewModelScope.cancel()
        advanceUntilIdle()
    }

    /**
     * custom-provider-api-format · baseUrl 末尾 `/` 被剥,避免拼成双斜杠。
     */
    @Test
    fun buildConfig_anthropic_trailingSlashStripped() = runTest(dispatcher) {
        viewModel = newViewModel()
        val s = CustomProviderEditUiState(
            displayName = "DeepSeek",
            providerId = "deepseek-custom",
            baseUrl = "https://api.deepseek.com/anthropic/",
            apiFormat = ApiFormat.ANTHROPIC,
            authStyle = AuthStyle.AUTHORIZATION,
            defaultModel = "deepseek-v4-flash",
            supportedModels = listOf("deepseek-v4-flash")
        )
        val cfg = viewModel.buildConfig(s)
        assertNotNull(cfg)
        assertEquals("https://api.deepseek.com/anthropic", cfg!!.baseUrl)
        // 拼出路径无双斜杠
        assertEquals("https://api.deepseek.com/anthropic/v1/messages", cfg.baseUrl + cfg.endpointPath)
        viewModel.viewModelScope.cancel()
        advanceUntilIdle()
    }

    /**
     * custom-provider-api-format · `save` 走的 config 含 OPENAI(apiFormat 通过 buildConfig
     * 透传到 store.save)。
     */
    @Test
    fun save_openaiConfig_passesOpenaiApiFormatToStore() = runTest(dispatcher) {
        coEvery { customProviderStore.save(any()) } returns Unit
        coEvery { secureApiKeyStore.save(any(), any()) } returns Unit
        coEvery { secureApiKeyStore.has(any()) } returns false
        coEvery { providerPrefsStore.setSelectedProviderId(any()) } returns Unit
        viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.onDisplayNameChanged("My DeepSeek")
        viewModel.onProviderIdChanged("my-deepseek")
        viewModel.onBaseUrlChanged("https://api.deepseek.com")
        viewModel.onApiFormatChanged(ApiFormat.OPENAI)
        viewModel.onApiKeyChanged("sk-test")
        viewModel.onDefaultModelChanged("deepseek-chat")
        advanceUntilIdle()

        viewModel.save()
        advanceUntilIdle()

        coVerify {
            customProviderStore.save(
                match {
                    it.apiFormat == ApiFormat.OPENAI &&
                        it.endpointPath == "/chat/completions" &&
                        it.baseUrl == "https://api.deepseek.com" &&
                        it.id == "my-deepseek"
                }
            )
        }
        viewModel.viewModelScope.cancel()
        advanceUntilIdle()
    }

    private fun newViewModel(): CustomProviderEditViewModel = CustomProviderEditViewModel(
        customProviderStore = customProviderStore,
        secureApiKeyStore = secureApiKeyStore,
        providerPrefsStore = providerPrefsStore,
        okHttpClient = OkHttpClient(),
        coreAiGateway = aiGateway
    )

    private fun newConfig(id: String, baseUrl: String, apiFormat: ApiFormat): ProviderConfig = ProviderConfig(
        id = id,
        displayName = id,
        baseUrl = baseUrl,
        endpointPath = "",
        authStyle = AuthStyle.AUTHORIZATION,
        defaultModel = "m",
        supportedModels = listOf("m"),
        apiFormat = apiFormat
    )
}
