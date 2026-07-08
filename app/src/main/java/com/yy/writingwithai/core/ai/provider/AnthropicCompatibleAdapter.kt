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
        // M4 修:用 AtomicBoolean 在 flow lambda 和 retry predicate 之间共享状态。
        // 必须在 flow lambda 外声明，retry predicate 链在 flow lambda 外，看不到内部 var。
        val emittedDelta = AtomicBoolean(false)
        return flow {
            val baseUrl = credentials.baseUrlOverride ?: config.baseUrl
            // model-management-detail-dropdown X 方案:用户在详情页可切 OpenAI/Anthropic,endpoint path 跟着切
            val effectiveApiFormat = request.apiFormatOverride ?: config.apiFormat
            // ux-2026-06-28 #3:custom 表单走"完整 URL",endpointPath 留空 → 直用 baseUrl;
// 内置 provider(deepseek/minimax/mimo)的 config.endpointPath 非空，继续走
// "$baseUrl${endpointPath}" 拼接，行为不变。
//
// custom-provider-api-format r2:删掉硬编码 path(原 line 77-80 按 effectiveApiFormat
// 拼 /chat/completions 或 /anthropic/v1/messages)，统一用 config.endpointPath:
// - custom 表单:buildConfig 按 state.apiFormat 设 endpointPath = /v1/messages
//   (Anthropic SDK) 或 /chat/completions (OpenAI SDK),path 由 SDK 设计固定，
//   厂家可能把 /anthropic 子路径塞进 baseUrl(DeepSeek 风格)。
// - 内置 provider:endpointPath 显式配置(MinimaxConfig = /anthropic/v1/messages,
//   适配 Minimax 自家 SDK 行为)。
//
// 之前硬编码 path = /anthropic/v1/messages 对 DeepSeek 用户造成 /anthropic 重复
// (logcat 23:53:08 验证:POST https://api.deepseek.com/anthropic/anthropic/v1/messages
// → 404，因为 DeepSeek baseUrl 已经含 /anthropic,adapter 不应再拼)。
            val url = if (config.endpointPath.isBlank()) {
                baseUrl
            } else {
                val trimmed = baseUrl.trimEnd('/')
                if (config.endpointPath.startsWith("/")) {
                    "$trimmed${config.endpointPath}"
                } else {
                    "$trimmed/${config.endpointPath}"
                }
            }
            // fix-2026-06-24-review-r1-high H9:strip role-marker + cap length 8192
            val systemPrompt = sanitizeSystemPrompt(request.systemPrompt ?: systemPromptFor(request.op))

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

            // fix-review-r3-high H1:把 `response.close()` 集中到外层 try-finally,
            // 之前 success / failure 两条路径各自 close，取消路径(catch 抛
            // CancellationException)下 body source 泄漏(socket 不释放)。
            // fix-review-r3-high H3:body read 的 `catch (Throwable)` 同样吞
            // CancellationException，需要在通用 catch 前先 rethrow。
            val response = client.newCall(httpRequest).execute()
            try {
                val ct = response.header("Content-Type")
                android.util.Log.i(
                    "AnthropicAdapter",
                    "POST $url → ${response.code} Content-Type=$ct"
                )
                if (!response.isSuccessful) {
                    val code = response.code
                    // review r2 修:readUtf8(byteCount) 在 body 小于 byteCount 时抛 EOFException,
                    // 导致绝大多数非 1MiB+ 的错误响应详情全部丢失。参照 FeishuApiClientImpl 做
                    // source.request(Long.MAX_VALUE) 拉完 body 到 buffer，再按 buffer.size 决定是否截断。
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
                    // M3 修:截断 raw body 长度(provider 5xx 经常返回 KB 级 HTML，可能含 Authorization header 回显),
                    // 脱敏 apikey / Bearer / x-api-key pattern(避免 provider 把请求 header 回显到错误页)。
                    val detail = sanitizeErrorDetail(rawDetail)
                    // custom-provider-api-format r2 debug:把 url + code 一并写到 logcat + error detail,
                    // 让用户 adb logcat 直接看真实 POST URL / 响应码(404 / 401 / 5xx 等)
                    // 不用猜是 URL 拼错还是 protocol 不匹配。Log.d 标签固定 "AnthropicAdapter"。
                    android.util.Log.d(
                        "AnthropicAdapter",
                        "POST $url → $code body=${rawDetail.take(200)}"
                    )
                    emit(
                        AiStreamEvent.Failed(
                            error =
                            when (code) {
                                401, 403 -> AiError.Auth(code, detail)
                                402 -> AiError.InsufficientBalance(detail)
                                // review r2 修:429 应映射 RateLimited(而非 Unknown),5xx 应映射 ServerError(而非 Network)
                                429 -> {
                                    val retryAfter = parseRetryAfterSeconds(response.header("Retry-After")) ?: 60
                                    AiError.RateLimited(retryAfterSeconds = retryAfter)
                                }
                                in 500..599 -> AiError.ServerError(code)
                                else -> AiError.Unknown(code, detail)
                            },
                            recoverable = code !in NON_RECOVERABLE_CODES
                        )
                    )
                    return@flow
                }

                emit(AiStreamEvent.Started)

                val source = response.body?.source()
                if (source == null) {
                    emit(AiStreamEvent.Failed(AiError.Unknown(null, "empty response body"), false))
                    return@flow
                }

                // fix-deepseek-non-streaming:部分 provider(如 Deepseek)的 /anthropic/v1/messages
                // 端点即使请求体带 stream=true，也返回 Content-Type: application/json 的非流式
                // JSON 响应(完整 Anthropic Messages API response object)。SseParser 只处理
                // data: 前缀的 SSE 行，会静默忽略整个 JSON body，导致 collectText() 拿到空串。
                // 检测 Content-Type，非 text/event-stream 时走非流式 JSON 解析路径。
                val contentType = response.header("Content-Type", "").orEmpty()
                val isSse = contentType.contains("text/event-stream", ignoreCase = true)

                if (isSse) {
                    // 流式 SSE 路径(正常 Anthropic 兼容 provider)
                    SseParser.parse(source).collect { sse ->
                        currentCoroutineContext().ensureActive()
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
                } else {
                    // 非流式 JSON 路径:provider 返回完整 Anthropic Messages API response object
                    // (Content-Type: application/json)。从 content[] 数组提取文本，emit 为
                    // Delta → Usage → Done，让下游 collectText() 正常工作。
                    // fix-review:复用 MAX_RESPONSE_BODY_BYTES 上限，防止恶意/误配 provider
                    // 返回 multi-GB body 导致 OOM(与错误路径一致)。
                    source.request(Long.MAX_VALUE)
                    if (source.buffer.size > MAX_RESPONSE_BODY_BYTES) {
                        emit(
                            AiStreamEvent.Failed(
                                AiError.Unknown(null, "non-streaming response exceeds 1MiB limit"),
                                false
                            )
                        )
                        return@flow
                    }
                    val rawBody = source.buffer.readUtf8()
                    val nonStreamResult = parseNonStreamingResponse(rawBody, effectiveApiFormat)
                    if (nonStreamResult != null) {
                        if (nonStreamResult.text.isNotEmpty()) {
                            emit(AiStreamEvent.Delta(nonStreamResult.text))
                            emittedDelta.set(true)
                        }
                        nonStreamResult.usage?.let { emit(it) }
                        emit(AiStreamEvent.Done)
                    } else {
                        // fix-review:parseNonStreamingResponse 返回 null 说明 JSON 格式异常，
                        // 不应 emit Done(会误导下游认为成功)，应 emit Failed。
                        emit(
                            AiStreamEvent.Failed(
                                AiError.Unknown(null, "non-streaming response parse error"),
                                false
                            )
                        )
                    }
                }
            } finally {
                response.close()
            }
        }
            .flowOn(Dispatchers.IO)
            // fix H12 + M4:skip retry after Delta emitted (avoids duplicate UI text).
            // emittedDelta 用 AtomicBoolean 在 flow lambda 和 retry predicate 之间共享。
            // fix-review-r3-medium M1:retry 范围太宽，把所有 IOException 都重试 — 但 SSL/UnknownHost /
            // ConnectException 等"环境错"retry 也不会自愈，只会拖慢 1.5s+ 后把 Network 抛给 UI。
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

        // fix H13:reserved headers that user must not override
        val RESERVED_HEADERS = setOf(
            "host",
            "authorization",
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
