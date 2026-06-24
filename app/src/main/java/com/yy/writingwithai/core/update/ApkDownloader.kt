package com.yy.writingwithai.core.update

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * app-self-hosted-update · 用系统 DownloadManager 入队下载。
 *
 * 返回 downloadId 给 ViewModel 持有,完成时通过 [UpdateDownloadReceiver] 回调处理
 * SHA-256 校验 + 系统安装 intent。
 *
 * 不需要 WRITE_EXTERNAL_STORAGE 权限(DownloadManager 自有下载目录)。
 */
@Singleton
class ApkDownloader @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun start(manifest: AppUpdateManifest): Long {
        val request = DownloadManager.Request(Uri.parse(manifest.apkUrl))
            .setTitle("写作助手 v${manifest.versionName}")
            .setDescription("下载中,完成后自动校验...")
            .setMimeType(MIME_APK)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                DOWNLOAD_SUBDIR + manifest.apkUrl.substringAfterLast('/')
            )
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return dm.enqueue(request)
    }

    fun cancel(downloadId: Long) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.remove(downloadId)
    }

    companion object {
        private const val MIME_APK = "application/vnd.android.package-archive"
        private const val DOWNLOAD_SUBDIR = "app-update/"
    }
}
