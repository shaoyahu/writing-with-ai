package com.yy.writingwithai.core.ai.provider

import com.yy.writingwithai.core.ai.api.AiCredentials
import com.yy.writingwithai.core.ai.api.AiError
import com.yy.writingwithai.core.ai.api.AiProvider
import com.yy.writingwithai.core.ai.api.AiRequest
import com.yy.writingwithai.core.ai.api.AiStreamEvent
import com.yy.writingwithai.core.ai.api.ApiFormat
import com.yy.writingwithai.core.ai.api.WritingOp
import com.yy.writingwithai.core.ai.prompt.DefaultPrompts
import com.yy.writingwithai.core.ai.prompt.SafePromptTemplate
import com.yy.writingwithai.core.ai.stream.SseEvent
import com.yy.writingwithai.core.ai.stream.SseParser
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Named
import javax.net.ssl.SSLException
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
 * 由 [ProviderConfig] 驱动认证 / URL / 字段，不 为每家写独立 adapter。
 * 支持三家预置 provider(deepseek / minimax / mimo)+ 自定义 Anthropic 兼容 provider。
 *
 * fix-full-review:移除 @Singleton + @Inject — 本类有两种实例化路径:
 * 1) AiModule.@Provides 手动构造(内置 provider，Hilt @Singleton 在 @Provides 上保证单例)
 * 2) CoreAiGateway.resolveProvider 手动构造(自定义 provider，走 customAdapterCache 缓存)
 * 类级 @Singleton 对路径 2 无效(手动构造绕过 Hilt)，@Inject 也从未被 Hilt 使用。
 * 单例保证由 @Provides/@Named 或 customAdapterCache 负责，不在类声明上误导。
 */
