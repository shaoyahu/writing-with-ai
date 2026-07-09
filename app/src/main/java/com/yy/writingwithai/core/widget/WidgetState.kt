package com.yy.writingwithai.core.widget

import kotlinx.serialization.Serializable

/**
 * widget-rome-compat · widget 进程状态快照(DataStore 持久化)。
 *
 * widget host process 被国产 ROM(MIUI 等)杀 → 30s 后系统拉起 → 通过
 * [WidgetStateDefinition] 读回 stale 状态作为兜底显示。
 *
 * fix-full-review M55:过去有 4 个字段,实际只有 [currentNoteIndex] 被 [QuickNoteWidget]
 * + [WidgetStateStore] 读写;`cachedNoteIds` / `lastRefreshAt` / `romVendor` 都是
 * 设计期预想的 ROM 兜底 / 缓存降级方案,但 `RomDetector` 没真正接入,字段只写不读。
 * 删掉避免误导后续维护者,需要时再加回(走 [WidgetStateDefinition] 兼容升级)。
 *
 * @property currentNoteIndex 轮播 widget 当前显示第几条笔记。
 */
@Serializable
data class WidgetState(
    val currentNoteIndex: Int = 0
)
