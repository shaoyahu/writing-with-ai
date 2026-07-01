package com.yy.writingwithai.core.data.model

/**
 * 标签 UI 领域模型。
 *
 * M1 阶段标签只是字符串，后续若加颜色 / emoji / 描述字段，在 data class 上追加即可。
 *
 * 见 [openspec.changes.quick-note-feature.specs.quick-note.spec] §"Tag many-to-many"。
 */
data class Tag(
    val name: String
)
