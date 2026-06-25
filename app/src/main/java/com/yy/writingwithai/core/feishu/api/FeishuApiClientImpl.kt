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
        } catch (e: Throwable) {
            throw FeishuError.NetworkError(detail = e.javaClass.simpleName + ": " + (e.message ?: ""))
        }
        return response.use { resp ->
            // F2 fix H12:review r1 body OOM — resp.body.string() 一次性把整段 body 读进 heap,
            // 飞书服务端如果返回 MB 级大文档(理论上不受限)会 OOM。统一 1 MiB cap,与
            // AnthropicCompatibleAdapter.MAX_RESPONSE_BODY_BYTES 对齐。
            // 用 okio source.request(Long.MAX_VALUE) 拉完所有 body 到 buffer,再判断
            // buffer.size 决定是否截断到 MAX_BODY。避开 readUtf8(byteCount) 在源不足时
            // 抛 EOFException 的坑。
            val body = resp.body?.source()?.use { source ->
                source.request(Long.MAX_VALUE)
                if (source.buffer.size > MAX_BODY) {
                    Log.w(
                        "FeishuApi",
                        "response body exceeded ${MAX_BODY} bytes for ${request.url.encodedPath}, truncating"
                    )
                    source.buffer.readUtf8(MAX_BODY)
                } else {
                    source.buffer.readUtf8()
                }
            }.orEmpty()
            when (resp.code) {
                in 200..299 -> {
                    val parsed = try {
                        Json.parseToJsonElement(body).jsonObject
                    } catch (e: Throwable) {
                        throw FeishuError.NetworkError(detail = "JSON parse failed: ${e.message}")
                    }
                    val code = parsed["code"]?.jsonPrimitive?.intOrNull ?: 0
                    if (code != 0) {
                        val msg = parsed["msg"]?.jsonPrimitive?.contentOrNull ?: ""
                        if (code == 99991663) throw FeishuError.TokenInvalid
                        throw FeishuError.BadRequest(code, msg)
                    }
                    ParsedResponse(
                        rawBody = body,
                        data = parsed["data"]?.jsonObject ?: emptyJsonObject()
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
     * 构造飞书 API URL。`BASE_HOST` 是硬编码到飞书默认 host(未来若需多租户,
     * 从 `FeishuAuthStore` 读 `tenant_domain` 拼 host,见 M4 TODO)。
     * `segments` 内部不再做额外编码:Feishu block_id 是字母数字,无 path 风险。
     */
    private fun urlFor(segments: String): String {
        val safe = segments.trimStart('/')
        // HttpUrl.Builder 负责 host 校验 + path 拼接,避免字符串拼接失误。
        return HttpUrl.Builder()
            .scheme("https")
            .host(BASE_HOST)
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
