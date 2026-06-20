package com.yy.writingwithai.core.ai

import com.yy.writingwithai.core.ai.api.AiCredentials
import com.yy.writingwithai.core.ai.api.AiError
import com.yy.writingwithai.core.ai.api.AiGateway
import com.yy.writingwithai.core.ai.api.AiProvider
import com.yy.writingwithai.core.ai.api.AiRequest
import com.yy.writingwithai.core.ai.api.AiStreamEvent
import com.yy.writingwithai.core.ai.api.ProviderDescriptor
import com.yy.writingwithai.core.ai.api.WritingOp
import com.yy.writingwithai.core.data.repo.AiHistoryRepository
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach

/**
 * [AiGateway] 唯一实现。
 *
 * - 根据 [providerId] 从 Hilt 注入的 `Map<String, AiProvider>` 取 provider
 * - 委托 `AiProvider.stream()` 发起调用,流式返回 [AiStreamEvent]
 * - 每次调用(成功/失败)通过 `onCompletion` 自动落 [AiHistoryEntity]
 * - 超时/IO 异常由 provider 内部处理并 emit [AiStreamEvent.Failed]
 */
@Singleton
class CoreAiGateway
@Inject
constructor(
    private val providers: Map<String, @JvmSuppressWildcards AiProvider>,
    private val historyRepo: Lazy<AiHistoryRepository>
) : AiGateway {
    override suspend fun listProviders(): List<ProviderDescriptor> = providers
        .map { (key, provider) ->
            ProviderDescriptor(
                id = provider.id,
                displayName = provider.displayName,
                models = provider.supportedModels,
                isConfigured = true
            )
        }
        .distinctBy { it.id }

    override fun streamWritingOp(
        op: WritingOp,
        sourceText: String,
        providerId: String,
        apikey: String,
        modelName: String?,
        systemPrompt: String?
    ): Flow<AiStreamEvent> {
        val provider =
            providers[providerId]
                ?: return kotlinx.coroutines.flow.flowOf(
                    AiStreamEvent.Failed(
                        AiError.Unknown(null, "provider $providerId not found"),
                        false
                    )
                )

        val model = modelName ?: provider.supportedModels.firstOrNull() ?: "unknown"
        val request = AiRequest(op, sourceText, model, systemPrompt)
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
                val errorMsg =
                    lastError?.summary()
                        ?: cause?.let { "${it::class.simpleName}: ${it.message}" }
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

    override suspend fun ping(providerId: String, apikey: String, modelName: String): Boolean {
        val provider = providers[providerId] ?: return false
        if (provider.id == "fake") return true
        var ok = true
        try {
            provider
                .stream(
                    AiRequest(WritingOp.EXPAND, "ping", modelName),
                    AiCredentials(apikey)
                ).collect { event ->
                    if (event is AiStreamEvent.Failed) ok = false
                }
        } catch (_: Exception) {
            ok = false
        }
        return ok
    }
}
