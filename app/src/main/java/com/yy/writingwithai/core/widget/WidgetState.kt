package com.yy.writingwithai.core.widget

import kotlinx.serialization.Serializable

/**
 * widget-rome-compat · widget 进程状态快照(DataStore 持久化)。
 *
 * widget host process 被国产 ROM(MIUI 等)杀 → 30s 后系统拉起 → 通过
 * [WidgetStateDefinition] 读回 stale 状态作为兜底显示。
 *
 * @property cachedNoteIds 最近 N 条笔记 id 缓存;`provideGlance` 拿 Room 真实最新失败时兜底
 * @property lastRefreshAt 上次 refresh epoch millis;`0L` = 未 refresh 过
 * @property romVendor 启动期 ROM 命中，首次写入时由 [RomDetector.current] 填
 */
@Serializable
data class WidgetState(
    val cachedNoteIds: List<String> = emptyList(),
    val lastRefreshAt: Long = 0L,
    val romVendor: RomVendor = RomVendor.AOSP,
    val currentNoteIndex: Int = 0
)
