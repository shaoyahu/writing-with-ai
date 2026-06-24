package com.yy.writingwithai.core.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.yy.writingwithai.core.security.PathSafety
import dagger.hilt.android.AndroidEntryPoint
import java.io.InputStream
import java.security.MessageDigest
import javax.inject.Inject

/**
 * app-self-hosted-update · 下载完成广播接收。
 *
 * 流程:
 * 1. DownloadManager 查 downloadId 对应 local URI(API 29+ 通常是 content://,旧 API 是 file://)
 * 2. ContentResolver.openInputStream(URI) 拿字节流算 SHA-256 → 与 manifest.apkSha256 比对
 * 3. 一致:FileProvider.getUriForFile 拿 content URI + 系统安装 intent
 * 4. 不一致:删文件 + Toast 报错
 *
 * review r1 修:
 * - 用 ContentResolver 而不是 `File(localUri)` 解析(API 29+ COLUMN_LOCAL_URI 返回 content://)
 * - install 用 FileProvider 而不是 Uri.fromFile(API 24+ 会抛 FileUriExposedException)
 *
 * fix-2026-06-24-review-r1-critical 修:
 * - install Intent filename 从 `manifest.apkName` 派生(走 `PathSafety.safeName`),不再从服务器 URL `substringAfterLast('/')`
 * - `cursor.getColumnIndex` 返回 -1 时显式 null 检查(防 lint Range error)
 */
@AndroidEntryPoint
class UpdateDownloadReceiver : BroadcastReceiver() {

    @Inject lateinit var manifestStore: UpdateManifestStore

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId == -1L) return

        val manifest = manifestStore.consume(downloadId) ?: run {
            Log.w(TAG, "no manifest for downloadId=$downloadId")
            return
        }

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        dm.query(query).use { cursor ->
            if (cursor == null || !cursor.moveToFirst()) {
                Log.w(TAG, "download cursor empty for id=$downloadId")
                return
            }
            val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            if (statusIdx < 0) {
                Log.w(TAG, "COLUMN_STATUS missing for id=$downloadId")
                return
            }
            if (cursor.getInt(statusIdx) != DownloadManager.STATUS_SUCCESSFUL) {
                Log.w(TAG, "download not successful for id=$downloadId status=${cursor.getInt(statusIdx)}")
                Toast.makeText(context, "下载未成功,请重试", Toast.LENGTH_LONG).show()
                return
            }

            // fix r1:防 `getColumnIndex` 返回 -1 时直接 `getString(-1)` 抛 ArrayIndexOutOfBoundsException
            val uriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_URI)
            val localIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
            val uriStr: String? = when {
                uriIdx >= 0 -> cursor.getString(uriIdx)
                localIdx >= 0 -> cursor.getString(localIdx)
                else -> {
                    Log.w(TAG, "no COLUMN_URI / COLUMN_LOCAL_URI for id=$downloadId")
                    return
                }
            }
            if (uriStr.isNullOrBlank()) {
                Log.w(TAG, "empty local uri for id=$downloadId")
                return
            }
            val uri = Uri.parse(uriStr)

            val actualSha = sha256(context, uri)
            if (actualSha == null) {
                Toast.makeText(context, "下载文件损坏:无法读取", Toast.LENGTH_LONG).show()
                return
            }
            if (!actualSha.equals(manifest.apkSha256, ignoreCase = true)) {
                Log.w(TAG, "sha mismatch: expected=${manifest.apkSha256} actual=$actualSha")
                runCatching { context.contentResolver.delete(uri, null, null) }
                Toast.makeText(context, "下载文件损坏,请重试", Toast.LENGTH_LONG).show()
                return
            }

            // fix r1:filename 来自 manifest.apkName 走 PathSafety 校验,不再从 URI 派生
            val safeName = PathSafety.safeName(manifest.apkName, fallback = DEFAULT_APK_NAME)
            if (safeName != manifest.apkName) {
                Log.w(TAG, "manifest.apkName='${manifest.apkName}' unsafe, fallback to $DEFAULT_APK_NAME")
            }
            installIntent(context, uri, safeName)
        }
    }

    private fun installIntent(context: Context, uri: Uri, filename: String) {
        // FileProvider 拿 content URI;authority = applicationId + ".fileprovider"
        val authority = "${context.packageName}.fileprovider"
        val apkFile = java.io.File(
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS),
            "app-update/$filename"
        )
        val contentUri = if (apkFile.exists()) {
            FileProvider.getUriForFile(context, authority, apkFile)
        } else {
            // 兜底:用 DownloadManager 拿到的原始 URI(可能已经是 content://)
            uri
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Throwable) {
            Log.w(TAG, "install intent failed: ${e.javaClass.simpleName}")
            Toast.makeText(context, "无法启动安装器,请手动到 /app/download/ 下载", Toast.LENGTH_LONG).show()
        }
    }

    private fun sha256(context: Context, uri: Uri): String? {
        val md = MessageDigest.getInstance("SHA-256")
        return try {
            context.contentResolver.openInputStream(uri)?.use { input: InputStream ->
                val buf = ByteArray(1 shl 20)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    md.update(buf, 0, n)
                }
            } ?: return null
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Throwable) {
            Log.w(TAG, "sha256 read failed: ${e.javaClass.simpleName}")
            null
        }
    }

    companion object {
        private const val TAG = "UpdateDownloadReceiver"
        private const val DEFAULT_APK_NAME = "update.apk"
    }
}
