package com.yy.writingwithai.core.security

import java.io.File

/**
 * fix-2026-06-24-review-r1-critical · 共享路径安全工具。
 *
 * 集中 allow-list 正则 + canonical-path containment check,被 app 自更新
 * (UpdateDownloadReceiver / ApkDownloader)、zip 导入 (ZipHelper)、附件存储
 * (AttachmentStore) 复用,避免正则漂移。
 */
object PathSafety {

    // fix-2026-06-25-review-r1 M7:加 negative lookahead 拒 leading `.` / 子串 `..` /
    // `.` 字符(防止 `.bashrc` / `..` 目录逃逸、`a.b` 这种把 `.` 当扩展名绕过)。
    // 例:`writing-with-ai-1.2.3.apk` 不再通过,需先 strip 扩展名后再校验 / 或 fallback 到 `default`。
    val SAFE_NAME: Regex = Regex("^(?!\\.)(?!.*\\.\\.)[A-Za-z0-9_-]{1,128}$")

    /** id 安全字符(noteId / attachmentId / docId)。无 `.` 无 `/` 无 `\`,只允许字母数字下划线连字符。 */
    val SAFE_ID: Regex = Regex("^[A-Za-z0-9_-]{1,64}$")

    /** 扩展名白名单(只允许短字母)。 */
    val SAFE_EXT: Regex = Regex("^[A-Za-z0-9]{1,8}$")

    /**
     * 安全文件名 fallback。`s` 通过 [SAFE_NAME] 校验 → 返回 `s`,否则返回 `fallback`。
     * 默认 fallback `"default"` 足以应对恶意 manifest。
     */
    fun safeName(s: String?, fallback: String = "default"): String =
        if (s != null && SAFE_NAME.matches(s)) s else fallback

    /** 安全 id,失败抛 [IllegalArgumentException](由 I/O 边界 catch 转 Result.failure)。 */
    fun requireSafeId(value: String, field: String) {
        require(SAFE_ID.matches(value)) { "$field must match $SAFE_ID (got: $value)" }
    }

    /** 安全扩展名,失败抛 [IllegalArgumentException]。 */
    fun requireSafeExt(value: String) {
        require(SAFE_EXT.matches(value)) { "extension must match $SAFE_EXT (got: $value)" }
    }

    /**
     * Canonical-path containment:断言 [child] 在 [root] 之下(或等于)。
     * 失败抛 [IllegalArgumentException]。
     */
    fun assertContainedUnder(child: File, root: File) {
        val c = child.canonicalPath
        val r = root.canonicalPath
        require(c == r || c.startsWith("$r/")) {
            "Path escapes root: $c not under $r"
        }
    }
}
