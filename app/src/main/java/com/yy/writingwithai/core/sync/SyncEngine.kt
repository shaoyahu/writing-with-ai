package com.yy.writingwithai.core.sync

import com.yy.writingwithai.core.data.model.Note

/**
 * cloud-sync-foundation · 同步引擎接口。
 * 后续 B5b 对接实际后端(WebDAV/Firebase等)，B5a 只定义接口 + Fake 实现。
 */
interface SyncEngine {
    /** 推送本地变更到远端，返回新的 syncRevision。 */
    suspend fun push(note: Note, revision: String?): SyncResult

    /** 拉取远端变更，返回有更新的笔记列表。 */
    suspend fun pull(sinceRevision: String?): SyncResult

    /** 获取远端同步状态。 */
    suspend fun getStatus(): SyncStatus
}
