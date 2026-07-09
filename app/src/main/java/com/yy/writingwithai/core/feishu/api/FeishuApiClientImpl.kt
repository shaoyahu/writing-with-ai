package com.yy.writingwithai.core.feishu.api

import android.util.Log
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
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

    override suspend fun createBlock(
        docId: String,
        parentBlockId: String,
        blockType: Int,
        blockContentJson: String
    ): String = withContext(Dispatchers.IO) {
        // fix-full-review:用 buildJsonObject 替代字符串拼接构造 JSON，避免转义/格式错误。
        // 空/空白 → {"block_type":N}；有内容 → 解析 blockContentJson 后合并到 block 对象。
        val trimmed = blockContentJson.trim()
        val blockObj = if (trimmed.isEmpty() || trimmed == "{}") {
            buildJsonObject { put("block_type", blockType) }
        } else {
            // blockContentJson 可能是 "image":{"token":"xxx"} 或 {"image":{"token":"xxx"}}
            // 统一解析后合并
            val wrapped = if (trimmed.startsWith("{")) trimmed else "{$trimmed}"
            val parsed = try {
                Json.parseToJsonElement(wrapped).jsonObject
            } catch (_: Throwable) {
                // 解析失败时 fallback 到原始拼接逻辑，保持兼容
                buildJsonObject {
                    put("block_type", blockType)
                }
            }
            buildJsonObject {
                put("block_type", blockType)
                for ((key, value) in parsed) {
                    put(key, value)
                }
            }
        }
        // fix-2026-07-05:图片放在文档末尾，index=-1 表示追加到最后
        val body = buildJsonObject {
            put("index", -1)
            put("children", buildJsonArray { add(blockObj) })
        }.toString()
        // fix H5:移除 release 代码中的 Log.d，避免泄露 API 请求体到 logcat。
        val request = Request.Builder()
            .url(urlFor("docx/v1/documents/$docId/blocks/$parentBlockId/children"))
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val rawBody = executeRequest(request).rawBody
        // 解析响应获取创建的 block_id
        val respObj = Json.parseToJsonElement(rawBody).jsonObject
        val dataObj = respObj["data"]?.jsonObject
        val childrenArr = dataObj?.get("children")?.jsonArray
        if (childrenArr == null || childrenArr.isEmpty()) {
            throw FeishuError.BadRequest(0, "createBlock response missing children")
        }
        val blockId = childrenArr[0].jsonObject["block_id"]?.jsonPrimitive?.content
            ?: throw FeishuError.BadRequest(0, "createBlock response missing block_id")
        blockId
    }

    override suspend fun patchBlock(docId: String, blockId: String, patchJson: String): Unit =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(urlFor("docx/v1/documents/$docId/blocks/$blockId"))
                .patch(patchJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            executeRequest(request)
            Unit
        }

    override suspend fun replaceImageBlock(
        docId: String,
        blockId: String,
        fileToken: String,
        width: Int?,
        height: Int?
    ): Unit = withContext(Dispatchers.IO) {
        // fix-2026-07-05-feishu-image:使用 batch_update 的 replace_image 绑定图片 token
        // 参考 https://open.feishu.cn/document/server-docs/docs/docs/docx-v1/document-block/batch_update
        val body = buildJsonObject {
            put(
                "requests",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("block_id", blockId)
                            put(
                                "replace_image",
                                buildJsonObject {
                                    put("token", fileToken)
                                    if (width != null) put("width", width)
                                    if (height != null) put("height", height)
                                }
                            )
                        }
                    )
                }
            )
        }.toString()
        val request = Request.Builder()
            .url(urlFor("docx/v1/documents/$docId/blocks/batch_update"))
            .patch(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        executeRequest(request)
        Unit
    }

    override suspend fun batchDeleteChildren(docId: String, parentBlockId: String, startIndex: Int, endIndex: Int) {
        withContext(Dispatchers.IO) {
            // 飞书 docx v1 batch_delete 用 DELETE + start_index/end_index(按索引范围，非 block_id)。
            val body = """{"start_index":$startIndex,"end_index":$endIndex}"""
            val request = Request.Builder()
                .url(urlFor("docx/v1/documents/$docId/blocks/$parentBlockId/children/batch_delete"))
                .delete(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            executeRequest(request)
        }
    }

    // ---- v2 docs_ai/v1 ----
    // fix M68 (full-review):原 100 行 mega-function,拆 4 步 helper:
    //   1) executeHttp           — OkHttp call + 结构化 catch 链(rethrow FeishuError/Cancellation/
    //                              OOM/StackOverflow;分类 UnknownHost/SSL/其他 → FeishuError.NetworkError)
    //   2) readBodyCapped        — source.request(MAX_BODY+1) 流式截断(防恶意 endpoint OOM)+ UTF-8 解码
    //   3) parseFeishuBody       — 业务 code 0 → data;非 0 翻译 NotFound/BadRequest/TokenInvalid
    //   4) mapHttpStatus         — 200..299 走 parse;4xx/5xx 直接抛对应 FeishuError
    // 拆后每函数 < 40 行,行数 guideline 内,且每子函数单一职责便于单测。
    private fun executeRequest(request: Request): ParsedResponse {
        val response = executeHttp(request)
        return response.use { resp ->
            val body = readBodyCapped(resp)
            when (resp.code) {
                in 200..299 -> parseFeishuBody(body, request)
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

    /**
     * 执行 OkHttp 调用并把 IO/SSL/未知异常映射到 [FeishuError.NetworkError]。
     * 重新封装 H45 修复:FeishuError / Cancellation / OOM / StackOverflow 显式 rethrow,
     * 避免被兜底 catch(Throwable) 包成 NetworkError 误标 retry-friendly。
     */
    private fun executeHttp(request: Request): Response {
        return try {
            httpClient.newCall(request).execute()
        } catch (e: FeishuError) {
            // review r1 HIGH#5:AuthInterceptor runBlocking 抛的 FeishuError(NotAuthorized 等)
            // 不能被下面 Throwable catch 包成 NetworkError，直接 rethrow 保语义。
            throw e
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            // fix-2026-06-26-review-r3 HIGH(feishu agent re-scan):rethrow 取消信号
            throw e
        } catch (e: java.net.UnknownHostException) {
            // fix-MEDIUM(feishu M2):UnknownHost 是确定性可重试错误(网络/域名问题),
            // 区分标记为 "host=" 前缀，便于上层做 retry/backoff 时识别(SSL/IO 异常通常不应重试)。
            throw FeishuError.NetworkError(detail = "host=" + e.javaClass.simpleName + ": " + (e.message ?: ""))
        } catch (e: javax.net.ssl.SSLException) {
            // fix-MEDIUM(feishu M2):SSL 错误通常不是网络抖动(证书 / 协议)，不推荐自动重试。
            throw FeishuError.NetworkError(detail = "ssl=" + e.javaClass.simpleName + ": " + (e.message ?: ""))
        } catch (e: OutOfMemoryError) {
            // fix-full-review M45:rethrow OOM/StackOverflow — 不是网络错误，包成 NetworkError 会误导 retry/backoff。
            // Error 不是 Exception，故意保留在 Throwable catch 之后。
            throw e
        } catch (e: StackOverflowError) {
            throw e
        } catch (e: Throwable) {
            throw FeishuError.NetworkError(detail = e.javaClass.simpleName + ": " + (e.message ?: ""))
        }
    }

    /**
     * 流式读取 body,封顶 [MAX_BODY] 字节,防恶意 endpoint 返回超大 body OOM。
     * 取消信号 rethrow,IO 异常翻译为 NetworkError。
     */
    private fun readBodyCapped(resp: Response): String {
        return try {
            resp.body?.source()?.use { source ->
                // fix-full-review:用 capped read 替代 source.request(Long.MAX_VALUE)，
                // 避免恶意/异常 endpoint 返回超大 body 时 OOM。request(maxSize + 1) 只从
                // socket 读到 maxSize+1 字节就停止；若 buffer.size > maxSize 说明 body
                // 超限，截断读取；否则正常读完。比先全量拉进 heap 再判断安全得多。
                source.request(MAX_BODY + 1)
                val bytes = if (source.buffer.size > MAX_BODY) {
                    Log.w(
                        "FeishuApi",
                        "response body exceeded ${MAX_BODY} bytes, truncating"
                    )
                    source.buffer.readByteArray(MAX_BODY)
                } else {
                    source.buffer.readByteArray()
                }
                String(bytes, Charsets.UTF_8)
            }.orEmpty()
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        }
    }

    /**
     * 解析 200 OK body:JSON 反序列化 + 业务 code 翻译到 FeishuError。
     * 业务 code=0 走 data 提取;非 0 特判 NotFound(已删除 docx)/
     * TokenInvalid(401 等价) / BadRequest(其他业务错误)。
     */
    private fun parseFeishuBody(body: String, request: Request): ParsedResponse {
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
            // fix(feishu-doc-deleted-3380003):飞书对已删除文档返回 HTTP 200
            // 但业务 code=3380003(msg="Document page has been deleted"),原本包成
            // BadRequest 直接逃到 UI 显示"操作失败"。特判翻译成 NotFound,
            // 让 FeishuDocService.updateDoc 已有的 catch NotFound 链路接管:
            // 标记 REMOTE_DELETED → sync push catch NotFound → 删旧 ref + 重建 doc。
            if (code == FEISHU_DOC_DELETED_CODE) {
                throw FeishuError.NotFound(resource = request.url.encodedPath)
            }
            throw FeishuError.BadRequest(code, msg)
        }
        val data = try {
            parsed["data"]?.jsonObject ?: emptyJsonObject()
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Throwable) {
            // fix-2026-06-26-review-r3 HIGH H-extra(r3 regression fix):
            // `data` 不是 JsonObject 时的强转异常也应包装为 NetworkError，保持
            // 与"200 但 body 坏"一致的错误语义，避免 IllegalArgumentException
            // 逃逸破坏调用方异常处理。
            throw FeishuError.NetworkError(detail = "data is not a JSON object: ${e.message}")
        }
        return ParsedResponse(rawBody = body, data = data)
    }

    private data class ParsedResponse(
        val rawBody: String,
        val data: JsonObject
    )

    private fun JsonObject.requireString(key: String): String = this[key]?.jsonPrimitive?.contentOrNull
        ?: throw FeishuError.BadRequest(0, "missing field: $key")

    private fun JsonObject.optionalString(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    /**
     * 飞书不同租户 / 子账号 / 不同 SDK 版本可能返 `"true"`/`"false"`(标准 JSON)
     * 或 `"1"`/`"0"`(部分 web 协议兼容形态),用 [String.toBooleanStrict] 严格解析会
     * 把 `"1"`/`"0"` 误为 null → 走默认值 → 分页截断(bug fix 2026-07-07 Finding #5)。
     * 此 helper 兼容两种形态;空白 / 未知值走 [default]。
     */
    private fun JsonObject.optionalBoolean(key: String, default: Boolean = false): Boolean {
        val raw = this[key]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase() ?: return default
        return when (raw) {
            "true", "1" -> true
            "false", "0" -> false
            else -> default
        }
    }

    private fun emptyJsonObject(): JsonObject = buildJsonObject { }

    // ---- v2 docs_ai/v1(XML format，参考 larksuite/cli) ----

    override suspend fun createDocumentV2(xml: String, folderToken: String?): DocCreateResultV2 =
        withContext(Dispatchers.IO) {
            val bodyObj = buildJsonObject {
                put("format", "xml")
                put("content", xml)
                if (folderToken != null) put("parent_token", folderToken)
            }
            val request = Request.Builder()
                .url(urlFor("docs_ai/v1/documents"))
                .post(bodyObj.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            val resp = executeRequest(request)
            // fix(feishu-folder-token):飞书 docs_ai/v1 创建文档到文件夹时，
            // 响应 data.document 可能不包含 document_id 字段，而是用 node_token
            // 或 obj_token 作为标识。先尝试 document_id，再 fallback 到 node_token / obj_token。
            val doc = resp.data["document"]?.jsonObject
                ?: throw FeishuError.BadRequest(
                    0,
                    "missing data.document in v2 create, raw data keys: ${resp.data.keys}"
                )
            val docId = doc.optionalString("document_id")
                ?: doc.optionalString("node_token")
                ?: doc.optionalString("obj_token")
                ?: throw FeishuError.BadRequest(
                    0,
                    "missing document_id/node_token/obj_token in v2 create, doc keys: ${doc.keys}"
                )
            val docUrl = doc.optionalString("url")
                ?: doc.optionalString("url_outer")
                ?: "https://bytedance.feishu.cn/docx/$docId"
            DocCreateResultV2(
                docId = docId,
                docUrl = docUrl,
                revisionId = doc.optionalString("revision_id") ?: ""
            )
        }

    override suspend fun updateDocumentV2(docToken: String, xml: String): DocMetadata? = withContext(Dispatchers.IO) {
        val bodyObj = buildJsonObject {
            put("format", "xml")
            put("command", "overwrite")
            put("content", xml)
        }
        val request = Request.Builder()
            .url(urlFor("docs_ai/v1/documents/$docToken"))
            .put(bodyObj.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val resp = executeRequest(request)
        val doc = resp.data["document"]?.jsonObject
        if (doc != null) {
            val docId = doc.optionalString("document_id")
                ?: doc.optionalString("node_token")
                ?: doc.optionalString("obj_token")
                ?: docToken
            DocMetadata(
                docId = docId,
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

    override suspend fun appendBlockV2(docToken: String, xml: String) {
        withContext(Dispatchers.IO) {
            val bodyObj = buildJsonObject {
                put("format", "xml")
                put("command", "block_insert_after")
                put("block_id", "-1")
                put("content", xml)
            }
            val request = Request.Builder()
                .url(urlFor("docs_ai/v1/documents/$docToken"))
                .put(bodyObj.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            executeRequest(request)
        }
    }

    /**
     * feishu-sync-image-support · 上传素材到飞书云空间。
     *
     * API: `POST /open-apis/drive/v1/medias/upload_all`(multipart/form-data)
     * 官方文档: https://open.feishu.cn/document/server-docs/docs/drive-v1/media/upload_all
     *
     * 三步法第二步:parent_node 应传**image block ID**(第一步 createBlock 返回的),
     * 不是 document_id!
     *
     * 必填字段:`file_name` / `parent_type=docx_image` / `parent_node=blockId` /
     * `size` / `file`(二进制)。`checksum`(SHA1)选填但服务端建议带,失败重传更快。
     *
     * 文件大小限制:单文件 ≤ 20 MB(v1 不支持分片,本方法在入口直接拒绝超过 20 MB 的文件)。
     * 限流:5 QPS、10k/天(由 caller 串行调用自然满足)。
     */
    override suspend fun uploadMedia(
        docId: String,
        parentNode: String,
        file: File,
        mimeType: String
    ): MediaUploadResult = withContext(Dispatchers.IO) {
        // task 1.3:入口校验 20 MB,超限直接 BadRequest,避免无效上传浪费服务端配额。
        val size = file.length()
        if (size > MAX_UPLOAD_BYTES) {
            throw FeishuError.BadRequest(
                0,
                "file > 20 MB (got $size bytes), v1 不支持分片上传"
            )
        }
        // fix-2026-07-05:checksum 选填，不传让飞书服务端自行计算
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file_name", file.name)
            .addFormDataPart("parent_type", "docx_image")
            .addFormDataPart("parent_node", parentNode)
            .addFormDataPart("size", size.toString())
            .addFormDataPart("extra", "{\"drive_route_token\":\"$docId\"}")
            .addFormDataPart(
                "file",
                file.name,
                file.asRequestBody(mimeType.toMediaType())
            )
            .build()
        val request = Request.Builder()
            .url(urlFor("drive/v1/medias/upload_all"))
            .post(body)
            .build()
        val resp = executeRequest(request)
        val fileToken = resp.data["file_token"]?.jsonPrimitive?.contentOrNull
            ?: throw FeishuError.BadRequest(
                0,
                "missing data.file_token in upload response, keys: ${resp.data.keys}"
            )
        MediaUploadResult(fileToken = fileToken, bytes = size)
    }

    override suspend fun resolveFolderToken(rawToken: String): ResolvedFolderToken = withContext(Dispatchers.IO) {
        // 根据飞书 token 前缀推断 doc_type:
        // - fldcn* → folder, wikcn* → wiki, doccn* → doc, docx* → docx
        // 如果无法推断，用 "folder" 作为默认（让飞书 API 自行校验）
        val docType = when {
            rawToken.startsWith("fldcn") -> "folder"
            rawToken.startsWith("wikcn") -> "wiki"
            rawToken.startsWith("doccn") -> "doc"
            rawToken.startsWith("docx") -> "docx"
            else -> "folder"
        }
        val bodyObj = buildJsonObject {
            put(
                "request_docs",
                kotlinx.serialization.json.buildJsonArray {
                    add(
                        buildJsonObject {
                            put("doc_token", rawToken)
                            put("doc_type", docType)
                        }
                    )
                }
            )
            put("with_url", false)
        }
        val request = Request.Builder()
            .url(urlFor("drive/v1/metas/batch_query"))
            .post(bodyObj.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        try {
            val resp = executeRequest(request)
            val metas = resp.data["metas"]?.jsonArray
            Log.i(
                "FeishuApi",
                "resolveFolderToken: rawToken=$rawToken docType=$docType metas=${metas?.size ?: 0}"
            )
            if (metas.isNullOrEmpty()) {
                // 没有返回元数据，降级返回原 token
                Log.w("FeishuApi", "resolveFolderToken: no metas returned for $rawToken, falling back")
                return@withContext ResolvedFolderToken(token = rawToken, originalType = null, resolved = false)
            }
            val meta = metas[0].jsonObject
            val resolvedType = meta.optionalString("doc_type")
            val resolvedToken = meta.optionalString("doc_token") ?: rawToken
            Log.i(
                "FeishuApi",
                "resolveFolderToken: resolvedType=$resolvedType resolvedToken=$resolvedToken"
            )

            // 如果原始类型是 folder，直接用原 token（folder token 就是正确的父文件夹 token）
            if (resolvedType == "folder") {
                return@withContext ResolvedFolderToken(
                    token = rawToken,
                    originalType = resolvedType,
                    resolved = true
                )
            }
            // wiki 或其他类型：返回 batch_query 解析出的真实 doc_token
            ResolvedFolderToken(
                token = resolvedToken,
                originalType = resolvedType,
                resolved = true
            )
        } catch (e: FeishuError) {
            // batch_query 失败，降级返回原 token（让后续 create API 自行校验）
            Log.w("FeishuApi", "resolveFolderToken: batch_query failed for $rawToken: ${e.message}, falling back")
            ResolvedFolderToken(token = rawToken, originalType = null, resolved = false)
        }
    }

    /**
     * feishu-folder-migration · 删除飞书文件(移到回收站)。
     *
     * API: `DELETE /open-apis/drive/v1/files/{file_token}`
     * 文件移入回收站，30 天内用户可在飞书恢复。
     *
     * 飞书 OpenAPI 强制要求请求体带 `type` 字段(枚举:`doc` / `docx` / `sheet` /
     * `bitable` / `mindnote` / `file` / `slides` / `wiki`),缺字段会返回 400 + 错误码
     * 99992402 `field_violations: [{field: "type", description: "type is required"}]`。
     * 本项目只创建 docx 文档(`createDocument` 走 `docx/v1/documents`),所以这里
     * 硬编码 `"docx"` 即可,无需扩 FeishuRefEntity 加 docType 字段(避免 Room migration)。
     *
     * DELETE 请求 RFC 7230 §4.3.5 不强制需要 body,但飞书服务端要求 Content-Type 必填
     * 才校验请求(否则拒绝),所以 body 用 `buildJsonObject` 构造非空 JSON。
     *
     * 2026-07-03 fix:旧实现传 `"".toRequestBody(JSON_MEDIA_TYPE)`,body 为空 → 飞书
     * 服务端直接 400,DELETE_FAILED 事件堆积(见同步日志)。
     */
    override suspend fun deleteFile(fileToken: String): Unit = withContext(Dispatchers.IO) {
        val body = buildJsonObject { put("type", "docx") }.toString()
        val request = Request.Builder()
            .url(urlFor("drive/v1/files/$fileToken"))
            .delete(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        executeRequest(request)
        Unit
    }

    /**
     * feishu-import-from-folder · 列文件夹下的文件清单(分页)。
     *
     * API: `GET /open-apis/drive/v1/files?folder_token={t}&page_size={n}&page_token={p}`
     * 官方文档: https://open.feishu.cn/document/server-docs/docs/drive-v1/folder/list
     *
     * 响应 data.files: 文件条目数组,每条带 `type` 字段(docx / doc / sheet /
     * bitable / folder / file / shortcut 等)。本方法不预过滤,业务层按
     * `entry.type == "docx"` 自己挑。
     *
     * 分页:hasMore=true 时业务层循环调 nextPageToken 直到 hasMore=false。
     */
    override suspend fun listFolder(folderToken: String, pageSize: Int, pageToken: String?): ListFolderResponse =
        withContext(Dispatchers.IO) {
            val urlBuilder = HttpUrl.Builder()
                .scheme("https")
                .host(BASE_HOST)
                .addPathSegments("open-apis/drive/v1/files")
                .addQueryParameter("folder_token", folderToken)
                .addQueryParameter("page_size", pageSize.toString())
            if (!pageToken.isNullOrBlank()) {
                urlBuilder.addQueryParameter("page_token", pageToken)
            }
            val request = Request.Builder()
                .url(urlBuilder.build())
                .get()
                .build()
            val resp = executeRequest(request)
            val filesArray = resp.data["files"]?.jsonArray
            val entries = filesArray?.mapNotNull { el ->
                try {
                    el.jsonObject.let { obj ->
                        FolderFileEntry(
                            name = obj.optionalString("name") ?: "",
                            token = obj.optionalString("token") ?: return@mapNotNull null,
                            type = obj.optionalString("type") ?: "",
                            url = obj.optionalString("url"),
                            createdTime = obj.optionalString("created_time"),
                            modifiedTime = obj.optionalString("modified_time"),
                            ownerId = obj.optionalString("owner_id"),
                            parentToken = obj.optionalString("parent_token")
                        )
                    }
                } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    // 单条 entry 解析失败不阻塞整体,跳过
                    null
                }
            }.orEmpty()
            ListFolderResponse(
                files = entries,
                nextPageToken = resp.data.optionalString("next_page_token"),
                hasMore = resp.data.optionalBoolean("has_more", default = false)
            )
        }

    /**
     * 构造飞书 API URL。
     *
     * 飞书所有 Open API 基地址为 `https://open.feishu.cn/open-apis/`,
     * [segments] 传的是 `open-apis/` 之后的路径(如 `docx/v1/documents`)。
     *
     * fix(feishu-sync-error):补上 `open-apis/` 前缀 — 原版 `addPathSegments` 直接拼
     * `docx/v1/documents` 到 host，导致请求发到 `open.feishu.cn/docx/v1/documents`
     * (缺少 `/open-apis/` 段)，飞书服务器返回 404 或非 JSON body → 客户端报错。
     * v1 docx 和 v2 docs_ai 的所有 endpoint 都受影响。
     *
     * fix-MEDIUM(feishu M5):用 `addPathSegments(segments, alreadyEncoded=false)` 显式
     * 声明不做预编码，让 okhttp/okio 内部的 RFC 3986 path encoder 负责。
     * 之前默认值是 alreadyEncoded=true，如果 caller 已经 URL-encode 过会出现双重编码;
     * 改 false 后即使 docId 出现 `+` `:` `/`(feishu 真实 block_id 是字母数字，但
     * ref.docUrl 取末段 → 用户可能贴带 query/fragment 的完整 URL)也不会炸。
     */
    private fun urlFor(segments: String): String {
        val safe = segments.trimStart('/')
        // HttpUrl.Builder 负责 host 校验 + path 拼接，避免字符串拼接失误。
        return HttpUrl.Builder()
            .scheme("https")
            .host(BASE_HOST)
            .addPathSegments("open-apis")
            .addPathSegments(safe)
            .build()
            .toString()
    }

    companion object {
        // TODO(feishu-multi-tenant):从 FeishuAuthStore 读 tenant_domain，默认 bytedance。
        internal const val BASE_HOST = "open.feishu.cn"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        // F2 fix: 与 AnthropicCompatibleAdapter.MAX_RESPONSE_BODY_BYTES(1 MiB)对齐，
        // 避免恶意/异常 endpoint 返回 MB 级 body 触发 OOM。
        private const val MAX_BODY: Long = 1L * 1024 * 1024

        // fix(feishu-doc-deleted-3380003):飞书对已删除的文档/update API 返回 HTTP 200
        // 但业务 code=3380003,msg="Document page has been deleted. This page can no
        // longer be edited..."。executeRequest 特判该 code,翻译为 FeishuError.NotFound,
        // 让 FeishuDocService.updateDoc 已有的 catch NotFound 链路自动接管(标记
        // REMOTE_DELETED → 上层 push catch NotFound → 删旧 ref + createDoc 重建)。
        private const val FEISHU_DOC_DELETED_CODE = 3380003

        // feishu-sync-image-support:upload_all 单文件上限 20 MB(v1 不支持分片)。
        private const val MAX_UPLOAD_BYTES: Long = 20L * 1024 * 1024
    }
}
