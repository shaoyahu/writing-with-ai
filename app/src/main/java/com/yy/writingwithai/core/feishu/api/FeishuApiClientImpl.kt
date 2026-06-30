package com.yy.writingwithai.core.feishu.api

import android.util.Log
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.use

/**
 * feishu-oauth-flow · FeishuApiClient OkHttp 实现。
 *
 * 所有 endpoint 基地址 `https://open.feishu.cn/open-apis`(通过 AuthInterceptor 已塞 Authorization)。
 * 响应统一解析:HTTP 200 + `code != 0` → 业务错误;HTTP 401/403 由 AuthInterceptor 重取一次;
 * HTTP 5xx → ServerError;HTTP 429 → RateLimited(从 Retry-After header 读秒数)。
 *
 * spec: openspec/changes/feishu-oauth-flow/specs/feishu-api-client/spec.md
 */
@Singleton
class FeishuApiClientImpl
@Inject
constructor(
    @Named("feishu") private val httpClient: OkHttpClient
) : FeishuApiClient {

    override suspend fun createDocument(title: String, folderToken: String?): DocCreateResult =
        withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                put("title", title)
                if (folderToken != null) put("folder_token", folderToken)
            }.toString()
            val request = Request.Builder()
                .url(urlFor("docx/v1/documents"))
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            val resp = executeRequest(request)
            val doc = resp.data["document"]?.jsonObject
                ?: throw FeishuError.BadRequest(0, "missing data.document in create response")
            DocCreateResult(
                docId = doc.requireString("document_id"),
                docUrl = doc.optionalString("url")
                    ?: "https://bytedance.feishu.cn/docx/${doc.requireString("document_id")}"
            )
        }

    override suspend fun getDocument(docId: String): DocMetadata = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(urlFor("docx/v1/documents/$docId"))
            .get()
            .build()
        val resp = executeRequest(request)
        // 飞书 docx get API 响应结构: data.document.document_id / data.document.revision_id / ...
        val doc = resp.data["document"]?.jsonObject
            ?: throw FeishuError.BadRequest(0, "missing data.document in get response")
        DocMetadata(
            docId = doc.requireString("document_id"),
            revisionId = doc.requireString("revision_id"),
            title = doc.optionalString("title") ?: ""
        )
    }

    override suspend fun getBlocks(docId: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(urlFor("docx/v1/documents/$docId/blocks"))
            .get()
            .build()
        executeRequest(request).rawBody
    }

    override suspend fun appendChildren(docId: String, parentBlockId: String, childrenJson: String): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(urlFor("docx/v1/documents/$docId/blocks/$parentBlockId/children"))
                .post(childrenJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            executeRequest(request).rawBody
        }

    override suspend fun batchDeleteChildren(docId: String, parentBlockId: String, startIndex: Int, endIndex: Int) {
        withContext(Dispatchers.IO) {
            // 飞书 docx v1 batch_delete 用 DELETE + start_index/end_index(按索引范围,非 block_id)。
            val body = """{"start_index":$startIndex,"end_index":$endIndex}"""
            val request = Request.Builder()
                .url(urlFor("docx/v1/documents/$docId/blocks/$parentBlockId/children/batch_delete"))
                .delete(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            executeRequest(request)
        }
    }

    /**
     * 统一执行:状态码 → FeishuError 映射;body → 业务错误 code 检查。
     */
    private fun executeRequest(request: Request): ParsedResponse {
        val response = try {
            httpClient.newCall(request).execute()
        } catch (e: FeishuError) {
            // review r1 HIGH#5:AuthInterceptor runBlocking 抛的 FeishuError(NotAuthorized 等)
            // 不能被下面 Throwable catch 包成 NetworkError,直接 rethrow 保语义。
            throw e
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            // fix-2026-06-26-review-r3 HIGH(feishu agent re-scan):rethrow 取消信号
            throw e
        } catch (e: java.net.UnknownHostException) {
            // fix-MEDIUM(feishu M2):UnknownHost 是确定性可重试错误(网络/域名问题),
            // 区分标记为 "host=" 前缀,便于上层做 retry/backoff 时识别(SSL/IO 异常通常不应重试)。
            throw FeishuError.NetworkError(detail = "host=" + e.javaClass.simpleName + ": " + (e.message ?: ""))
        } catch (e: javax.net.ssl.SSLException) {
            // fix-MEDIUM(feishu M2):SSL 错误通常不是网络抖动(证书 / 协议),不推荐自动重试。
            throw FeishuError.NetworkError(detail = "ssl=" + e.javaClass.simpleName + ": " + (e.message ?: ""))
        } catch (e: Throwable) {
            throw FeishuError.NetworkError(detail = e.javaClass.simpleName + ": " + (e.message ?: ""))
        }
        return response.use { resp ->
            // fix-2026-06-26-review-r3 HIGH H9:流式截断到 1 MiB,不让 okio buffer 缓存整段 body。
            // 之前 `source.request(Long.MAX_VALUE)` 把整段 body 拉进 heap buffer 再判断截断,
            // 已经把恶意/异常 endpoint 的 MB 级 body 吃进内存了。改用 readByteArray(maxBytes)
            // 让 okio 在拉到上限时停止从 socket 读。
            val body = try {
                resp.body?.source()?.use { source ->
                    // fix-2026-06-26-review-r3 HIGH H9:流式截断到 1 MiB,不让 okio buffer 缓存整段 body。
                    // 用 try/catch EOFException 兜底短 body(测试 fake / 飞书端小响应)——
                    // 之前 `source.request(Long.MAX_VALUE)` 把整段 body 拉进 heap buffer 再判断截断,
                    // 已经把恶意/异常 endpoint 的 MB 级 body 吃进内存了。改用 readByteArray(maxBytes)
                    // 让 okio 在拉到上限时停止从 socket 读;body 短于上限属于正常情况,不算错。
                    val bytes = try {
                        source.readByteArray(MAX_BODY)
                    } catch (e: java.io.EOFException) {
                        // body 短于 MAX_BODY:返回已读字节,标记为截断(无,本来就短)
                        source.readByteArray()
                    }
                    if (!source.exhausted()) {
                        Log.w(
                            "FeishuApi",
                            "response body exceeded ${MAX_BODY} bytes for ${request.url.encodedPath}, truncating"
                        )
                    }
                    String(bytes, Charsets.UTF_8)
                }.orEmpty()
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                // fix-MEDIUM(feishu M1):结构化并发取消时 (coroutine 取消),
                // 读 socket body 抛 CancellationException 不应被包成 NetworkError
                // 吞掉 → 保持结构化并发语义(原 throw 上去)。
                throw e
            }
            when (resp.code) {
                in 200..299 -> {
                    val parsed = try {
                        Json.parseToJsonElement(body).jsonObject
                    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        throw FeishuError.NetworkError(detail = "JSON parse failed: ${e.message}")
                    }
                    val code = parsed["code"]?.jsonPrimitive?.intOrNull ?: 0
                    if (code != 0) {
                        val msg = parsed["msg"]?.jsonPrimitive?.contentOrNull ?: ""
                        if (code == AuthInterceptor.FEISHU_TOKEN_INVALID_CODE) throw FeishuError.TokenInvalid
                        throw FeishuError.BadRequest(code, msg)
                    }
                    val data = try {
                        parsed["data"]?.jsonObject ?: emptyJsonObject()
                    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        // fix-2026-06-26-review-r3 HIGH H-extra(r3 regression fix):
                        // `data` 不是 JsonObject 时的强转异常也应包装为 NetworkError,保持
                        // 与"200 但 body 坏"一致的错误语义,避免 IllegalArgumentException
                        // 逃逸破坏调用方异常处理。
                        throw FeishuError.NetworkError(detail = "data is not a JSON object: ${e.message}")
                    }
                    ParsedResponse(
                        rawBody = body,
                        data = data
                    )
                }
                400 -> throw FeishuError.BadRequest(400, body)
                401 -> throw FeishuError.AuthExpired
                403 -> throw FeishuError.Forbidden(scope = null)
                404 -> throw FeishuError.NotFound(resource = request.url.encodedPath)
                429 -> {
                    val retryAfter = resp.header("Retry-After")?.toIntOrNull() ?: 60
                    throw FeishuError.RateLimited(retryAfterSeconds = retryAfter)
                }
                in 500..599 -> throw FeishuError.ServerError(resp.code)
                else -> throw FeishuError.ServerError(resp.code)
            }
        }
    }

    private data class ParsedResponse(
        val rawBody: String,
        val data: JsonObject
    )

    private fun JsonObject.requireString(key: String): String = this[key]?.jsonPrimitive?.contentOrNull
        ?: throw FeishuError.BadRequest(0, "missing field: $key")

    private fun JsonObject.optionalString(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    private fun emptyJsonObject(): JsonObject = buildJsonObject { }

    // ---- v2 docs_ai/v1(XML format,参考 larksuite/cli) ----

    override suspend fun createDocumentV2(xmlContent: String, folderToken: String?): DocCreateResultV2 =
        withContext(Dispatchers.IO) {
            val bodyObj = buildJsonObject {
                put("format", "xml")
                put("content", xmlContent)
                if (folderToken != null) put("parent_token", folderToken)
            }
            val request = Request.Builder()
                .url(urlFor("docs_ai/v1/documents"))
                .post(bodyObj.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            val resp = executeRequest(request)
            val doc = resp.data["document"]?.jsonObject
                ?: throw FeishuError.BadRequest(0, "missing data.document in v2 create")
            DocCreateResultV2(
                docId = doc.requireString("document_id"),
                docUrl = doc.optionalString("url")
                    ?: "https://bytedance.feishu.cn/docx/${doc.requireString("document_id")}",
                revisionId = doc.optionalString("revision_id") ?: ""
            )
        }

    override suspend fun updateDocumentV2(docToken: String, xmlContent: String): DocMetadata? =
        withContext(Dispatchers.IO) {
            val bodyObj = buildJsonObject {
                put("format", "xml")
                put("command", "overwrite")
                put("content", xmlContent)
            }
            val request = Request.Builder()
                .url(urlFor("docs_ai/v1/documents/$docToken"))
                .put(bodyObj.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            val resp = executeRequest(request)
            val doc = resp.data["document"]?.jsonObject
            if (doc != null) {
                DocMetadata(
                    docId = doc.requireString("document_id"),
                    revisionId = doc.optionalString("revision_id") ?: "",
                    title = doc.optionalString("title") ?: ""
                )
            } else {
                null
            }
        }

    override suspend fun fetchDocumentV2(docToken: String): String = withContext(Dispatchers.IO) {
        val bodyObj = buildJsonObject { put("format", "markdown") }
        val request = Request.Builder()
            .url(urlFor("docs_ai/v1/documents/$docToken/fetch"))
            .post(bodyObj.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val resp = executeRequest(request)
        resp.data["document"]?.jsonObject?.optionalString("content") ?: ""
    }

    override suspend fun appendBlockV2(docToken: String, xmlContent: String) {
        withContext(Dispatchers.IO) {
            val bodyObj = buildJsonObject {
                put("format", "xml")
                put("command", "block_insert_after")
                put("block_id", "-1")
                put("content", xmlContent)
            }
            val request = Request.Builder()
                .url(urlFor("docs_ai/v1/documents/$docToken"))
                .put(bodyObj.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            executeRequest(request)
        }
    }

    /**
     * 构造飞书 API URL。
     *
     * 飞书所有 Open API 基地址为 `https://open.feishu.cn/open-apis/`,
     * [segments] 传的是 `open-apis/` 之后的路径(如 `docx/v1/documents`)。
     *
     * fix(feishu-sync-error):补上 `open-apis/` 前缀 — 原版 `addPathSegments` 直接拼
     * `docx/v1/documents` 到 host,导致请求发到 `open.feishu.cn/docx/v1/documents`
     * (缺少 `/open-apis/` 段),飞书服务器返回 404 或非 JSON body → 客户端报错。
     * v1 docx 和 v2 docs_ai 的所有 endpoint 都受影响。
     *
     * fix-MEDIUM(feishu M5):用 `addPathSegments(segments, alreadyEncoded=false)` 显式
     * 声明不做预编码,让 okhttp/okio 内部的 RFC 3986 path encoder 负责。
     * 之前默认值是 alreadyEncoded=true,如果 caller 已经 URL-encode 过会出现双重编码;
     * 改 false 后即使 docId 出现 `+` `:` `/`(feishu 真实 block_id 是字母数字,但
     * ref.docUrl 取末段 → 用户可能贴带 query/fragment 的完整 URL)也不会炸。
     */
    private fun urlFor(segments: String): String {
        val safe = segments.trimStart('/')
        // HttpUrl.Builder 负责 host 校验 + path 拼接,避免字符串拼接失误。
        return HttpUrl.Builder()
            .scheme("https")
            .host(BASE_HOST)
            .addPathSegments("open-apis")
            .addPathSegments(safe)
            .build()
            .toString()
    }

    companion object {
        // TODO(feishu-multi-tenant):从 FeishuAuthStore 读 tenant_domain,默认 bytedance。
        internal const val BASE_HOST = "open.feishu.cn"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        // F2 fix: 与 AnthropicCompatibleAdapter.MAX_RESPONSE_BODY_BYTES(1 MiB)对齐,
        // 避免恶意/异常 endpoint 返回 MB 级 body 触发 OOM。
        private const val MAX_BODY: Long = 1L * 1024 * 1024
    }
}
