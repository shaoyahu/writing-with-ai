package com.yy.writingwithai.core.widget

import android.content.Context
import android.util.Log
import dagger.hilt.android.EntryPointAccessors

/**
 * hardening-sse-and-widget-init H-3:Widget host process / Worker 进程访问 Hilt
 * 单例的入口 —— 不再用全局 mutable 字段,改为 `EntryPointAccessors` 函数式取值。
 *
 * 旧实现 (`var repository: QuickNoteWidgetRepository?`):
 * - 依赖 `Application.onCreate` 写入字段
 * - Glance 冷启动 / Worker 周期触发时若 onCreate 未完成,字段为 null,`provideGlance`
 *   静默 `?: emptyList()` 显示空 widget,30s TTL 缓存还会锁这个错误状态
 *
 * 新实现:
 * - 移除 mutable 字段,`resolveRepository(context)` 走 `EntryPointAccessors`
 * - 解析失败(catch 所有 Throwable)返回 null,`provideGlance` 据此走 `Result.failure()`
 * - 不依赖 Application.onCreate 写入时机
 */
object QuickNoteWidgetHiltBridge {
    private const val TAG = "QuickNoteWidgetHiltBridge"

    /**
     * 解析 [QuickNoteWidgetRepository]。失败时返回 null 并 log,warn。
     *
     * 可能失败原因:
     * - Application 未初始化(Glance 冷启动先于 Application.onCreate 完成)
     * - EntryPoint 接口未在 SingletonComponent 注册(编译期问题,理论上不存在)
     * - Hilt 自身状态异常
     */
    fun resolveRepository(context: Context): QuickNoteWidgetRepository? = try {
        EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            .repository()
    } catch (e: Throwable) {
        Log.w(TAG, "widget repository unavailable", e)
        null
    }
}
