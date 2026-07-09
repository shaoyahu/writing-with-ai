package com.yy.writingwithai.core.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * app-self-hosted-update · 服务端 manifest data class。
 *
 * 对应 `https://xiaozha.nananxue.cn/app/{channel}/version.json` 响应。
 * 由 `scripts/release-server/build-version-json-local.py` 在发布时本地生成，
 * scp 到服务器。apkUrl 指向 GitHub Releases CDN。
 */
@Serializable
data class AppUpdateManifest(
    @SerialName("versionCode") val versionCode: Int,
    @SerialName("versionName") val versionName: String,
    @SerialName("apkUrl") val apkUrl: String,
    @SerialName("apkSha256") val apkSha256: String,
    @SerialName("apkName") val apkName: String? = null,
    // fix M59 (full-review):以下 4 字段保留 JSON 反序列化兼容(服务端可能返回),
    // 但当前客户端未读取。标记 @Deprecated 防止新代码误用,需要时移除注解即可。
    @Deprecated("Unused by client, kept for JSON compat") @SerialName("apkSize") val apkSize: Long = 0L,
    @SerialName("releaseNotes") val releaseNotes: String = "",
    @Deprecated("Unused by client, kept for JSON compat") @SerialName("releasedAt") val releasedAt: String = "",
    @Deprecated("Unused by client, kept for JSON compat")
    @SerialName("minSupportedVersionCode")
    val minSupportedVersionCode: Int = 1,
    @Deprecated("Unused by client, kept for JSON compat") @SerialName("mandatory") val mandatory: Boolean = false
) {
    init {
        require(versionCode > 0) { "versionCode must be positive" }
        require(versionName.isNotBlank()) { "versionName must not be blank" }
        require(apkUrl.startsWith("https://")) { "apkUrl must use HTTPS" }
        require(SHA256_REGEX.matches(apkSha256)) { "apkSha256 must be 64 hex chars" }
    }

    companion object {
        private val SHA256_REGEX = Regex("^[0-9a-fA-F]{64}$")
    }
}
