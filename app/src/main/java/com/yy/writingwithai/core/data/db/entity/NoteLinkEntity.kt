package com.yy.writingwithai.core.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * 笔记间链接边表。
 *
 * - 复合主键 `(srcNoteId, dstNoteId, linkType)`:同一种信号在两个 note 之间只允许一行
 * - 每种 `LinkType` 独立成行,读路径用 SQL 聚合(见 [com.yy.writingwithai.core.data.db.NoteLinkDao.getRelated])
 * - `weight` ∈ [0,1],各 LinkType 语义不同:
 *   - [LinkType.WIKILINK]:固定 1.0(显式最强)
 *   - [LinkType.TAG_OVERLAP]:jaccard(tags(src), tags(dst))
 *   - [LinkType.CONTENT_SIM]:1 - normalize(bm25(src.content, dst.content))
 *   - [LinkType.LLM_EXTRACT]:LLM 输出的 confidence
 * - `evidence` 可选 JSON 字符串,存 sharedTags / reason 等可观测信息
 * - CASCADE:删源或删目标 note 时,关联边自动清
 *
 * 见 [openspec.changes.note-association.specs.note-association.spec] §"Note links storage schema"。
 */
@Entity(
    tableName = "note_links",
    primaryKeys = ["srcNoteId", "dstNoteId", "linkType"],
    indices = [
        Index("srcNoteId"),
        Index("dstNoteId"),
        Index("linkType")
    ],
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["srcNoteId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["dstNoteId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class NoteLinkEntity(
    val srcNoteId: String,
    val dstNoteId: String,
    val linkType: LinkType,
    val weight: Float,
    val createdAt: Long,
    val updatedAt: Long,
    val evidence: String? = null
)

/**
 * 链接信号类型。
 *
 * 每种类型独立写一行,读路径 SQL 聚合得到最终 score。
 */
enum class LinkType {
    WIKILINK,
    TAG_OVERLAP,
    CONTENT_SIM,
    LLM_EXTRACT,
    ENTITY_HIT
}
