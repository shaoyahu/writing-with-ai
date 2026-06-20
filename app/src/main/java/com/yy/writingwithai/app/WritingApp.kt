package com.yy.writingwithai.app

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.yy.writingwithai.BuildConfig
import com.yy.writingwithai.core.prefs.ConsentStore
import com.yy.writingwithai.core.widget.QuickNoteWidgetHiltBridge
import com.yy.writingwithai.core.widget.QuickNoteWidgetRepository
import com.yy.writingwithai.core.widget.QuickNoteWidgetWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

/**
 * writing-with-ai · Application 入口。
 *
 * 由 `@HiltAndroidApp` 触发 Hilt 组件编译期生成;
 * M2 起在 onCreate 里完成 Room / DataStore / OkHttp 初始化,M0 仅做 Hilt 接入验证。
 *
 * M4-1:onCreate 里
 * 1. 把 [QuickNoteWidgetHiltBridgeInjector.repository] 桥接到 [QuickNoteWidgetHiltBridge]
 *    (widget host process 拿不到 Hilt,这里静态单例供 widget `provideGlance` 读)
 * 2. 用 [WorkManager.enqueueUniquePeriodicWork] 注册 widget 兜底 15min 周期任务
 *
 * M4-4:r1 H3 修 — `BuildConfig.CONSENT_GATE_ENABLED=false` 时同步写默认 consent 到 DataStore
 * (一次性,卸载重装会重置,等同于"回滚到 M4-3 行为")。这样 App 冷启时 `AppNav` 看到
 * `accepted=true` 直接跳过 onboarding 屏,不会死锁。
 */
@HiltAndroidApp
class WritingApp : Application() {
    @Inject
    lateinit var widgetRepository: QuickNoteWidgetRepository

    @Inject
    lateinit var consentStore: ConsentStore

    override fun onCreate() {
        super.onCreate()
        // 1) 把 Hilt 单例桥接到 widget host process 可读的静态字段
        QuickNoteWidgetHiltBridge.repository = widgetRepository

        // r1 H3 修:CONSENT_GATE_ENABLED=false 回滚逃生口 — 同步写默认 consent
        // (卸载重装即重置,避免永久卡 onboarding 屏)
        if (!BuildConfig.CONSENT_GATE_ENABLED) {
            runBlocking {
                consentStore.setAccepted(
                    version = BuildConfig.CONSENT_VERSION,
                    at = System.currentTimeMillis()
                )
            }
        }

        // 2) 注册 widget 兜底周期任务(15 min,KEEP 保证 App 重启不重置下次执行时间)
        val periodic = PeriodicWorkRequestBuilder<QuickNoteWidgetWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "quicknote-widget-refresh",
            ExistingPeriodicWorkPolicy.KEEP,
            periodic
        )
    }
}
