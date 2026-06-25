package com.yy.writingwithai.core.ai

import com.yy.writingwithai.core.ai.api.AiCredentials
import com.yy.writingwithai.core.ai.api.AiError
import com.yy.writingwithai.core.ai.api.AiGateway
import com.yy.writingwithai.core.ai.api.AiProvider
import com.yy.writingwithai.core.ai.api.AiRequest
import com.yy.writingwithai.core.ai.api.AiStreamEvent
import com.yy.writingwithai.core.ai.api.ApiFormat
import com.yy.writingwithai.core.ai.api.ProviderDescriptor
import com.yy.writingwithai.core.ai.api.WritingOp
import com.yy.writingwithai.core.ai.provider.AnthropicCompatibleAdapter
import com.yy.writingwithai.core.ai.provider.CustomProviderStore
import com.yy.writingwithai.core.ai.provider.ProviderPrefsStore
import com.yy.writingwithai.core.data.repo.AiHistoryRepository
import dagger.Lazy
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import okhttp3.OkHttpClient

/**
 * [AiGateway] 唯一实现。
 *
 * - 根据 [providerId] 先从 Hilt 注入的 `Map<String, AiProvider>` 取内置 provider
 * - 未命中时查 [CustomProviderStore] 动态构造 [AnthropicCompatibleAdapter]
 * - adapter 缓存(ConcurrentHashMap)避免每次调用重建 OkHttp / SSE parser
 * - 订阅 store 的 onInvalidate 回调,save/delete 时清缓存,避免 stale config
 * - 委托 `AiProvider.stream()` 发起调用,流式返回 [AiStreamEvent]
 * - 每次调用(成功/失败)通过 `onCompletion` 自动落 [AiHistoryEntity]
 * - 超时/IO 异常由 provider 内部处理并 emit [AiStreamEvent.Failed]
 *
 * M6 custom-model: 动态支持用户自定义 provider。
 */
