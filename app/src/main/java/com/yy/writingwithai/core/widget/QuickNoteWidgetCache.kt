package com.yy.writingwithai.core.widget

import com.yy.writingwithai.core.data.model.Note
import java.util.concurrent.atomic.AtomicReference

/**
 * fix-2026-06-26-review-r3 H25:widget 渲染层 TTL 缓存。
 *
 * Glance widget 每次 `provideGlance`(系统周期 / 主动 updateAll)都触发
 * `NoteRepository.observeRecent(limit).first()` 走一次 Room 读;在用户无操作时
 * 这些查询完全是冗余的。加 30s TTL:同一 limit 在 TTL 内复用上次结果,降低 Room 负载。
 *
 * 主路径写(save/delete)仍走 `QuickNoteWidgetUpdater.updateAll(context)` 强制刷新,
 * 缓存本身在下次 expire 时也会自然过期,所以**不会**让用户看到陈旧笔记超过 30s。
 *
 * AtomicReference 持 [Pair],read 一致 + write atomic,无锁、无 volatile 边界坑。
 */
internal object QuickNoteWidgetCache {
    private const val TTL_MS = 30_000L

    internal data class Entry(val limit: Int, val loadedAt: Long, val notes: List<Note>)

    private val cached = AtomicReference<Entry?>(null)

    /**
     * 取缓存的笔记列表;过期或 limit 变化则调 [loader] 重读。
     * [nowEpochMs] 由 caller 传入便于测试确定性。
     */
    suspend fun getOrLoad(limit: Int, nowEpochMs: Long, loader: suspend () -> List<Note>): List<Note> {
        val snapshot = cached.get()
        if (snapshot != null && snapshot.limit == limit && nowEpochMs - snapshot.loadedAt < TTL_MS) {
            return snapshot.notes
        }
        val fresh = loader()
        cached.set(Entry(limit = limit, loadedAt = nowEpochMs, notes = fresh))
        return fresh
    }

    /** 测试 / 主动 flush:下次 getOrLoad 强制重读。 */
    fun invalidate() {
        cached.set(null)
    }
}
