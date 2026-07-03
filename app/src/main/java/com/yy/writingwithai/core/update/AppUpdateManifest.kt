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
    @SerialName("apkSize") val apkSize: Long = 0L,
    @SerialName("releaseNotes") val releaseNotes: String = "",
    @SerialName("releasedAt") val releasedAt: String = "",
    @SerialName("minSupportedVersionCode") val minSupportedVersionCode: Int = 1,
    @SerialName("mandatory") val mandatory: Boolean = false
)
