package com.yy.writingwithai.core.data.model

/**
 * 笔记 + 它挂的 tag 列表 + 首张图片路径(可选)。
 *
 * 列表屏一次拿到完整渲染需要的字段,不需要在 UI 层再次拼装。
 *
 * note-list-thumbnail · `firstImagePath` 走 `note_attachments` 表批量 join 拿首张
 * 图片的 localPath,`null` 表示该笔记无图片附件。列表卡片右侧根据此字段
 * 决定是否渲染 72dp × 72dp 缩略图。
 */
data class NoteWithTags(
    val note: Note,
    val tags: List<String>,
    val firstImagePath: String? = null
)
