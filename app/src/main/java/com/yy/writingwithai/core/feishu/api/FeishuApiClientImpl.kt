package com.yy.writingwithai.core.feishu.api

import javax.inject.Inject
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

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
    private val httpClient: OkHttpClient
) : FeishuApiClient {

    override suspend fun createDocument(title: String, folderToken: String?): DocCreateResult =
        withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                put("title", title)
                if (folderToken != null) put("folder_token", folderToken)
            }.toString()
            val request = Request.Builder()
                .url("$BASE_URL/docx/v1/documents")
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            val resp = executeRequest(request)
            val data = resp.data
            DocCreateResult(
                docId = data.requireString("document_id"),
                docUrl = data.optionalString("url")
                    ?: "https://bytedance.feishu.cn/docx/${data.requireString("document_id")}"
            )
        }

    override suspend fun getDocument(docId: String): DocMetadata = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/docx/v1/documents/$docId")
            .get()
            .build()
        val resp = executeRequest(request)
        val data = resp.data
        DocMetadata(
            docId = data.requireString("document_id"),
            revisionId = data.requireString("revision_id"),
            title = data.requireString("title")
        )
    }

    override suspend fun getBlocks(docId: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/docx/v1/documents/$docId/blocks")
            .get()
            .build()
        executeRequest(request).rawBody
    }

    override suspend fun appendChildren(docId: String, parentBlockId: String, childrenJson: String): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$BASE_URL/docx/v1/documents/$docId/blocks/$parentBlockId/children")
                .post(childrenJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            executeRequest(request).rawBody
        }

    override suspend fun batchDeleteChildren(docId: String, parentBlockId: String, childIds: String) {
        withContext(Dispatchers.IO) {
            val body = """{"block_ids":"$childIds"}"""
            val request = Request.Builder()
                .url("$BASE_URL/docx/v1/documents/$docId/blocks/$parentBlockId/children/batch_delete")
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
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
        } catch (e: Throwable) {
            throw FeishuError.NetworkError(detail = e.javaClass.simpleName + ": " + (e.message ?: ""))
        }
        return response.use { resp ->
            val body = resp.body?.string().orEmpty()
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

    companion object {
        internal const val BASE_URL = "https://open.feishu.cn/open-apis"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