@Singleton
class CoreAiGateway
@Inject
constructor(
    private val providers: Map<String, @JvmSuppressWildcards AiProvider>,
    private val customProviderStore: CustomProviderStore,
    @Named("ai") private val okHttpClient: OkHttpClient,
    private val historyRepo: Lazy<AiHistoryRepository>,
    private val providerPrefsStore: ProviderPrefsStore
) : AiGateway {
    /** 动态 adapter 缓存:providerId → AnthropicCompatibleAdapter。 */
    private val customAdapterCache = ConcurrentHashMap<String, AnthropicCompatibleAdapter>()

    // review r1 M1:用 listener 模式替代直接 `onInvalidate = { ... }` 赋值,
    // 避免 Hilt 多次创建 CoreAiGateway 时 lambda 互相覆盖导致回调静默失效。
    private val customInvalidateListener: (String) -> Unit = { id ->
        if (id.isBlank()) {
            customAdapterCache.clear()
        } else {
            customAdapterCache.remove(id)
        }
    }

    init {
        customProviderStore.addInvalidateListener(customInvalidateListener)
    }

    override suspend fun listProviders(): List<ProviderDescriptor> {
        val builtin = providers
            .map { (key, provider) ->
                ProviderDescriptor(
                    id = provider.id,
                    displayName = provider.displayName,
                    models = provider.supportedModels,
                    isConfigured = true
                )
            }
            .distinctBy { it.id }

        val custom = customProviderStore.getAll().map { config ->
            ProviderDescriptor(
                id = config.id,
                displayName = config.displayName,
                models = config.supportedModels,
                isConfigured = true
            )
        }
        return builtin + custom
    }

    /** 按 providerId 取 AiProvider(内置优先,未命中查自定义)。suspend,无 runBlocking。 */
    private suspend fun resolveProvider(providerId: String): AiProvider? {
        providers[providerId]?.let { return it }
        customAdapterCache[providerId]?.let { return it }
        val config = customProviderStore.getById(providerId) ?: return null
        return AnthropicCompatibleAdapter(config, okHttpClient).also {
            customAdapterCache[providerId] = it
        }
    }

    override suspend fun streamWritingOp(
        op: WritingOp,
        sourceText: String,
        providerId: String,
        apikey: String,
        modelName: String?,
        systemPrompt: String?,
        apiFormatOverride: ApiFormat?
    ): Flow<AiStreamEvent> {
        val provider = resolveProvider(providerId)
            ?: return flowOf(
                AiStreamEvent.Failed(
                    AiError.Unknown(null, "provider $providerId not found"),
                    false
                )
            )

        val model = modelName ?: provider.supportedModels.firstOrNull() ?: "unknown"
        // H1 修:`apiFormatOverride` 由 caller(Vm) 在 suspend 上下文读 prefs 后传入,
        // 删原 `runBlocking { providerPrefsStore.getApiFormat(providerId) }`(主线程 ANR)。
        val request = AiRequest(op, sourceText, model, systemPrompt, apiFormatOverride)
        val credentials = AiCredentials(apikey = apikey)

        val startTime = System.currentTimeMillis()
        val outputBuilder = StringBuilder()
        var lastUsage: AiStreamEvent.Usage? = null
        var lastError: AiError? = null

        return provider.stream(request, credentials)
            .onEach { event ->
                when (event) {
                    is AiStreamEvent.Delta -> outputBuilder.append(event.text)
                    is AiStreamEvent.Usage -> lastUsage = event
                    is AiStreamEvent.Failed -> lastError = event.error
                    else -> Unit
                }
            }
            .onCompletion { cause ->
                // Polish: cause 优先于 lastError(Failed event 后流又抛异常的少见情况)
                val errorMsg = if (cause == null) {
                    lastError?.summary()
                } else {
                    lastError?.summary()?.let { "$it; ${cause::class.simpleName}: ${cause.message}" }
                        ?: "${cause::class.simpleName}: ${cause.message}"
                }
                val duration = System.currentTimeMillis() - startTime
                historyRepo.get().record(
                    noteId = null,
                    providerId = providerId,
                    model = model,
                    op = op.name.lowercase(),
                    inputTokens = lastUsage?.inputTokens ?: (sourceText.length / 2),
                    outputTokens = lastUsage?.outputTokens ?: outputBuilder.length,
                    totalTokens =
                    lastUsage?.totalTokens
                        ?: (sourceText.length / 2 + outputBuilder.length),
                    durationMs = duration,
                    createdAt = System.currentTimeMillis(),
                    inputSnapshot = sourceText,
                    outputSnapshot = outputBuilder.toString(),
                    error = errorMsg
                )
            }
    }

    override suspend fun ping(
        providerId: String,
        apikey: String,
        modelName: String,
        apiFormatOverride: ApiFormat?
    ): String? {
        val provider = resolveProvider(providerId) ?: return "provider $providerId not registered"
        if (provider.id == "fake") {
            // review r1 M5:fake provider 不应被静默返 null(看上去像"成功"),改抛 AiError.ProviderNotConfigured
            // 让 UI 进入"未配置"分支,引导用户到设置 → 模型管理配真实 apikey。
            throw IllegalStateException(AiError.ProviderNotConfigured.summary())
        }
        val effectiveModel = provider.supportedModels.firstOrNull() ?: modelName
        // X 方案:ping 也走用户选的 apiFormat,endpoint 跟着切。
        // H1 修:ping 是 suspend,可在函数顶部 await providerPrefsStore.getApiFormat 而无需 runBlocking。
        val effectiveApiFormat = apiFormatOverride ?: providerPrefsStore.getApiFormat(providerId)
        var failureReason: String? = null
        try {
            provider
                .stream(
                    AiRequest(WritingOp.EXPAND, "ping", effectiveModel, apiFormatOverride = effectiveApiFormat),
                    AiCredentials(apikey)
                ).collect { event ->
                    if (event is AiStreamEvent.Failed) {
                        failureReason = event.error.summary()
                    }
                }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            failureReason = e.message ?: e::class.simpleName ?: "unknown exception"
        }
        return failureReason
    }
}
