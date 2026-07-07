package com.yy.writingwithai.core.feishu.sync

import android.util.Log
import com.yy.writingwithai.core.data.db.entity.NoteAttachmentEntity
import com.yy.writingwithai.core.feishu.api.skipFeishuAuth
import com.yy.writingwithai.core.media.AttachmentStore
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * feishu-import-from-folder · 飞书文档 markdown 图片下载器。
 *
 * 解析 markdown / HTML 中的图片 url → 串行下载到本地附件 → 替换 markdown 里的 url 为本地路径。
 *
 * 关键设计(design §4):
 * - 解析两种语法:`![alt](url)` 和 `<img ... src="url" ...>`
 * - 扩展名走 Content-Type(`image/png` → `png`),fallback `img`
 * - 请求加 `X-No-Auth-Retry: 1` 跳过 AuthInterceptor(飞书 CDN 用签名 query,加 Bearer 反而 401)
 * - 复用 @Named("feishu") OkHttpClient,**不**新建 client
 * - 单图 > 20MB 跳过 → 占位符
 * - 失败 → 占位符 `[图片下载失败]` + failedUrls
 * - 全部串行,不并发
 */
@Singleton
class FeishuImageDownloader
@Inject
constructor(
    private val attachmentStore: AttachmentStore
) {

    data class DownloadResult(
        val updatedMarkdown: String,
        val attachments: List<NoteAttachmentEntity>,
        val failedUrls: List<String>
    )

    /**
     * 入口:解析 → 下载 → 替换。
     *
     * @param markdown 原始 markdown
     * @param noteId 新笔记 ID(用于附件目录归属)
     * @param httpClient 已构造的 OkHttpClient(由调用方注入,避免此处依赖 Hilt)
     */
    suspend fun downloadAndInline(markdown: String, noteId: String, httpClient: OkHttpClient): DownloadResult {
        val urls = extractUrls(markdown)
        if (urls.isEmpty()) {
            return DownloadResult(updatedMarkdown = markdown, attachments = emptyList(), failedUrls = emptyList())
        }

        val urlToLocalPath = mutableMapOf<String, String>()
        val attachments = mutableListOf<NoteAttachmentEntity>()
        val failed = mutableListOf<String>()
        val now = System.currentTimeMillis()

        for ((idx, url) in urls.withIndex()) {
            val result = downloadOne(url, noteId, httpClient)
            if (result != null) {
                urlToLocalPath[url] = result.localPath
                attachments += NoteAttachmentEntity(
                    id = result.attachmentId,
                    noteId = noteId,
                    mimeType = result.mimeType,
                    localPath = result.localPath,
                    fileSize = result.bytes,
                    createdAt = now + idx
                )
            } else {
                failed += url
            }
        }

        val updated = replaceInMarkdown(markdown, urlToLocalPath, failed)
        return DownloadResult(
            updatedMarkdown = updated,
            attachments = attachments,
            failedUrls = failed
        )
    }

    /**
     * 提取 markdown + HTML 中所有图片 url,去重并保留首次出现顺序。
     *
     * design §4.1:只处理 `![...](url)` 和 `<img src="url">`,不处理 srcset / inline style。
     */
    fun extractUrls(markdown: String): List<String> {
        if (markdown.isEmpty()) return emptyList()
        val seen = LinkedHashSet<String>()
        for (match in MD_IMG.findAll(markdown)) {
            val url = match.groupValues[2].trim()
            if (url.isNotBlank() && isHttpUrl(url)) seen += url
        }
        for (match in HTML_IMG.findAll(markdown)) {
            val url = match.groupValues[1].trim()
            if (url.isNotBlank() && isHttpUrl(url)) seen += url
        }
        return seen.toList()
    }

    private fun isHttpUrl(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)

    private data class Downloaded(
        val attachmentId: String,
        val localPath: String,
        val mimeType: String,
        val bytes: Long
    )

    /**
     * 下载单张图。捕获所有异常返回 null。
     */
    private suspend fun downloadOne(url: String, noteId: String, client: OkHttpClient): Downloaded? {
        val request = Request.Builder()
            // review 2026-07-07 Finding #15:用 [skipFeishuAuth] 扩展而非硬编码 "X-No-Auth-Retry"/"1",
// 避免 AuthInterceptor 内部改名后下载器静默失效。
            .url(url)
            .skipFeishuAuth()
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "downloadOne: HTTP ${resp.code} for $url")
                    return null
                }
                val body = resp.body ?: return null
                val contentType = body.contentType()?.toString().orEmpty()
                val mime = parseMime(contentType)
                val ext = extFromMime(mime)

                val declared = body.contentLength()
                if (declared > MAX_IMAGE_BYTES) {
                    Log.w(TAG, "downloadOne: $url declared $declared > 20MB, skip")
                    return null
                }

                val attachmentId = UUID.randomUUID().toString()
                val file = try {
                    attachmentStore.save(body.byteStream(), noteId, attachmentId, ext)
                } catch (e: Exception) {
                    Log.w(TAG, "downloadOne: save failed for $url", e)
                    return null
                }
                val actualBytes = file.length()
                if (actualBytes > MAX_IMAGE_BYTES) {
                    file.delete()
                    Log.w(TAG, "downloadOne: $url actual $actualBytes > 20MB, removed")
                    return null
                }
                Downloaded(
                    attachmentId = attachmentId,
                    localPath = file.absolutePath,
                    mimeType = mime,
                    bytes = actualBytes
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "downloadOne: IO error for $url", e)
            null
        } catch (e: Throwable) {
            Log.w(TAG, "downloadOne: unexpected error for $url", e)
            null
        }
    }

    /**
     * 替换 markdown 中的 url → 本地路径或占位符。
     */
    private fun replaceInMarkdown(markdown: String, urlToLocalPath: Map<String, String>, failed: List<String>): String {
        if (urlToLocalPath.isEmpty() && failed.isEmpty()) return markdown
        var result = markdown

        result = MD_IMG.replace(result) { match ->
            val alt = match.groupValues[1]
            val url = match.groupValues[2].trim()
            when {
                urlToLocalPath.containsKey(url) -> "![$alt](${urlToLocalPath[url]})"
                url in failed -> "![$alt]($PLACEHOLDER)"
                else -> match.value
            }
        }

        result = HTML_IMG.replace(result) { match ->
            val url = match.groupValues[1].trim()
            when {
                urlToLocalPath.containsKey(url) ->
                    match.value.replace("\"$url\"", "\"${urlToLocalPath[url]}\"")
                        .replace("'$url'", "'${urlToLocalPath[url]}'")
                url in failed -> match.value.replace(
                    "src=\"$url\"",
                    "src=\"$PLACEHOLDER\""
                ).replace("src='$url'", "src='$PLACEHOLDER'")
                else -> match.value
            }
        }

        return result
    }

    private fun parseMime(contentType: String): String {
        return contentType.substringBefore(";").trim().lowercase().ifEmpty { "image/unknown" }
    }

    private fun extFromMime(mime: String): String = when {
        mime.endsWith("/png") -> "png"
        mime.endsWith("/jpeg") || mime.endsWith("/jpg") -> "jpg"
        mime.endsWith("/webp") -> "webp"
        mime.endsWith("/gif") -> "gif"
        mime.endsWith("/heic") -> "heic"
        mime.endsWith("/bmp") -> "bmp"
        mime.endsWith("/svg+xml") -> "svg"
        else -> "img"
    }

    companion object {
        private const val TAG = "FeishuImageDL"

        // feishu-import-from-folder:单图 20MB 上限(跟 upload_all 一致)
        private const val MAX_IMAGE_BYTES: Long = 20L * 1024 * 1024

        // feishu-import-from-folder:失败占位符
        const val PLACEHOLDER = "[图片下载失败]"

        // markdown: ![alt](url "title") — url 限 1..2048 字符,防恶意超长 url 触发回溯 / 内存膨胀。
        // 飞书 CDN url 通常 < 500 字符;2048 是 RFC 8089 推荐上限。
        //
        // url 字符类 [^)\s]+ 排除 ) 和空白:飞书 CDN url 偶尔含 `)`(签名 query / 维基风格文件名),
        // 严格排除 `)` 会把 https://.../foo(bar.png 截断到 `(` 处。改用 "balanced parens" 模式:
        // 先匹配到第一个 `)` 然后向前看是否紧跟 `)` 或空白 → 若是则该 `)` 是 url 内部,继续;
        // 否则该 `)` 是 markdown 闭合。
        // 简化方案:url 字符类放宽到 `[^\s]+`(只排除空白),信任 markdown 自身的 `\)` 转义;
        // 如果 markdown 含裸 `)` 在 url 内,parser 会优先匹配最右 `)` 作为闭合,这是 markdown 标准行为。
        // review 2026-07-07 Finding #4:实操中飞书 docx 输出的 markdown 经常含 `)` url,严格排除导致
        // 大批量图片失败 → 占位符。放宽后修复。
        private val MD_IMG = Regex("""!\[([^\]]{0,500})\]\(([^\s]{1,2048}?)(?:\s+"[^"]{0,200}")?\)""")

        // html: <img ... src="url" ...> 或 src='url' — src 同样限长。
        // review 2026-07-07 Finding #4:HTML_IMG 用 `"['"]` 闭合 src 字符串,不含 `)`,不受 #4 影响。
        private val HTML_IMG =
            Regex("""<img\s[^>]*?src=["']([^"']{1,2048})["'][^>]*?/?>""", RegexOption.IGNORE_CASE)
    }
}
