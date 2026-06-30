package com.yy.writingwithai.core.note.impl

import androidx.room.withTransaction
import com.yy.writingwithai.core.data.db.AppDatabase
import com.yy.writingwithai.core.data.db.NoteDao
import com.yy.writingwithai.core.data.db.dao.NoteLinkDao
import com.yy.writingwithai.core.data.db.dao.RelatedRow
import com.yy.writingwithai.core.data.db.entity.NoteLinkEntity
import com.yy.writingwithai.core.note.NoteLinker
import com.yy.writingwithai.core.note.RelatedNote
import com.yy.writingwithai.core.note.config.LinkWeights
import com.yy.writingwithai.core.prefs.NoteAssociationSettingsStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

@Singleton
class CompositeNoteLinker
@Inject
constructor(
    private val db: AppDatabase,
    private val noteLinkDao: NoteLinkDao,
    private val noteDao: NoteDao,
    private val localLinker: LocalNoteLinker,
    private val wikilinkIndexer: WikilinkIndexer,
    private val entityBacklinker: EntityBacklinker,
    private val semanticLinker: SemanticNoteLinker,
    private val assocSettings: NoteAssociationSettingsStore
) : NoteLinker {

    /**
     * fix-2026-06-30-full-review-r1 CRITICAL C1:delete + upsert 包 `db.withTransaction`。
     * 之前 delete 与 upsert 之间无事务边界,进程被杀(系统回收 / OOM killer / 用户强杀)
     * 落在中间窗口 → 该笔记所有出站链接永久丢失。注入 AppDatabase 走 Room 事务。
     */
    override suspend fun recomputeForNote(noteId: String) = coroutineScope {
        // 三个子计算先并发跑(纯计算 + DAO read,无写入竞争),拿到结果后再进事务写入
        val localDeferred = async { localLinker.compute(noteId) }
        val wikiDeferred = async { wikilinkIndexer.index(noteId) }
        val entityDeferred = async { entityBacklinker.compute(noteId) }

        val localCandidates = localDeferred.await()
        val wikiRows = wikiDeferred.await()
        val entityRows = entityDeferred.await()

        val now = System.currentTimeMillis()
        val localRows = localCandidates.map { c ->
            NoteLinkEntity(
                srcNoteId = noteId,
                dstNoteId = c.dstNoteId,
                linkType = c.linkType,
                weight = c.weight,
                createdAt = c.createdAt,
                updatedAt = now,
                evidence = c.evidence
            )
        }
        val allRows = (localRows + wikiRows + entityRows)
        // entity-extraction-polish §2.4:阈值由 store 提供(同步 SharedPreferences 读取,无需挂起)。
        val threshold = assocSettings.threshold().toDouble()
        val capped = NoteLinkCap.enforce(allRows, threshold = threshold)

        // C1 修:delete + upsertAll 走单事务,process kill 不留 link 丢失窗口
        db.withTransaction {
            noteLinkDao.deleteBySrc(noteId)
            if (capped.isNotEmpty()) noteLinkDao.upsertAll(capped)
        }

        // tasks §3.3:共享实体 < 1 时回退到 LLM 语义抽取
        val entityDstCount = entityRows.map { it.dstNoteId }.distinct().size
        if (assocSettings.isEnabled() && entityDstCount < 1) {
            try {
                semanticLinker.extractAndPersist(noteId)
            } catch (e: Exception) {
                // R4-2-C fix:静默吞 LLM 异常不利于调试网络 / 配额 / 反序列化问题。
                // CancellationException 必须重抛(协程取消机制),其它异常 Log.w 后吞掉(主流程已成功写入 local/wiki/entity 边)。
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.w(
                    "CompositeNoteLinker",
                    "LLM semantic fallback failed for noteId=$noteId",
                    e
                )
            }
        }
    }

    /**
     * R3 fix M8:之前是死 SPI(`return 0`)。现在真做:取全部 note id,
     * 逐条串行 `recomputeForNote`(已经有 500ms debounce + scope 收口,并行反而撞 Room)。
     * 返回成功处理的 note 数 —— 任何 per-note 失败不算数,caller 可重试。
     */
    override suspend fun recomputeAll(): Int {
        val ids = noteDao.getAllIds()
        var ok = 0
        for (id in ids) {
            try {
                recomputeForNote(id)
                ok++
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // 单条 poisoned note 不能 abort 整个 backfill —— H8 教训。
                // 跳过并 log,继续下一条。
                android.util.Log.w("CompositeNoteLinker", "recomputeAll: failed for noteId=$id", e)
            }
        }
        return ok
    }

    override suspend fun getRelated(noteId: String, limit: Int): List<RelatedNote> {
        // entity-extraction-polish §2.2:DAO threshold 形参由 store 提供。
        val rows: List<RelatedRow> = noteLinkDao.getRelated(noteId, limit, assocSettings.threshold().toDouble())
        return rows.map { it.map() }
    }

    override suspend fun getBacklinks(noteId: String, limit: Int): List<RelatedNote> {
        val rows: List<RelatedRow> = noteLinkDao.getBacklinks(noteId, limit, assocSettings.threshold().toDouble())
        return rows.map { it.map() }
    }
}

internal fun RelatedRow.map(): RelatedNote = RelatedNote(
    noteId = noteId,
    title = title,
    preview = preview,
    score = score,
    signals = LinkWeights.parseSignals(signals),
    evidence = evidence
)
