package com.yy.writingwithai.core.feishu.sync

/**
 * feishu-import-from-folder · 用户输入解析(纯函数)。
 *
 * 把用户粘贴的链接 / token 字符串统一解析为 [ParsedToken],业务层据此走不同路径。
 *
 * 规则:
 * - `https://my.feishu.cn/drive/folder/{token}` → [ParsedToken.Folder]
 * - `https://my.feishu.cn/wiki/{token}` → [ParsedToken.Folder](需 resolveFolderToken 解析)
 * - `https://my.feishu.cn/docx/{token}` → [ParsedToken.Doc]
 * - 直接 token(`fldcn*` / `wikcn*` / `docx*` / `doccn*`)→ [ParsedToken.RawToken]
 * - `https://larksuite.com/...` 或其他非 feishu.cn host → [ParsedToken.UnsupportedHost]
 * - 空 / 全空白 → [ParsedToken.Malformed]
 */
object FeishuInputParser {

    sealed class ParsedToken {
        data class RawToken(val token: String) : ParsedToken()
        data class Folder(val token: String) : ParsedToken()
        data class Doc(val token: String) : ParsedToken()
        data class UnsupportedHost(val host: String) : ParsedToken()
        data class Malformed(val reason: String) : ParsedToken()
    }

    private val KNOWN_TOKEN_PREFIXES = listOf("fldcn", "wikcn", "docx", "doccn")

    fun parse(input: String): ParsedToken {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ParsedToken.Malformed("empty")

        // 1. URL 形式(必须含 feishu.cn)
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return parseUrl(trimmed)
        }

        // 2. 裸 token — 必须以已知前缀开头(防恶意字符串进网络请求)。
        // 飞书真实 token 形如 `fldcnXXXXXXXX` / `wikcnXXXXXXX` / `docxXXXXX` / `doccnXXXXX`。
        // 不允许任意 8+ 字母数字组合(否则攻击者可构造 `'; DROP TABLE users;--` 等
        // 通过校验,虽无 SQL 注入风险但会触发不必要的网络请求 / SSRF 探测)。
        return if (
            KNOWN_TOKEN_PREFIXES.any { trimmed.startsWith(it) } &&
            trimmed.length in 6..64 &&
            trimmed.all { it.isLetterOrDigit() }
        ) {
            ParsedToken.RawToken(trimmed)
        } else {
            ParsedToken.Malformed("not a recognized token or url")
        }
    }

    private fun parseUrl(url: String): ParsedToken {
        // 简易 host 提取:取 `://` 后第一个 `/` 之前的部分
        val afterScheme = url.substringAfter("://", missingDelimiterValue = "")
        val host = afterScheme.substringBefore("/").substringBefore("?").substringBefore("#").lowercase()
        if (host != "my.feishu.cn" && host != "feishu.cn") {
            return ParsedToken.UnsupportedHost(host)
        }

        // path 取 scheme 后到 query/fragment 前
        val path = afterScheme.substringAfter("/").substringBefore("?").substringBefore("#")
        val lastSegment = path.trim('/').substringAfterLast('/')
        if (lastSegment.isEmpty()) {
            return ParsedToken.Malformed("url missing token segment")
        }

        return when {
            path.contains("/drive/folder/", ignoreCase = true) -> ParsedToken.Folder(lastSegment)
            path.contains("/wiki/", ignoreCase = true) -> ParsedToken.Folder(lastSegment)
            path.contains("/docx/", ignoreCase = true) -> ParsedToken.Doc(lastSegment)
            path.contains("/docs/", ignoreCase = true) -> ParsedToken.Doc(lastSegment)
            else -> ParsedToken.Malformed("unrecognized feishu url path: /$path")
        }
    }
}
