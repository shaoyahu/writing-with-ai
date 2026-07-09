package com.yy.writingwithai.core.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.yy.writingwithai.R
import com.yy.writingwithai.core.security.PathSafety
import dagger.hilt.android.AndroidEntryPoint
import java.io.InputStream
import java.security.MessageDigest
import javax.inject.Inject

/**
 * app-self-hosted-update · 下载完成广播接收。
 *
 * 流程:
 * 1. DownloadManager 查 downloadId 对应 local URI(API 29+ 通常是 content://，旧 API 是 file://)
 * 2. ContentResolver.openInputStream(URI) 拿字节流算 SHA-256 → 与 manifest.apkSha256 比对
 * 3. 一致:FileProvider.getUriForFile 拿 content URI + 系统安装 intent
 * 4. 不一致:删文件 + Toast 报错
 *
 * review r1 修:
 * - 用 ContentResolver 而不是 `File(localUri)` 解析(API 29+ COLUMN_LOCAL_URI 返回 content://)
 * - install 用 FileProvider 而不是 Uri.fromFile(API 24+ 会抛 FileUriExposedException)
 *
 * fix-2026-06-24-review-r1-critical 修:
 * - install Intent filename 从 `manifest.apkName` 派生(走 `PathSafety.safeName`)，不再从服务器 URL `substringAfterLast('/')`
 * - `cursor.getColumnIndex` 返回 -1 时显式 null 检查(防 lint Range error)
 */
@AndroidEntryPoint
class UpdateDownloadReceiver : BroadcastReceiver() {

    @Inject lateinit var manifestStore: UpdateManifestStore

    override fun onReceive(context: Context, intent: Intent) {
        // fix H20:goAsync() 防止 onReceive 内重 IO(DB 查询 + SHA-256)导致 ANR。
        val pendingResult = goAsync()
        try {
            doReceive(context, intent)
        } finally {
            pendingResult.finish()
        }
    }

    private fun doReceive(context: Context, intent: Intent) {
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
                Toast.makeText(context, R.string.update_toast_failed, Toast.LENGTH_LONG).show()
                return
            }

            // fix r1:防 `getColumnIndex` 返回 -1 时直接 `getString(-1)` 抛 ArrayIndexOutOfBoundsException
            // fix H21:优先使用 COLUMN_LOCAL_URI(本地文件路径)做 SHA-256 校验，
            // COLUMN_URI 是远程 URL，无法用于本地文件校验。
            val uriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_URI)
            val localIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
            val uriStr: String? = when {
                localIdx >= 0 -> cursor.getString(localIdx)
                uriIdx >= 0 -> cursor.getString(uriIdx)
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
                Toast.makeText(context, R.string.update_toast_read_failed, Toast.LENGTH_LONG).show()
                return
            }
            if (!actualSha.equals(manifest.apkSha256, ignoreCase = true)) {
                // fix-2026-06-30-full-review-r1 MEDIUM M8:不 log actual hash(下载文件哈希
                // 可能协助攻击者理解服务内容)，只记 downloadId 便于排查。
                Log.w(TAG, "sha mismatch for downloadId=$downloadId")
                runCatching { context.contentResolver.delete(uri, null, null) }
                Toast.makeText(context, R.string.update_toast_sha_mismatch, Toast.LENGTH_LONG).show()
                return
            }

            // fix r1:filename 来自 manifest.apkName 走 PathSafety 校验，不再从 URI 派生
            val safeName = PathSafety.safeName(manifest.apkName, fallback = DEFAULT_APK_NAME)
            if (safeName != manifest.apkName) {
                // fix H15:不输出原始 apkName 值，避免 attacker-controlled 数据泄露到 logcat。
                Log.w(TAG, "manifest.apkName unsafe, fallback to $DEFAULT_APK_NAME")
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
        // review r1 M8:MIUI/EMUI 等国产 ROM 的包安装器需要显式 grantUriPermission,
        // 否则 FLAG_GRANT_READ_URI_PERMISSION 不一定透传到目标安装器。
        // fix H17:覆盖多个已知 installer 包名(Samsung/Xiaomi/Google 等)。
        val installers = listOf(
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.samsung.android.packageinstaller",
            "com.miui.packageinstaller",
            "com.xiaomi.packageinstaller"
        )
        for (pkg in installers) {
            runCatching {
                context.grantUriPermission(pkg, contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
            // fix M6 (full-review):grantUriPermission 后 15s 撤销，避免 installer 退出后
            // URI 权限残留(同一 ROM 进程后续可读 APK 文件)。15s 窗口覆盖 installer
            // 启动 + APK 读取完整生命周期,失败路径不撤销(下次广播会重试)。
            Handler(Looper.getMainLooper()).postDelayed({
                for (pkg in installers) {
                    runCatching {
                        context.revokeUriPermission(
                            pkg,
                            contentUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                }
            }, GRANT_REVOKE_DELAY_MS)
        } catch (e: Throwable) {
            Log.w(TAG, "install intent failed: ${e.javaClass.simpleName}")
            Toast.makeText(context, R.string.update_toast_no_installer, Toast.LENGTH_LONG).show()
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

        // fix M56 (full-review):DEFAULT_APK_NAME 与 ApkDownloader 默认值重复,
        // 改为引用 ApkDownloader.DEFAULT_APK_NAME 避免漂移。
        private const val DEFAULT_APK_NAME = ApkDownloader.DEFAULT_APK_NAME

        // fix M6 (full-review):grant → 撤销延迟。15s 覆盖大多数 installer 启动 +
        // APK 读取窗口,超时后撤销,ROM 进程残留读权限被回收。
        private const val GRANT_REVOKE_DELAY_MS: Long = 15_000L
    }
}
