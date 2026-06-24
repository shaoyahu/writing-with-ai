package com.yy.writingwithai.core.sync

import com.yy.writingwithai.core.data.model.Note
import javax.inject.Inject
import javax.inject.Singleton

/**
 * cloud-sync-webdav · WebDAV 同步引擎骨架。
 * B5b 实现: OkHttp PROPFIND/PUT/GET/DELETE + 增量同步 + 冲突解决。
 *
 * fix-2026-06-24-review-r1-critical:在 stub 阶段返回 `SyncResult.Unsupported`,
 * 区分"功能未启用"vs"同步真失败(网络/凭错)"。UI 必须分支处理。
 */
@Singleton
class WebDavSyncEngine @Inject constructor() : SyncEngine {
    override suspend fun push(note: Note, revision: String?): SyncResult {
        return SyncResult.Unsupported("WebDAV sync not implemented yet (B5b)")
    }

    override suspend fun pull(sinceRevision: String?): SyncResult {
        return SyncResult.Unsupported("WebDAV sync not implemented yet (B5b)")
    }

    override suspend fun getStatus(): SyncStatus = SyncStatus.Idle
}
