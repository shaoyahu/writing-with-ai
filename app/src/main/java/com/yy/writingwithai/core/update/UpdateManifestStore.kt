package com.yy.writingwithai.core.update

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * app-self-hosted-update · 下载 ID ↔ manifest 关联 + TTL 清理。
 *
 * ApkDownloader.start() put;UpdateDownloadReceiver 完成时 consume。
 * TTL 1h:防止用户取消 / 进程重启后 stale entry 永远残留。
 */
@Singleton
class UpdateManifestStore @Inject constructor() {

    private data class Entry(val manifest: AppUpdateManifest, val timestamp: Long = System.currentTimeMillis())

    private val map = ConcurrentHashMap<Long, Entry>()

    fun put(downloadId: Long, manifest: AppUpdateManifest) {
        map[downloadId] = Entry(manifest)
    }

    /** 取走(避免后续同 ID 再次命中),并清理超期条目。 */
    fun consume(downloadId: Long): AppUpdateManifest? {
        cleanup()
        return map.remove(downloadId)?.manifest
    }

    private fun cleanup() {
        val now = System.currentTimeMillis()
        map.entries.removeIf { now - it.value.timestamp > TTL_MS }
    }

    companion object {
        private const val TTL_MS = 60L * 60 * 1000L // 1h
    }
}
