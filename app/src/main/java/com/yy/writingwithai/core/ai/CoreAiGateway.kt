package com.yy.writingwithai.core.ai

import com.yy.writingwithai.BuildConfig
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
import com.yy.writingwithai.core.prefs.ConsentStore
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
 * - 订阅 store 的 onInvalidate 回调，save/delete 时清缓存，避免 stale config
 * - 委托 `AiProvider.stream()` 发起调用，流式返回 [AiStreamEvent]
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
    private val providerPrefsStore: ProviderPrefsStore,
    private val consentStore: ConsentStore
) : AiGateway {
    /** 动态 adapter 缓存:providerId → AnthropicCompatibleAdapter。 */
    private val customAdapterCache = ConcurrentHashMap<String, AnthropicCompatibleAdapter>()

    // review r1 M1:用 listener 模式替代直接 `onInvalidate = { ... }` 赋值，
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
        // fix-review-r3-medium M7:原版 builtin.distinctBy 后直接 + custom，如果用户在
        // CustomProviderStore 存了一个 id 跟内置 provider 撞的(custom 落到 builtin 之后),
        // UI 会看到两条同 id 的 ProviderDescriptor，后续 onClick 不知道选哪个。改成在合并后
        // 一次性 distinctBy,custom 优先级更高(用户配置覆盖)。
        val builtin = providers.map { (_, provider) ->
            ProviderDescriptor(
                id = provider.id,
                displayName = provider.displayName,
                models = provider.supportedModels,
                isConfigured = true,
                // fix-2026-06-28-ai-model-selection-actually-used:把 provider 的 defaultModel
                // 透出给 UI，卡片据此显示「实际将调用」行(消除「选 pro 实际调 flash」歧义)。
                defaultModel = provider.defaultModel
            )
        }
        val custom = customProviderStore.getAll().map { config ->
            ProviderDescriptor(
                id = config.id,
                displayName = config.displayName,
                models = config.supportedModels,
                isConfigured = true,
                defaultModel = config.defaultModel
            )
        }
        return (custom + builtin).distinctBy { it.id }
    }

    /** 按 providerId 取 AiProvider(内置优先，未命中查自定义)。suspend，无 runBlocking。
     * fix-full-review:用 computeIfAbsent 替代 get+put，消除 TOCTOU 竞态(两个并发调用
     * 可能同时构造同一 adapter)。computeIfAbsent 保证 mappingFunction 只执行一次。
     */
    private suspend fun resolveProvider(providerId: String): AiProvider? {
        providers[providerId]?.let { return it }
        customAdapterCache[providerId]?.let { return it }
        val config = customProviderStore.getById(providerId) ?: return null
        // computeIfAbsent 的 mappingFunction 是同步的，但构造 AnthropicCompatibleAdapter
        // 本身也是同步的(只赋值字段)，所以这里安全。
        return customAdapterCache.computeIfAbsent(providerId) {
            AnthropicCompatibleAdapter(config, okHttpClient)
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
        // fix-2026-06-30-full-review-r1 HIGH H10:在 gateway 入口加 consent 门控。
        // 项目 CLAUDE.md 规定"首次 AI 调用必须有用户同意",Gateway 是 AI 调用的
        // 单一抽象层(项目规则)，应作为强制执行点，防 deep link / 进程恢复后状态
        // 还原绕过导航层 consent 检查。
        if (!consentStore.isConsented(BuildConfig.CONSENT_VERSION)) {
            return flowOf(
                AiStreamEvent.Failed(AiError.UserConsentRequired, false)
            )
        }
        val provider = resolveProvider(providerId)
            ?: return flowOf(
                AiStreamEvent.Failed(
                    AiError.Unknown(null, "provider $providerId not found"),
                    false
                )
            )

        // fix-2026-06-28-ai-model-selection-actually-used:
        //   modelName 为 null 时 fallback 走 `provider.defaultModel` 而非
        //   `provider.supportedModels.firstOrNull()`。前者是 provider 显式声明的
        //   "无用户偏好时用这个"，有业务语义;后者是 list 顺序副作用，deepseek 的
        //   flash 在前完全因为按 "lite→贵" 排，选了 pro 但 gateway 拿 null 仍走
        //   flash，正是 change 标题里要修的 bug。defaultModel 同样为 blank 时
        //   仍 emit ProviderNotConfigured，不静默发 "unknown" 字面量。
        val resolvedModel = modelName?.takeIf { it.isNotBlank() } ?: provider.defaultModel
        if (resolvedModel.isBlank()) {
            return flowOf(
                AiStreamEvent.Failed(
                    AiError.ProviderNotConfigured,
                    false
                )
            )
        }
        val model = resolvedModel
        // H1 修:`apiFormatOverride` 由 caller(Vm) 在 suspend 上下文读 prefs 后传入，
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
                // fix-2026-06-30-full-review-r1 HIGH H3:onCompletion 中 record() 是 suspend
                // Room 写，可能抛 DB 异常。CancellationException 必须保留(结构化并发),
                // 其它异常吞掉 + log，不让 DB 失败覆盖流终止信号。
                try {
                    historyRepo.get().record(
                        noteId = null,
                        providerId = providerId,
                        model = model,
                        op = op.name.lowercase(),
                        // fix-review-r4 L5:sourceText.length / 2 是中文场景粗估(1 CJK 字 ≈ 2
                        // 字符 ≈ 1 token);纯 ASCII 时低估约 4x(1 char ≈ 0.25 token)。
                        // 仅作 fallback 估算——provider 未返回 Usage 时使用，不影响计费。
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
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    android.util.Log.w(
                        "CoreAiGateway",
                        "history record failed for provider=$providerId op=${op.name}",
                        e
                    )
                }
            }
    }

    override suspend fun ping(
        providerId: String,
        apikey: String,
        modelName: String,
        apiFormatOverride: ApiFormat?
    ): String? {
        // fix-2026-06-30-full-review-r1 H10:ping 也走 consent 门，保证单一抽象层一致。
        if (!consentStore.isConsented(BuildConfig.CONSENT_VERSION)) {
            return AiError.UserConsentRequired.summary()
        }
        val provider = resolveProvider(providerId) ?: return "provider $providerId not registered"
        // remove-debug-fake-fallback §4.1-4.2:FakeAiProvider 不再注入 DI 图,provider map 不含 "fake",
        // 这段判断冗余删除。debug 与 release 一致走真 provider 路径。
        // fix-2026-06-28-ai-model-selection-actually-used:ping fallback 也走
        //   `provider.defaultModel`，跟 streamWritingOp 行为一致;若 ping 入参 modelName
        //   非空，优先用入参(允许 UI 指定非默认 model 测连通性)。
        val effectiveModel = modelName.takeIf { it.isNotBlank() } ?: provider.defaultModel
        // X 方案:ping 也走用户选的 apiFormat,endpoint 跟着切。
        // H1 修:ping 是 suspend，可在函数顶部 await providerPrefsStore.getApiFormat 而无需 runBlocking。
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
