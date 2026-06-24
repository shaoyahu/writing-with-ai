package com.yy.writingwithai.core.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * app-self-hosted-update · 服务端 manifest data class。
 *
 * 对应 `https://xiaozha.nananxue.cn/app/version.json` 响应。
 * 由 `scripts/release-server/build-version-json.py` 扫 APK 目录派生,
 * 服务端不维护手工 manifest。
 */
@Serializable
data class AppUpdateManifest(
    @SerialName("versionCode") val versionCode: Int,
    @SerialName("versionName") val versionName: String,
    @SerialName("apkUrl") val apkUrl: String,
    @SerialName("apkSize") val apkSize: Long,
    @SerialName("apkSha256") val apkSha256: String,
    @SerialName("releaseNotes") val releaseNotes: String = "",
    @SerialName("releasedAt") val releasedAt: String = "",
    @SerialName("minSupportedVersionCode") val minSupportedVersionCode: Int = 1,
    @SerialName("mandatory") val mandatory: Boolean = false
)
