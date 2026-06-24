package com.yy.writingwithai.core.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
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
            if (statusIdx >= 0 && cursor.getInt(statusIdx) != DownloadManager.STATUS_SUCCESSFUL) {
                Log.w(TAG, "download not successful for id=$downloadId status=${cursor.getInt(statusIdx)}")
                Toast.makeText(context, "下载未成功,请重试", Toast.LENGTH_LONG).show()
                return
            }

            // 优先 COLUMN_URI(API 24+ 官方入口,返回 content://),fallback COLUMN_LOCAL_URI
            val uriStr = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_URI))
                ?: cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))
                ?: run {
                    Log.w(TAG, "no local uri for id=$downloadId")
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

            installIntent(context, uri, uriStr.substringAfterLast('/'))
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
    }
}
