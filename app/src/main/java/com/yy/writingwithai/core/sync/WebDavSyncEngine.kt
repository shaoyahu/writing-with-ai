package com.yy.writingwithai.core.sync

import com.yy.writingwithai.core.data.model.Note
import javax.inject.Inject
import javax.inject.Singleton

/**
 * cloud-sync-webdav · WebDAV 同步引擎骨架。
 * B5b 实现: OkHttp PROPFIND/PUT/GET/DELETE + 增量同步 + 冲突解决。
 * 当前返回 NotImplemented Failure。
 */
@Singleton
class WebDavSyncEngine @Inject constructor() : SyncEngine {
    override suspend fun push(note: Note, revision: String?): SyncResult {
        return SyncResult.Failure("WebDAV sync not yet implemented")
    }

    override suspend fun pull(sinceRevision: String?): SyncResult {
        return SyncResult.Failure("WebDAV sync not yet implemented")
    }

    override suspend fun getStatus(): SyncStatus = SyncStatus.Idle
}