class AnthropicCompatibleAdapter
constructor(
    private val config: ProviderConfig,
    @Named("ai") private val client: OkHttpClient
) : AiProvider {
    override val id = config.id
    override val displayName = config.displayName
    override val supportedModels = config.supportedModels
    override val defaultModel = config.defaultModel

    private val json = Json {
        ignoreUnknownKeys = true
        // L12 修:不写 encodeDefaults，避免显式 `max_tokens=2048` 被某些 OpenAI proxy 400。
    }

    override fun stream(request: AiRequest, credentials: AiCredentials): Flow<AiStreamEvent> {
        // fix M7 (full-review):原始 stream() ~257 行混合职责 — URL 拼装 + body 编码 +
        // HTTP 调用 + status 处理 + SSE 解析 + 非流式 JSON 解析 + retry/catch。
        // 拆为 urlFor() / buildBody() / handleResponse() 三个 private helper +
        // 顶层 stream() 只负责 orchestrate。stream 主体回归 30 行内。
        val emittedDelta = AtomicBoolean(false)
        return flow {
            val effectiveApiFormat = request.apiFormatOverride ?: config.apiFormat
            val systemPrompt =
                sanitizeSystemPrompt(request.systemPrompt ?: systemPromptFor(request.op))
            val body = buildRequestBody(request, systemPrompt, effectiveApiFormat)
            val url = urlFor(credentials)
            val httpRequest =
                Request.Builder()
                    .url(url)
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .apply { addAuthHeaders(this, credentials) }
                    .build()
            val response = client.newCall(httpRequest).execute()
            try {
                handleResponse(
                    response = response,
                    effectiveApiFormat = effectiveApiFormat,
                    emittedDelta = { emittedDelta.set(true) }
                )
            } finally {
                response.close()
            }
        }
            .flowOn(Dispatchers.IO)
            // fix H12 + M4:skip retry after Delta emitted (avoids duplicate UI text).
            // emittedDelta 用 AtomicBoolean 在 flow lambda 和 retry predicate 之间共享。
            // fix-review-r3-medium M1:retry 范围太宽,把所有 IOException 都重试 — 但 SSL/UnknownHost /
            // ConnectException 等"环境错"retry 也不会自愈,只会拖慢 1.5s+ 后把 Network 抛给 UI。
            // retry 仅限"瞬时"网络错(EOF / connection reset / read timeout 等),ssl/dns/connect 失败
            // 直接 fallthrough 给 .catch。
            // fix-2026-07-05-review-r4 CRITICAL C2:retry predicate 必须显式检查并 rethrow CancellationException
            // 避免 retry 操作符延迟协程取消信号传播
            .retry(1) { cause ->
                if (cause is kotlinx.coroutines.CancellationException) throw cause
                cause is IOException && cause !is SocketTimeoutException && !emittedDelta.get() && cause.isRetryable()
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

    /**
     * fix M7 (full-review):抽 URL 拼装逻辑。custom 表单走完整 URL(直用 baseUrl),
     * 内置 provider 把 endpointPath 拼到 baseUrl 后(diff baseUrl 已含 /anthropic 时不重复)。
     */
    private fun urlFor(credentials: AiCredentials): String {
        val baseUrl = credentials.baseUrlOverride ?: config.baseUrl
        if (config.endpointPath.isBlank()) return baseUrl
        val trimmed = baseUrl.trimEnd('/')
        return if (config.endpointPath.startsWith("/")) {
            "$trimmed${config.endpointPath}"
        } else {
            "$trimmed/${config.endpointPath}"
        }
    }

    /**
     * fix M7 (full-review):抽请求 body 序列化。OpenAI / Anthropic 两条路径共用
     * ChatMessage,user content 走 SafePromptTemplate.fenceUserContent 防 prompt injection。
     */
    private fun buildRequestBody(request: AiRequest, systemPrompt: String, effectiveApiFormat: ApiFormat): String {
        val isOpenAi = effectiveApiFormat == ApiFormat.OPENAI

        @Serializable
        data class ChatMessage(val role: String, val content: String)

        @Serializable
        data class AnthropicBody(
            val model: String,
            val max_tokens: Int = DEFAULT_MAX_TOKENS,
            val stream: Boolean = true,
            val system: String,
            val messages: List<ChatMessage>
        )

        @Serializable
        data class OpenAiBody(
            val model: String,
            val max_tokens: Int = DEFAULT_MAX_TOKENS,
            val stream: Boolean = true,
            val messages: List<ChatMessage>
        )
        return if (isOpenAi) {
            json.encodeToString(
                OpenAiBody.serializer(),
                OpenAiBody(
                    model = request.model,
                    // fix M40 (full-review):显式给 max_tokens。`encodeDefaults=false` 是为了避免
                    // 旧 OpenAI proxy 把 DEFAULT_MAX_TOKENS=2048 当显式值 400,但完全省略字段
                    // 又会让 anthropic 报"missing max_tokens"。两者都传 model 自带的 maxOutput
                    // 兜底(没有则用 DEFAULT_MAX_TOKENS),且确保 max_tokens 一定存在。
                    max_tokens = request.maxOutput ?: DEFAULT_MAX_TOKENS,
                    messages = listOf(
                        ChatMessage("system", systemPrompt),
                        ChatMessage("user", SafePromptTemplate.fenceUserContent(request.sourceText))
                    )
                )
            )
        } else {
            json.encodeToString(
                AnthropicBody.serializer(),
                AnthropicBody(
                    model = request.model,
                    // 同 M40:显式 max_tokens。
                    max_tokens = request.maxOutput ?: DEFAULT_MAX_TOKENS,
                    system = systemPrompt,
                    messages =
                    listOf(
                        ChatMessage("user", SafePromptTemplate.fenceUserContent(request.sourceText))
                    )
                )
            )
        }
    }

    /**
     * fix M7 (full-review):抽 response 处理 — error path emit Failed,success path
     * 选 SSE 还是非流式 JSON,parse 后 emit Delta/Usage/Done/Failed。Stream 行为完全保留。
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<AiStreamEvent>.handleResponse(
        response: okhttp3.Response,
        effectiveApiFormat: ApiFormat,
        emittedDelta: () -> Unit
    ) {
        val code = response.code
        val ct = response.header("Content-Type")
        android.util.Log.i("AnthropicAdapter", "POST ${response.request.url} → $code Content-Type=$ct")
        if (!response.isSuccessful) {
            val rawDetail = try {
                response.body?.source()?.use { src ->
                    src.request(Long.MAX_VALUE)
                    if (src.buffer.size > MAX_RESPONSE_BODY_BYTES) {
                        src.buffer.readUtf8(MAX_RESPONSE_BODY_BYTES)
                    } else {
                        src.buffer.readUtf8()
                    }
                } ?: ""
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                ""
            }
            val detail = sanitizeErrorDetail(rawDetail)
            android.util.Log.d(
                "AnthropicAdapter",
                "POST ${response.request.url} → $code body=${detail.take(200)}"
            )
            emit(
                AiStreamEvent.Failed(
                    error = when (code) {
                        401, 403 -> AiError.Auth(code, detail)
                        402 -> AiError.InsufficientBalance(detail)
                        429 -> {
                            val retryAfter =
                                parseRetryAfterSeconds(response.header("Retry-After")) ?: 60
                            AiError.RateLimited(retryAfterSeconds = retryAfter)
                        }
                        in 500..599 -> AiError.ServerError(code)
                        else -> AiError.Unknown(code, detail)
                    },
                    recoverable = code !in NON_RECOVERABLE_CODES
                )
            )
            return
        }

        emit(AiStreamEvent.Started)
        val source = response.body?.source()
        if (source == null) {
            emit(AiStreamEvent.Failed(AiError.Unknown(null, "empty response body"), false))
            return
        }

        val contentType = response.header("Content-Type", "").orEmpty()
        val isSse = contentType.contains("text/event-stream", ignoreCase = true)
        if (isSse) {
            SseParser.parse(source).collect { sse ->
                currentCoroutineContext().ensureActive()
                when (sse) {
                    is SseEvent.Data -> {
                        val delta = parseDelta(sse.content, effectiveApiFormat)
                        if (delta != null) {
                            emit(AiStreamEvent.Delta(delta))
                            emittedDelta()
                        }
                        val usage = parseUsage(sse.content, effectiveApiFormat)
                        if (usage != null) emit(usage)
                    }
                    is SseEvent.Done -> emit(AiStreamEvent.Done)
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
        } else {
            source.request(Long.MAX_VALUE)
            if (source.buffer.size > MAX_RESPONSE_BODY_BYTES) {
                emit(
                    AiStreamEvent.Failed(
                        AiError.Unknown(null, "non-streaming response exceeds 1MiB limit"),
                        false
                    )
                )
                return
            }
            val rawBody = source.buffer.readUtf8()
            val nonStreamResult = parseNonStreamingResponse(rawBody, effectiveApiFormat)
            if (nonStreamResult != null) {
                if (nonStreamResult.text.isNotEmpty()) {
                    emit(AiStreamEvent.Delta(nonStreamResult.text))
                    emittedDelta()
                }
                nonStreamResult.usage?.let { emit(it) }
                emit(AiStreamEvent.Done)
            } else {
                emit(
                    AiStreamEvent.Failed(
                        AiError.Unknown(null, "non-streaming response parse error"),
                        false
                    )
                )
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
                // 会覆盖此默认，符合"用户自定义优先"预期，不额外处理顺序。
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
            // L5 修:只吞序列化异常，其他异常抛给外层 catch。
            null
        }
    }

    private fun parseUsage(content: String, apiFormat: ApiFormat = config.apiFormat): AiStreamEvent.Usage? {
        return try {
            if (apiFormat == ApiFormat.OPENAI) {
                @Serializable
                data class OpenAiUsageObj(
                    // fix-review-r3-medium M3:个别 provider(尤其代理)只填部分字段，其余 null。
                    // 把字段改成 nullable，缺哪项就 fallback 算，避免 SerializationException 把整个
                    // usage chunk 丢了。
                    val prompt_tokens: Int? = null,
                    val completion_tokens: Int? = null,
                    val total_tokens: Int? = null
                )

                @Serializable
                data class OpenAiUsageWrapper(val usage: OpenAiUsageObj? = null)
                val obj = json.decodeFromString(OpenAiUsageWrapper.serializer(), content)
                val u = obj.usage ?: return null
                val input = u.prompt_tokens ?: 0
                val output = u.completion_tokens ?: 0
                val total = u.total_tokens ?: (input + output)
                AiStreamEvent.Usage(
                    inputTokens = input,
                    outputTokens = output,
                    totalTokens = total
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

    /**
     * fix-deepseek-non-streaming:解析非流式 JSON 响应。
     *
     * 部分 provider(如 Deepseek)的 /anthropic/v1/messages 端点即使请求体带 stream=true，
     * 也返回 Content-Type: application/json 的完整 Anthropic Messages API response object，
     * 而非 SSE text/event-stream。此方法从完整 JSON body 中提取文本和 usage 信息。
     *
     * Anthropic Messages API 非流式响应结构:
     * ```
     * {
     *   "id": "msg_...",
     *   "type": "message",
     *   "role": "assistant",
     *   "content": [
     *     {"type": "thinking", "thinking": "..."},  // 可选，extended thinking
     *     {"type": "text", "text": "实际回复文本"}
     *   ],
     *   "model": "...",
     *   "usage": {"input_tokens": N, "output_tokens": M}
     * }
     * ```
     */
    private fun parseNonStreamingResponse(
        rawBody: String,
        apiFormat: ApiFormat = config.apiFormat
    ): NonStreamingResult? {
        return try {
            if (apiFormat == ApiFormat.OPENAI) {
                @Serializable
                data class OpenAiMessage(val content: String? = null)

                @Serializable
                data class OpenAiChoice(val message: OpenAiMessage? = null)

                @Serializable
                data class OpenAiUsage(
                    val prompt_tokens: Int? = null,
                    val completion_tokens: Int? = null,
                    val total_tokens: Int? = null
                )

                @Serializable
                data class OpenAiResponse(
                    val choices: List<OpenAiChoice> = emptyList(),
                    val usage: OpenAiUsage? = null
                )
                val obj = json.decodeFromString(OpenAiResponse.serializer(), rawBody)
                val text = obj.choices.firstOrNull()?.message?.content.orEmpty()
                val usage = obj.usage?.let { u ->
                    val input = u.prompt_tokens ?: 0
                    val output = u.completion_tokens ?: 0
                    AiStreamEvent.Usage(
                        inputTokens = input,
                        outputTokens = output,
                        totalTokens = u.total_tokens ?: (input + output)
                    )
                }
                NonStreamingResult(text, usage)
            } else {
                // Anthropic Messages API 非流式响应
                @Serializable
                data class ContentBlock(
                    val type: String,
                    val text: String? = null,
                    val thinking: String? = null
                )

                @Serializable
                data class AnthropicUsage(
                    val input_tokens: Int = 0,
                    val output_tokens: Int = 0
                )

                @Serializable
                data class AnthropicResponse(
                    val content: List<ContentBlock> = emptyList(),
                    val usage: AnthropicUsage? = null
                )
                val obj = json.decodeFromString(AnthropicResponse.serializer(), rawBody)
                // 只提取 type="text" 的 content block，跳过 thinking 等其他类型
                val text = obj.content
                    .filter { it.type == "text" }
                    .mapNotNull { it.text }
                    .joinToString("")
                val usage = obj.usage?.let { u ->
                    AiStreamEvent.Usage(
                        inputTokens = u.input_tokens,
                        outputTokens = u.output_tokens,
                        totalTokens = u.input_tokens + u.output_tokens
                    )
                }
                NonStreamingResult(text, usage)
            }
        } catch (_: kotlinx.serialization.SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            // fix-review:JSON 语法合法但语义异常(如 content 是 object 而非 array、
            // Int 溢出等)时 kotlinx.serialization 内部抛 IllegalArgumentException，
            // 与 SSE 路径 parseDelta/parseUsage 的 graceful degradation 对齐。
            null
        }
    }

    /** 非流式响应解析结果。 */
    private data class NonStreamingResult(
        val text: String,
        val usage: AiStreamEvent.Usage?
    )

    private fun systemPromptFor(op: WritingOp): String = DefaultPrompts.forOp(op)

    private companion object {
        // fix-2026-06-26-review-r3 LOW:默认 max_tokens 常量，避免 data class 默认值里 magic number。
        const val DEFAULT_MAX_TOKENS = 2048

        const val MAX_ERROR_DETAIL_LEN = 200

        // fix H10:cap upstream response body reads to 1 MiB
        const val MAX_RESPONSE_BODY_BYTES = 1L shl 20

        // fix H9:cap system prompt length
        const val MAX_SYSTEM_PROMPT_LEN = 8192

        // fix H9:strip role-marker abuse
        val ROLE_MARKERS = Regex("""(?i)\b(role|system|assistant|user)\s*:\s*""")

        // fix H2:加入 x-api-key 防止 customHeaders 覆盖 apikey(OkHttp last-writer-wins)。
        val RESERVED_HEADERS = setOf(
            "host",
            "authorization",
            "x-api-key",
            "content-length",
            "transfer-encoding",
            "connection",
            "cookie"
        )

        // fix H:不可恢复的错误码集合，避免每次调用创建新 list
        val NON_RECOVERABLE_CODES = setOf(401, 403, 402)

        // RFC-7230 token: tchar = "!" / "#" / "$" / "%" / "&" / "'" / "*" / "+" / "-" / "." /
        //   "^" / "_" / "`" / "|" / "~" / DIGIT / ALPHA
        val HEADER_NAME_REGEX = Regex("^[A-Za-z0-9!#\\$%&'*+\\-.^_`|~]+\$")
        val SENSITIVE_PATTERNS = listOf(
            Regex("""sk-[A-Za-z0-9_\-]{16,}"""),
            Regex("""(?i)Bearer\s+[A-Za-z0-9_\-\.=]{16,}"""),
            Regex("""(?i)x-api-key[:\s]+[A-Za-z0-9_\-\.=]{16,}""")
        )
    }

    /** M3:provider 错误页可能 HTML 或含敏感 header 回显，统一截断 + 脱敏。 */
    private fun sanitizeErrorDetail(raw: String): String {
        // 检测 HTML 错误页直接替换成"上游服务错误"，避免 10KB HTML 灌进 UI / history
        val trimmed = raw.take(MAX_ERROR_DETAIL_LEN)
        val noHtml = if (trimmed.contains("<html", ignoreCase = true) || trimmed.contains("<body", ignoreCase = true)) {
            "上游服务错误"
        } else {
            trimmed
        }
        return SENSITIVE_PATTERNS.fold(noHtml) { acc, p -> acc.replace(p, "***REDACTED***") }
    }

    /**
     * fix-review-r3-medium M4:`Retry-After` 头按 RFC 7231 有两种格式:
     *  - delta-seconds(纯整数，如 `120`)
     *  - HTTP-date(如 `Wed, 21 Oct 2015 07:28:00 GMT`)
     *
     * 之前只 `toIntOrNull()`,HTTP-date 形式 silently 退到默认 60s,provider 真要 1h 后重试时
     * 我们 60s 就重发，加重 provider 负担 / 触发更长 ban。
     */
    internal fun parseRetryAfterSeconds(header: String?): Int? {
        if (header.isNullOrBlank()) return null
        header.trim().toIntOrNull()?.let { return it.coerceAtLeast(0) }
        // HTTP-date 形式:解析失败 → null(由 caller 用默认 60s)
        val httpDate = parseHttpDate(header.trim()) ?: return null
        val deltaMs = httpDate.time - System.currentTimeMillis()
        // 已过期 / 已到时 → 0(马上重试);未来 → 等待秒数
        // fix-full-review:coerceAtMost(Int.MAX_VALUE) 防止 Long→Int 溢出
        return (deltaMs / 1000L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }

    private fun parseHttpDate(s: String): Date? {
        // fix-2026-07-05-review-r4 MEDIUM M4:使用线程安全的 DateTimeFormatter 替代 SimpleDateFormat
        // RFC 7231 HTTP-date 格式，使用 RFC_1123_DATE_TIME (thread-safe)
        return try {
            val instant = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.parse(s, java.time.Instant::from)
            Date.from(instant)
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * fix-review-r3-medium M1:只对"瞬时"网络错做 retry。SSL/TLS / DNS / 主动 connect refused
 * 是环境错，retry 不会自愈，直接交回上层 .catch emit Failed。
 *
 * 顶层 internal extension，供单测直接调用(放 companion 里会被 private 收紧)。
 */
internal fun IOException.isRetryable(): Boolean = when (this) {
    is SSLException -> false
    is UnknownHostException -> false
    is java.net.ConnectException -> false
    else -> true
}
