package com.yy.writingwithai.core.note

import com.yy.writingwithai.core.data.db.entity.LinkType

/**
 * 笔记链接 SPI(详见 [com.yy.writingwithai.core.note.impl.CompositeNoteLinker] 默认实现)。
 *
 * 写路径:`recomputeForNote` / `recomputeAll`(保存时 / backfill)
 * 读路径:`getRelated` / `getBacklinks`(详情页 / 编辑器 autocomplete)
 *
 * 见 [openspec.changes.note-association.specs.note-association.spec] §"Note linker SPI"。
 */
interface NoteLinker {
    /**
     * 重新计算 [noteId] 相关的所有边(写路径入口)。
     *
     * 行为:
     * 1. 删除 `note_links` 中 `srcNoteId = noteId` 的所有旧行
     * 2. 并行 fan-out:解析 wikilink / tag jaccard / FTS top-K / (opt-in) LLM extract
     * 3. 写入新行
     */
    suspend fun recomputeForNote(noteId: String)

    /**
     * 全量 backfill(首次启动 / schema 升级时跑一次)。
     * @return 处理的 note 数
     */
    suspend fun recomputeAll(): Int

    /**
     * 相关笔记(outgoing,score 降序)。
     */
    suspend fun getRelated(noteId: String, limit: Int = 10): List<RelatedNote>

    /**
     * 反向链接(incoming,score 降序)。
     */
    suspend fun getBacklinks(noteId: String, limit: Int = 10): List<RelatedNote>
}

/**
 * 详情页 / autocomplete 用的"相关笔记"结构。
 *
 * - `preview` 是 content 前 80 字(走 SQL `SUBSTR(content, 1, 80)`)
 * - `signals` 标识哪些信号贡献了这条相关(P3 wikilink UI / 空态文案可能用)
 */
data class RelatedNote(
    val noteId: String,
    val title: String,
    val preview: String,
    val score: Float,
    val signals: Set<LinkType>,
    val evidence: String? = null
)
