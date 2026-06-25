package com.yy.writingwithai.core.ai.provider

import com.yy.writingwithai.core.ai.api.AiCredentials
import com.yy.writingwithai.core.ai.api.AiError
import com.yy.writingwithai.core.ai.api.AiProvider
import com.yy.writingwithai.core.ai.api.AiRequest
import com.yy.writingwithai.core.ai.api.AiStreamEvent
import com.yy.writingwithai.core.ai.api.ApiFormat
import com.yy.writingwithai.core.ai.api.WritingOp
import com.yy.writingwithai.core.ai.prompt.DefaultPrompts
import com.yy.writingwithai.core.ai.stream.SseEvent
import com.yy.writingwithai.core.ai.stream.SseParser
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
    @Named("ai") private val client: OkHttpClient
) : AiProvider {
    override val id = config.id
    override val displayName = config.displayName
    override val supportedModels = config.supportedModels

    private val json = Json {
        ignoreUnknownKeys = true
        // L12 修:不写 encodeDefaults,避免显式 `max_tokens=2048` 被某些 OpenAI proxy 400。
    }

    override fun stream(request: AiRequest, credentials: AiCredentials): Flow<AiStreamEvent> {
        // M4 修:用 AtomicBoolean 在 flow lambda 和 retry predicate 之间共享状态。
        // 必须在 flow lambda 外声明,retry predicate 链在 flow lambda 外,看不到内部 var。
        val emittedDelta = AtomicBoolean(false)
        return flow {
            val baseUrl = credentials.baseUrlOverride ?: config.baseUrl
            // model-management-detail-dropdown X 方案:用户在详情页可切 OpenAI/Anthropic,endpoint path 跟着切
            val effectiveApiFormat = request.apiFormatOverride ?: config.apiFormat
            val path = when (effectiveApiFormat) {
                ApiFormat.OPENAI -> "/chat/completions"
                ApiFormat.ANTHROPIC -> "/anthropic/v1/messages"
            }
            val url = "$baseUrl$path"
            // fix-2026-06-24-review-r1-high H9:strip role-marker + cap length 8192
            val systemPrompt = sanitizeSystemPrompt(request.systemPrompt ?: systemPromptFor(request.op))

            @Serializable
            data class ChatMessage(val role: String, val content: String)

            @Serializable
            data class AnthropicBody(
                val model: String,
                val max_tokens: Int = 2048,
                val stream: Boolean = true,
                val system: String,
                val messages: List<ChatMessage>
            )

            @Serializable
            data class OpenAiBody(
                val model: String,
                val max_tokens: Int = 2048,
                val stream: Boolean = true,
                val messages: List<ChatMessage>
            )

            val isOpenAi = effectiveApiFormat == ApiFormat.OPENAI
            val body =
                if (isOpenAi) {
                    json.encodeToString(
                        OpenAiBody.serializer(),
                        OpenAiBody(
                            model = request.model,
                            messages = listOf(
                                ChatMessage("system", systemPrompt),
                                ChatMessage("user", request.sourceText)
                            )
                        )
                    )
                } else {
                    json.encodeToString(
                        AnthropicBody.serializer(),
                        AnthropicBody(
                            model = request.model,
                            system = systemPrompt,
                            messages = listOf(ChatMessage("user", request.sourceText))
                        )
                    )
                }

            val httpRequest =
                Request.Builder()
                    .url(url)
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .apply { addAuthHeaders(this, credentials) }
                    .build()

            val response = client.newCall(httpRequest).execute()
            if (!response.isSuccessful) {
                val code = response.code
                // review r2 修:readUtf8(byteCount) 在 body 小于 byteCount 时抛 EOFException,
                // 导致绝大多数非 1MiB+ 的错误响应详情全部丢失。参照 FeishuApiClientImpl 做
                // source.request(Long.MAX_VALUE) 拉完 body 到 buffer,再按 buffer.size 决定是否截断。
                val rawDetail = try {
                    response.body?.source()?.use { src ->
                        src.request(Long.MAX_VALUE)
                        if (src.buffer.size > MAX_RESPONSE_BODY_BYTES) {
                            src.buffer.readUtf8(MAX_RESPONSE_BODY_BYTES)
                        } else {
                            src.buffer.readUtf8()
                        }
                    } ?: ""
                } catch (e: Throwable) {
                    ""
                }
                response.close()
                // M3 修:截断 raw body 长度(provider 5xx 经常返回 KB 级 HTML,可能含 Authorization header 回显),
                // 脱敏 apikey / Bearer / x-api-key pattern(避免 provider 把请求 header 回显到错误页)。
                val detail = sanitizeErrorDetail(rawDetail)
                emit(
                    AiStreamEvent.Failed(
                        error =
                        when (code) {
                            401, 403 -> AiError.Auth(code, detail)
                            402 -> AiError.InsufficientBalance(detail)
                            // review r2 修:429 应映射 RateLimited(而非 Unknown),5xx 应映射 ServerError(而非 Network)
                            429 -> {
                                val retryAfter = response.header("Retry-After")?.toIntOrNull() ?: 60
                                AiError.RateLimited(retryAfterSeconds = retryAfter)
                            }
                            in 500..599 -> AiError.ServerError(code)
                            else -> AiError.Unknown(code, detail)
                        },
                        recoverable = code !in listOf(401, 403, 402)
                    )
                )
                return@flow
            }

            emit(AiStreamEvent.Started)

            val source =
                response.body?.source() ?: run {
                    response.close()
                    emit(AiStreamEvent.Failed(AiError.Unknown(null, "empty response body"), false))
                    return@flow
                }

            try {
                SseParser.parse(source).collect { sse ->
                    currentCoroutineContext().ensureActive() // fix H11:cooperative cancel
                    when (sse) {
                        is SseEvent.Data -> {
                            val delta = parseDelta(sse.content, effectiveApiFormat)
                            if (delta != null) {
                                emit(AiStreamEvent.Delta(delta))
                                emittedDelta.set(true)
                            }
                            val usage = parseUsage(sse.content, effectiveApiFormat)
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
                                    true
                                )
                            )
                        }
                    }
                }
            } finally {
                response.close()
            }
        }
            .flowOn(Dispatchers.IO)
            // fix H12 + M4:skip retry after Delta emitted (avoids duplicate UI text).
            // emittedDelta 用 AtomicBoolean 在 flow lambda 和 retry predicate 之间共享。
            .retry(1) { cause ->
                cause is IOException && cause !is SocketTimeoutException && !emittedDelta.get()
            }
            .catch { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                when (e) {
                    is SocketTimeoutException ->
                        emit(AiStreamEvent.Failed(AiError.Timeout(e.message ?: "timeout"), true))
                    is IOException ->
                        emit(AiStreamEvent.Failed(AiError.Network(-1, e.message ?: "network error"), true))
                    else ->
                        emit(AiStreamEvent.Failed(AiError.Unknown(null, e.message ?: "unknown"), false))
                }
            }
    }

    private fun addAuthHeaders(request: Request.Builder, credentials: AiCredentials) {
        when (config.authStyle) {
            AuthStyle.AUTHORIZATION -> request.header("Authorization", "Bearer ${credentials.apikey}")
            AuthStyle.X_API_KEY -> request.header("x-api-key", credentials.apikey)
            AuthStyle.CUSTOM_HEADER -> {
                val name = config.customAuthHeaderName ?: "x-api-key"
                // L6 注:OkHttp `header()` 是后写后赢;用户自定义 `customHeaders` 含同名 key
                // 会覆盖此默认,符合"用户自定义优先"预期,不额外处理顺序。
                request.header(name, credentials.apikey)
            }
        }
        // fix H13:validate customHeaders keys against reserved list + RFC-7230 token check
        config.customHeaders.forEach { (k, v) ->
            require(!RESERVED_HEADERS.contains(k.lowercase())) { "reserved header: $k" }
            require(HEADER_NAME_REGEX.matches(k)) { "invalid header name: $k" }
            request.header(k, v)
        }
    }

    /**
     * fix-2026-06-24-review-r1-high H9:strip role-marker patterns + cap length.
     */
    internal fun sanitizeSystemPrompt(s: String): String {
        val stripped = ROLE_MARKERS.replace(s, "[redacted]:")
        return if (stripped.length > MAX_SYSTEM_PROMPT_LEN) {
            stripped.substring(0, MAX_SYSTEM_PROMPT_LEN)
        } else {
            stripped
        }
    }

    private fun parseDelta(content: String, apiFormat: ApiFormat = config.apiFormat): String? {
        return try {
            if (apiFormat == ApiFormat.OPENAI) {
                @Serializable
                data class OpenAiDelta(val content: String? = null)

                @Serializable
                data class OpenAiChoice(val delta: OpenAiDelta? = null)

                @Serializable
                data class OpenAiChunk(val choices: List<OpenAiChoice> = emptyList())
                val obj = json.decodeFromString(OpenAiChunk.serializer(), content)
                obj.choices.firstOrNull()?.delta?.content
            } else {
                @Serializable
                data class DeltaBlock(val text: String)

                @Serializable
                data class DeltaObj(val delta: DeltaBlock)
                val obj = json.decodeFromString(DeltaObj.serializer(), content)
                obj.delta.text
            }
        } catch (_: kotlinx.serialization.SerializationException) {
            // L5 修:只吞序列化异常,其他异常抛给外层 catch。
            null
        }
    }

    private fun parseUsage(content: String, apiFormat: ApiFormat = config.apiFormat): AiStreamEvent.Usage? {
        return try {
            if (apiFormat == ApiFormat.OPENAI) {
                @Serializable
                data class OpenAiUsageObj(
                    val prompt_tokens: Int = 0,
                    val completion_tokens: Int = 0,
                    val total_tokens: Int = 0
                )

                @Serializable
                data class OpenAiUsageWrapper(val usage: OpenAiUsageObj? = null)
                val obj = json.decodeFromString(OpenAiUsageWrapper.serializer(), content)
                val u = obj.usage ?: return null
                AiStreamEvent.Usage(
                    inputTokens = u.prompt_tokens,
                    outputTokens = u.completion_tokens,
                    totalTokens = u.total_tokens
                )
            } else {
                @Serializable
                data class UsageObj(
                    val input_tokens: Int = 0,
                    val output_tokens: Int = 0
                )

                @Serializable
                data class UsageWrapper(val usage: UsageObj)
                val obj = json.decodeFromString(UsageWrapper.serializer(), content)
                AiStreamEvent.Usage(
                    inputTokens = obj.usage.input_tokens,
                    outputTokens = obj.usage.output_tokens,
                    totalTokens = obj.usage.input_tokens + obj.usage.output_tokens
                )
            }
        } catch (_: kotlinx.serialization.SerializationException) {
            // L5 修:同上。
            null
        }
    }

    private fun systemPromptFor(op: WritingOp): String = DefaultPrompts.forOp(op)

    private companion object {
        const val MAX_ERROR_DETAIL_LEN = 200

        // fix H10:cap upstream response body reads to 1 MiB
        const val MAX_RESPONSE_BODY_BYTES = 1L shl 20

        // fix H9:cap system prompt length
        const val MAX_SYSTEM_PROMPT_LEN = 8192

        // fix H9:strip role-marker abuse
        val ROLE_MARKERS = Regex("""(?i)\b(role|system|assistant|user)\s*:\s*""")

        // fix H13:reserved headers that user must not override
        val RESERVED_HEADERS = setOf(
            "host",
            "authorization",
            "content-length",
            "transfer-encoding",
            "connection",
            "cookie"
        )

        // RFC-7230 token: tchar = "!" / "#" / "$" / "%" / "&" / "'" / "*" / "+" / "-" / "." /
        //   "^" / "_" / "`" / "|" / "~" / DIGIT / ALPHA
        val HEADER_NAME_REGEX = Regex("^[A-Za-z0-9!#\\$%&'*+\\-.^_`|~]+\$")
        val SENSITIVE_PATTERNS = listOf(
            Regex("""sk-[A-Za-z0-9_\-]{16,}"""),
            Regex("""(?i)Bearer\s+[A-Za-z0-9_\-\.=]{16,}"""),
            Regex("""(?i)x-api-key[:\s]+[A-Za-z0-9_\-\.=]{16,}""")
        )
    }

    /** M3:provider 错误页可能 HTML 或含敏感 header 回显,统一截断 + 脱敏。 */
    private fun sanitizeErrorDetail(raw: String): String {
        // 检测 HTML 错误页直接替换成"上游服务错误",避免 10KB HTML 灌进 UI / history
        val trimmed = raw.take(MAX_ERROR_DETAIL_LEN)
        val noHtml = if (trimmed.contains("<html", ignoreCase = true) || trimmed.contains("<body", ignoreCase = true)) {
            "上游服务错误"
        } else {
            trimmed
        }
        return SENSITIVE_PATTERNS.fold(noHtml) { acc, p -> acc.replace(p, "***REDACTED***") }
    }
}
