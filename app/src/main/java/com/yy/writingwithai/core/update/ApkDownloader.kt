package com.yy.writingwithai.core.update

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.yy.writingwithai.core.security.PathSafety
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * app-self-hosted-update · 用系统 DownloadManager 入队下载。
 *
 * 返回 downloadId 给 ViewModel 持有，完成时通过 [UpdateDownloadReceiver] 回调处理
 * SHA-256 校验 + 系统安装 intent。
 *
 * 不需要 WRITE_EXTERNAL_STORAGE 权限(DownloadManager 自有下载目录)。
 *
 * fix-2026-06-24-review-r1-critical:download destination filename 从 `manifest.apkUrl.substringAfterLast('/')`
 * (服务器 URL 路径段，attacker-controlled) 改为 `manifest.apkName` + `PathSafety.safeName`,
 * fallback `"update.apk"`。
 */
@Singleton
class ApkDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val manifestStore: UpdateManifestStore
) {

    fun start(manifest: AppUpdateManifest): Long {
        // fix-2026-06-30-full-review-r1 CRITICAL C3:强制 apkUrl HTTPS，防 manifest
        // 被劫持(明文 / file:// / ftp://)时通过 DownloadManager 投毒。SHA-256
        // 校验仅防 APK 内容篡改，URL scheme 校验防网络层拦截下载源。
        // fix H16:require 消息不泄露完整 apkUrl，避免异常被 log 时泄露 URL。
        require(manifest.apkUrl.startsWith(HTTPS_PREFIX)) {
            "apkUrl must use HTTPS"
        }
        val safeFileName = PathSafety.safeName(manifest.apkName, fallback = DEFAULT_APK_NAME)
        val request = DownloadManager.Request(Uri.parse(manifest.apkUrl))
            .setTitle("写作助手 v${manifest.versionName}")
            .setDescription("下载中，完成后自动校验...")
            .setMimeType(MIME_APK)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                DOWNLOAD_SUBDIR + safeFileName
            )
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = dm.enqueue(request)
        // fix H18:enqueue 后立即 put(downloadId, manifest)，否则下载完成时
        // UpdateDownloadReceiver.consume(downloadId) 返回 null，无法校验 SHA-256。
        manifestStore.put(downloadId, manifest)
        return downloadId
    }

    fun cancel(downloadId: Long) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.remove(downloadId)
    }

    companion object {
        private const val MIME_APK = "application/vnd.android.package-archive"
        private const val DOWNLOAD_SUBDIR = "app-update/"

        // fix M56 (full-review):internal 让 UpdateDownloadReceiver 复用,
        // 避免两边独立维护 DEFAULT_APK_NAME 字符串漂移。
        internal const val DEFAULT_APK_NAME = "update.apk"

        // fix-2026-06-30-full-review-r1 C3:HTTPS-only transport 强制。
        private const val HTTPS_PREFIX = "https://"
    }
}
