package com.yy.writingwithai.core.ai.provider

import com.yy.writingwithai.core.ai.api.AiCredentials
import com.yy.writingwithai.core.ai.api.AiError
import com.yy.writingwithai.core.ai.api.AiProvider
import com.yy.writingwithai.core.ai.api.AiRequest
import com.yy.writingwithai.core.ai.api.AiStreamEvent
import com.yy.writingwithai.core.ai.api.WritingOp
import com.yy.writingwithai.core.ai.prompt.ExpandPrompt
import com.yy.writingwithai.core.ai.prompt.OrganizePrompt
import com.yy.writingwithai.core.ai.prompt.PolishPrompt
import com.yy.writingwithai.core.ai.stream.SseEvent
import com.yy.writingwithai.core.ai.stream.SseParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.retry
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * 通用的 Anthropic Messages API 兼容 adapter。
 *
 * 由 [ProviderConfig] 驱动认证 / URL / 字段,不 为每家写独立 adapter。
 * 支持三家预置 provider(deepseek / minimax / mimo)+ 自定义 Anthropic 兼容 provider。
 */
@Singleton
class AnthropicCompatibleAdapter
    @Inject
    constructor(
        private val config: ProviderConfig,
        @Named("ai") private val client: OkHttpClient,
    ) : AiProvider {
        override val id = config.id
        override val displayName = config.displayName
        override val supportedModels = config.supportedModels

        private val json = Json { ignoreUnknownKeys = true }

        override fun stream(
            request: AiRequest,
            credentials: AiCredentials,
        ): Flow<AiStreamEvent> =
            flow {
                val baseUrl = credentials.baseUrlOverride ?: config.baseUrl
                val url = "$baseUrl${config.endpointPath}"
                val systemPrompt = systemPromptFor(request.op)

                @Serializable
                data class ChatMessage(val role: String, val content: String)

                @Serializable
                data class RequestBody(
                    val model: String,
                    val max_tokens: Int = 2048,
                    val stream: Boolean = true,
                    val system: String,
                    val messages: List<ChatMessage>,
                )

                val body =
                    json.encodeToString(
                        RequestBody.serializer(),
                        RequestBody(
                            model = request.model,
                            system = systemPrompt,
                            messages = listOf(ChatMessage("user", request.sourceText)),
                        ),
                    )

                val httpRequest =
                    Request.Builder()
                        .url(url)
                        .post(body.toRequestBody("application/json".toMediaType()))
                        .apply { addAuthHeaders(this, credentials) }
                        .build()

                val response = client.newCall(httpRequest).execute()
                if (!response.isSuccessful) {
                    val code = response.code
                    val detail = response.body?.string() ?: ""
                    response.close()
                    emit(
                        AiStreamEvent.Failed(
                            error =
                                when (code) {
                                    401, 403 -> AiError.Auth(code, detail)
                                    402 -> AiError.InsufficientBalance(detail)
                                    in 500..599 -> AiError.Network(code, detail)
                                    else -> AiError.Unknown(code, detail)
                                },
                            recoverable = code !in listOf(401, 403, 402),
                        ),
                    )
                    return@flow
                }

                emit(AiStreamEvent.Started)

                val source =
                    response.body?.source() ?: run {
                        response.close()
                        emit(AiStreamEvent.Failed(AiError.Unknown(null, "empty response body"), true))
                        return@flow
                    }

                try {
                    SseParser.parse(source).collect { sse ->
                        when (sse) {
                            is SseEvent.Data -> {
                                val delta = parseDelta(sse.content)
                                if (delta != null) {
                                    emit(AiStreamEvent.Delta(delta))
                                }
                                val usage = parseUsage(sse.content)
                                if (usage != null) {
                                    emit(usage)
                                }
                            }
                            is SseEvent.Done -> {
                                emit(AiStreamEvent.Done)
                            }
                            is SseEvent.Error -> {
                                emit(
                                    AiStreamEvent.Failed(
                                        AiError.Network(-1, sse.cause.message ?: "SSE error"),
                                        true,
                                    ),
                                )
                            }
                        }
                    }
                } finally {
                    response.close()
                }
            }
                .flowOn(Dispatchers.IO)
                .retry(1) { cause ->
                    cause is IOException && cause !is SocketTimeoutException
                }
                .catch { e ->
                    when (e) {
                        is SocketTimeoutException ->
                            emit(AiStreamEvent.Failed(AiError.Timeout(e.message ?: "timeout"), true))
                        is IOException ->
                            emit(AiStreamEvent.Failed(AiError.Network(-1, e.message ?: "network error"), true))
                        else ->
                            emit(AiStreamEvent.Failed(AiError.Unknown(null, e.message ?: "unknown"), false))
                    }
                }

        private fun addAuthHeaders(
            request: Request.Builder,
            credentials: AiCredentials,
        ) {
            when (config.authStyle) {
                AuthStyle.AUTHORIZATION -> request.header("Authorization", "Bearer ${credentials.apikey}")
                AuthStyle.X_API_KEY -> request.header("x-api-key", credentials.apikey)
                AuthStyle.CUSTOM_HEADER -> {
                    val name = config.customAuthHeaderName ?: "x-api-key"
                    request.header(name, credentials.apikey)
                }
            }
            config.customHeaders.forEach { (k, v) ->
                request.header(k, v)
            }
        }

        private fun parseDelta(content: String): String? {
            return try {
                @Serializable
                data class DeltaBlock(val text: String)

                @Serializable
                data class DeltaObj(val delta: DeltaBlock)
                val obj = json.decodeFromString(DeltaObj.serializer(), content)
                obj.delta.text
            } catch (_: Exception) {
                null
            }
        }

        private fun parseUsage(content: String): AiStreamEvent.Usage? {
            return try {
                @Serializable
                data class UsageObj(
                    val input_tokens: Int = 0,
                    val output_tokens: Int = 0,
                )

                @Serializable
                data class UsageWrapper(val usage: UsageObj)
                val obj = json.decodeFromString(UsageWrapper.serializer(), content)
                AiStreamEvent.Usage(
                    inputTokens = obj.usage.input_tokens,
                    outputTokens = obj.usage.output_tokens,
                    totalTokens = obj.usage.input_tokens + obj.usage.output_tokens,
                )
            } catch (_: Exception) {
                null
            }
        }

        private fun systemPromptFor(op: WritingOp): String =
            when (op) {
                WritingOp.EXPAND -> ExpandPrompt.SYSTEM
                WritingOp.POLISH -> PolishPrompt.SYSTEM
                WritingOp.ORGANIZE -> OrganizePrompt.SYSTEM
            }
    }
