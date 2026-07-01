package com.yy.writingwithai.core.widget

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * hardening-sse-and-widget-init H-3:Hilt EntryPoint 让 widget host process / Worker
 * 进程可以在不依赖 [QuickNoteWidgetHiltBridge] 全局 mutable 字段的前提下解析
 * [QuickNoteWidgetRepository] 单例。
 *
 * Glance 进程触发 render 时，`Application.onCreate` 可能尚未完成;`EntryPointAccessors`
 * 在 `fromApplication(context, EntryPoint::class.java)` 时由 Hilt 提供的
 * `SingletonComponent` 兜底，失败抛 `EntryPointNotFoundException` —— 我们 catch 后
 * 返回 null,widget 走默认空态而非静默 fallback。
 *
 * 不引入 `androidx.hilt:hilt-work`(项目无此 dep)，保留 Hilt 自身的 `EntryPointAccessors`
 * 模式 + CoroutineWorker 即可。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun repository(): QuickNoteWidgetRepository
}
