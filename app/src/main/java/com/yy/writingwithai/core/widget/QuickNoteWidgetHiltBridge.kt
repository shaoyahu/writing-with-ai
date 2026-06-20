package com.yy.writingwithai.core.widget

/**
 * M4-1 · 把 Hilt 单例 [QuickNoteWidgetRepository] 桥接到 widget host process。
 *
 * Glance 1.1.x 限制:widget host process 拿不到 Hilt `EntryPointAccessors`(跨进程)。
 * `Application.onCreate`(`WritingApp`)里把 Repository 单例塞进 `repository`,
 * widget host process 渲染时直接 `bridge.repository` 读。
 *
 * M5 polish 阶段改 GlanceStateDefinition + DataStore 持久化,本字段仅做 mvp。
 */
object QuickNoteWidgetHiltBridge {
    @Volatile
    var repository: QuickNoteWidgetRepository? = null
}
